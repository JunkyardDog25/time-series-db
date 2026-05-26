# Weather Monitoring — Spring Boot + InfluxDB

Skelet projekta za KT2: vremenski nizovi sa Open-Meteo arhive, dva InfluxDB merenja (`weather_observation`, `air_quality`) i 3 složena Flux upita izložena kao REST endpoint-i.

## Stack

- Spring Boot 4.0.6 (Java 21)
- `influxdb-client-java` 7.2 (zvanični klijent za InfluxDB 2.x)
- Spring Data Redis + Spring Cache (Redis kao kešing backend + direktne komande)
- WebFlux `WebClient` za pozive ka Open-Meteo
- Lombok (kratki POJO-i)
- Docker Compose za pokretanje InfluxDB-a i Redis-a

## Pokretanje

1. **Diži InfluxDB i Redis:**
   ```bash
   docker compose up -d
   ```
   InfluxDB UI: `http://localhost:8086` (login: `admin` / `adminadmin`).
   Redis CLI: `docker exec -it weather-redis redis-cli`

2. **Build & run aplikacije:**
   ```bash
   mvn spring-boot:run
   ```

3. **Popuni bazu** (jedan poziv puni svih 10 gradova × 2 merenja):
   ```bash
   curl -X POST "http://localhost:8080/api/weather/ingest?start=2025-04-01&end=2025-05-25"
   ```
   Za 10 gradova × ~55 dana × 24 sata × 2 merenja → oko 26.000 slogova (daleko preko zahteva).

4. **Pojedinačni create (CRUD operacija za merenje):**
   ```bash
   # weather_observation
   curl -X POST http://localhost:8080/api/weather/observations \
     -H "Content-Type: application/json" \
     -d '{
       "city": "Beograd",
       "country": "RS",
       "climateZone": "continental",
       "temperatureC": 22.5,
       "humidityPct": 65.0,
       "pressureHpa": 1013.2,
       "windSpeedMs": 3.4,
       "windDirectionDeg": 180.0,
       "precipitationMm": 0.0,
       "cloudCoverPct": 40.0
     }'

   # air_quality
   curl -X POST http://localhost:8080/api/weather/air-quality \
     -H "Content-Type: application/json" \
     -d '{
       "city": "Beograd", "country": "RS",
       "pm25": 18.4, "pm10": 32.1, "no2": 25.0, "o3": 60.0, "co": 0.3
     }'
   ```
   Vremenska oznaka (`time`) je opciona — ako se izostavi, koristi se `Instant.now()`.

5. **Pojedinačni delete (CRUD operacija za merenje):**
   ```bash
   # obriši weather_observation za Beograd u datom vremenskom opsegu
   curl -X DELETE "http://localhost:8080/api/weather/observations?\
   start=2025-04-01T00:00:00Z&stop=2025-04-02T00:00:00Z&city=Beograd"

   # obriši sve air_quality slogove u opsegu (bez filtera po gradu)
   curl -X DELETE "http://localhost:8080/api/weather/air-quality?\
   start=2025-04-01T00:00:00Z&stop=2025-04-02T00:00:00Z"
   ```
   Vremenski opseg je obavezan (InfluxDB DeleteAPI ga zahteva). Predikat može filtrirati
   samo po `_measurement` i tagovima — ne po field vrednostima.

   **Brisanje svih podataka iz baze** (destruktivno — briše oba merenja):
   ```bash
   curl -X DELETE http://localhost:8080/api/weather/all
   ```
   Interno koristi opseg `1970-01-01 → 2100-01-01` jer DeleteAPI uvek zahteva vremenske granice.

6. **Pokreni upite:**
   ```bash
   curl "http://localhost:8080/api/weather/queries/avg-temperature?days=30"
   curl "http://localhost:8080/api/weather/queries/daily-amplitude?days=7"
   curl "http://localhost:8080/api/weather/queries/storm-events?days=90&thresholdHpa=5.0&minOccurrences=3"
   ```
   Prvi poziv ide na InfluxDB (videćeš `CACHE MISS` u logu), drugi i naredni pozivi sa istim parametrima vraćaju iz Redis-a — niskolatentno, bez pogađanja InfluxDB-a.

