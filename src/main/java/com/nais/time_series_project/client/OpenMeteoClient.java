package com.nais.time_series_project.client;

import com.nais.time_series_project.domain.City;
import com.nais.time_series_project.model.AirQualityObservation;
import com.nais.time_series_project.model.WeatherObservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenMeteoClient {

    private final WebClient webClient = WebClient.builder().build();

    private final String archiveUrl;

    private final String airQualityUrl;

    public OpenMeteoClient(@Value("${open-meteo.archive-url}") String archiveUrl,
                           @Value("${open_meteo.air-quality-url}") String airQualityUrl) {
        this.archiveUrl = archiveUrl;
        this.airQualityUrl = airQualityUrl;
    }

    public List<WeatherObservation> fetchWeather(City city, LocalDate start, LocalDate end) {
        String url = archiveUrl
                + "?latitude=" + city.getLatitude()
                + "&longitude=" + city.getLongitude()
                + "&start_date=" + start
                + "&end_date=" + end
                + "&hourly=temperature_2m,relativehumidity_2m,pressure_msl,"
                +  "windspeed_10m,winddirection_10m,precipitation,cloudcover"
                + "&timezone=UTC";

        Map<String, Object> hourly = fetchHourly(url);
        List<String> times = (List<String>)  hourly.get("time");

        List<Double> temps     = doubles(hourly, "temperature_2m");
        List<Double> humidity  = doubles(hourly, "relativehumidity_2m");
        List<Double> pressure  = doubles(hourly, "pressure_msl");
        List<Double> windSpeed = doubles(hourly, "windspeed_10m");
        List<Double> windDir   = doubles(hourly, "winddirection_10m");
        List<Double> precip    = doubles(hourly, "precipitation");
        List<Double> cloud     = doubles(hourly, "cloudcover");

        List<WeatherObservation> out = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            out.add(WeatherObservation.builder()
                    .city(city.getDisplayName())
                    .country(city.getCountry())
                    .climateZone(city.getClimateZone())
                    .temperatureC(temps.get(i))
                    .humidityPct(humidity.get(i))
                    .pressurehPa(pressure.get(i))
                    .windSpeedMs(windSpeed.get(i))
                    .windDirectionDeg(windDir.get(i))
                    .precipitationMm(precip.get(i))
                    .cloudCoverPct(cloud.get(i))
                    .time(parseInstant(times.get(i)))
                    .build());
        }
        return out;
    }

    public List<AirQualityObservation> fetchAirQuality(City city, LocalDate start, LocalDate end) {
        String url = airQualityUrl
                + "?latitude=" + city.getLatitude()
                + "&longitude=" + city.getLongitude()
                + "&start_date=" + start
                + "&end_date=" + end
                + "&hourly=pm2_5,pm10,nitrogen_dioxide,ozone,carbon_monoxide"
                + "&timezone=UTC";

        Map<String, Object> hourly = fetchHourly(url);
        List<String> times = (List<String>) hourly.get("time");

        List<Double> pm25 = doubles(hourly, "pm2_5");
        List<Double> pm10 = doubles(hourly, "pm10");
        List<Double> no2  = doubles(hourly, "nitrogen_dioxide");
        List<Double> o3   = doubles(hourly, "ozone");
        List<Double> co   = doubles(hourly, "carbon_monoxide");

        List<AirQualityObservation> out = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            out.add(AirQualityObservation.builder()
                    .city(city.getDisplayName())
                    .country(city.getCountry())
                    .pm25(pm25.get(i))
                    .pm10(pm10.get(i))
                    .no2(no2.get(i))
                    .o3(o3.get(i))
                    .co(co.get(i))
                    .time(parseInstant(times.get(i)))
                    .build());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchHourly(String url) {
        Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return (Map<String, Object>) response.get("hourly");
    }

    @SuppressWarnings("unchecked")
    private List<Double> doubles(Map<String, Object> hourly, String key) {
        List<Number> raw = (List<Number>) hourly.get(key);
        List<Double> out = new ArrayList<>(raw.size());
        for (Number n : raw) {
            out.add(n == null ? null : n.doubleValue());
        }
        return out;
    }

    private Instant parseInstant(String iso) {
        // Open-Meteo returns format like "2025-04-01T13:00"
        return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
    }
}
