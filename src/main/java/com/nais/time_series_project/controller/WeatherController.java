package com.nais.time_series_project.controller;

import com.nais.time_series_project.model.AirQualityObservation;
import com.nais.time_series_project.model.WeatherObservation;
import com.nais.time_series_project.service.RedisStatsService;
import com.nais.time_series_project.service.WeatherIngestionService;
import com.nais.time_series_project.service.WeatherQueryService;
import com.nais.time_series_project.service.WeatherWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherIngestionService ingestionService;

    private final WeatherQueryService queryService;

    private final WeatherWriteService writeService;

    private final RedisStatsService statsService;

    // ============ BULK INGESTION (from Open-Meteo) ============

    /** Trigger ingestion: POST /api/weather/ingest?start=2025-04-01&end=2025-05-25 */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestParam("start") String start,
                                      @RequestParam("end") String end) {
        int count = ingestionService.ingestAllCities(LocalDate.parse(start), LocalDate.parse(end));
        return Map.of("status", "ok", "records_ingested", count);
    }

    // ============ CRUD: weather_observation ============

    /** POST /api/weather/observations  — body: WeatherObservation JSON */
    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public WeatherObservation createWeatherObservation(@RequestBody WeatherObservation observation) {
        return writeService.createWeatherObservation(observation);
    }

    /**
     * DELETE /api/weather/observations?start=2025-04-01T00:00:00Z&stop=2025-04-02T00:00:00Z&city=Beograd
     * City is optional — omit it to delete all weather observations in the time range.
     */
    @DeleteMapping("/observations")
    public Map<String, String> deleteWeatherObservations(@RequestParam("start") String start,
                                                         @RequestParam("stop") String stop,
                                                         @RequestParam(value = "city", required = false) String city) {
        writeService.deleteWeatherObservations(
                OffsetDateTime.parse(start),
                OffsetDateTime.parse(stop),
                city);
        return Map.of("status", "deleted");
    }

    // ============ CRUD: air_quality ============

    /** POST /api/weather/air-quality  — body: AirQualityObservation JSON */
    @PostMapping("/air-quality")
    @ResponseStatus(HttpStatus.CREATED)
    public AirQualityObservation createAirQualityObservation(@RequestBody AirQualityObservation observation) {
        return writeService.createAirQualityObservation(observation);
    }

    /** DELETE /api/weather/air-quality?start=...&stop=...&city=... */
    @DeleteMapping("/air-quality")
    public Map<String, String> deleteAirQualityObservations(@RequestParam("start") String start,
                                                            @RequestParam("stop") String stop,
                                                            @RequestParam(value = "city", required = false) String city) {
        writeService.deleteAirQualityObservations(
                OffsetDateTime.parse(start),
                OffsetDateTime.parse(stop),
                city);
        return Map.of("status", "deleted");
    }

    @DeleteMapping("/all")
    public Map<String, String> deleteAll() {
        writeService.deleteAll();
        return Map.of("status", "all_data_deleted");
    }

    // ============ QUERIES ============

    /** Q1: GET /api/weather/queries/avg-temperature?days=800 */
    @GetMapping("/queries/avg-temperature")
    public List<Map<String, Object>> avgTemperature(@RequestParam(defaultValue = "800") int days) {
        return queryService.averageTemperatureByCity(days);
    }

    /** Q2: GET /api/weather/queries/daily-amplitude?days=800 */
    @GetMapping("/queries/daily-amplitude")
    public List<Map<String, Object>> dailyAmplitude(@RequestParam(defaultValue = "800") int days) {
        return queryService.dailyTemperatureAmplitude(days);
    }

    /** Q3: GET /api/weather/queries/storm-events?days=800&thresholdHpa=5.0&minOccurrences=3 */
    @GetMapping("/queries/storm-events")
    public List<Map<String, Object>> stormEvents(@RequestParam(defaultValue = "800") int days,
                                                 @RequestParam(defaultValue = "5.0") double thresholdHpa,
                                                 @RequestParam(defaultValue = "3") int minOccurrences) {
        return queryService.stormApproachEvents(days, thresholdHpa, minOccurrences);
    }

    // ============ REDIS STATS (key-value demo) ============

    /** GET /api/weather/stats/calls — current API call counts per endpoint (Redis INCR). */
    @GetMapping("/stats/calls")
    public Map<String, Long> getApiCallCounts() {
        return statsService.getAllApiCallCounts();
    }

    /** DELETE /api/weather/stats/calls — reset all API call counters. */
    @DeleteMapping("/stats/calls")
    public Map<String, String> resetApiCallCounts() {
        statsService.resetApiCallCounts();
        return Map.of("status", "reset");
    }

    /** GET /api/weather/stats/recent/{city} — last 10 weather observations for that city (Redis list). */
    @GetMapping("/stats/recent/{city}")
    public List<String> getRecentObservations(@PathVariable String city) {
        return statsService.getRecentObservations(city);
    }
}