7. **Redis statistika (key-value demo):**
   ```bash
   # broj poziva po endpointu (Redis INCR po preHandle hooku)
   curl http://localhost:8080/api/weather/stats/calls

   # poslednjih 10 weather observation-a za grad (Redis list)
   curl http://localhost:8080/api/weather/stats/recent/Beograd

   # reset svih brojača
   curl -X DELETE http://localhost:8080/api/weather/stats/calls
   ```

   Provera direktno u Redis-u:
   ```bash
   docker exec -it weather-redis redis-cli
   > KEYS *
   > GET "api:calls:GET /api/weather/queries/avg-temperature"
   > LRANGE "recent:obs:Beograd" 0 -1
   ```

## Redis integracija — pregled

Dva komplementarna upotrebna obrasca koja pokrivaju zahtev za ključ-vrednost bazom:

| Sloj | Šta radi | Kako |
|---|---|---|
| **Cache sloj** | Kešira rezultate sva 3 Flux upita (TTL 5 min). Pri svakom write/delete pozivu, kešovi se automatski invalidiraju. | `@Cacheable` u `WeatherQueryService` + `@CacheEvict` u `WeatherWriteService`. Spring Boot auto-konfiguriše Redis backend preko `spring.cache.type=redis`. |
| **Direktan key-value sloj** | Brojač HTTP poziva po endpointu (INCR) + capped lista poslednjih 10 observation-a po gradu (LPUSH + LTRIM + LRANGE). | `StringRedisTemplate` u `RedisStatsService`, pozvan iz `ApiStatsInterceptor` i iz `WeatherWriteService.createWeatherObservation(...)`. |

**Kako proveriti da kešing radi** — pozovi isti query endpoint dva puta uzastopno:
- Prvi put: u logu se javi `CACHE MISS — querying InfluxDB ...`
- Drugi put: nema log poruke, odgovor se vraća iz Redis-a (drastično brže)
- Posle `curl -X POST .../observations` — sledeći query opet pravi MISS jer su kešovi evictovani

## Mapiranje na zahteve KT2

| Zahtev                                              | Pokriveno gde                                                                                                                                                 |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Baket sa logičkom jedinicom (merenjem)              | `weather_monitoring` baket, merenja `weather_observation` i `air_quality`                                                                                     |
| 1000+ slogova po merenju                            | Ingestion za par nedelja × 10 gradova prelazi 10K po merenju                                                                                                  |
| Create operacija za svako merenje                   | `WeatherWriteService.createWeatherObservation(...)` + `createAirQualityObservation(...)`                                                                      |
| Delete operacija za svako merenje                   | `WeatherWriteService.deleteWeatherObservations(...)` + `deleteAirQualityObservations(...)` preko `DeleteApi`                                                  |
| 3 složena upita (filter + group + aggregate + sort) | `WeatherQueryService` — Q1, Q2, Q3                                                                                                                            |
| **Integracija ključ-vrednost baze (Redis)**         | `RedisCacheConfig` + `@Cacheable`/`@CacheEvict` za kešing upita; `RedisStatsService` + `ApiStatsInterceptor` za direktne komande (INCR, LPUSH, LTRIM, LRANGE) |

## Struktura projekta

```
weather-monitoring/
├── pom.xml
├── docker-compose.yml
└── src/main/
    ├── resources/application.yml
    └── java/com/example/weather/
        ├── WeatherMonitoringApplication.java
        ├── config/
        │   ├── InfluxDbConfig.java
        │   ├── RedisCacheConfig.java
        │   ├── ApiStatsInterceptor.java
        │   └── WebMvcConfig.java
        ├── domain/City.java
        ├── model/
        │   ├── WeatherObservation.java
        │   └── AirQualityObservation.java
        ├── client/OpenMeteoClient.java
        ├── service/
        │   ├── WeatherIngestionService.java
        │   ├── WeatherWriteService.java
        │   ├── WeatherQueryService.java
        │   └── RedisStatsService.java
        └── controller/WeatherController.java
```
