package com.nais.time_series_project.service;

import com.influxdb.client.DeleteApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.DeletePredicateRequest;
import com.influxdb.client.domain.WritePrecision;
import com.nais.time_series_project.model.AirQualityObservation;
import com.nais.time_series_project.model.WeatherObservation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
public class WeatherWriteService {


    private static final String WEATHER_MEASUREMENT = "weather_observation";
    private static final String AIR_QUALITY_MEASUREMENT = "air_quality";
    private static final OffsetDateTime EPOCH_START =
            OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FAR_FUTURE =
            OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final InfluxDBClient influxDBClient;

    private final RedisStatsService statsService;

    private final String bucket;

    private final String organization;

    public  WeatherWriteService(InfluxDBClient influxDBClient,
                                RedisStatsService statsService,
                                @Value("${influxdb.bucket}") String bucket,
                                @Value("${influxdb.org}") String org) {
        this.influxDBClient = influxDBClient;
        this.statsService = statsService;
        this.bucket = bucket;
        this.organization = org;
    }

    @Caching(evict = {
            @CacheEvict(value = WeatherQueryService.CACHE_AVG_TEMP, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_AMPLITUDE, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_STORM, allEntries = true),
    })
    public WeatherObservation createWeatherObservation(WeatherObservation observation) {
        if (observation.getTime() == null) {
            observation.setTime(Instant.now());
        }

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writeMeasurement(WritePrecision.S, observation);
        log.info("Created weather_observation for city={} at {}", observation.getCity(), observation.getTime());

        statsService.pushRecentObservation(observation);

        return observation;
    }

    @Caching(evict = {
            @CacheEvict(value = WeatherQueryService.CACHE_AVG_TEMP,  allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_AMPLITUDE, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_STORM,     allEntries = true)
    })
    public AirQualityObservation createAirQualityObservation(AirQualityObservation observation) {
        if (observation.getTime() == null) {
            observation.setTime(Instant.now());
        }

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writeMeasurement(WritePrecision.S, observation);
        log.info("Created air_quality observation for city={} at {}", observation.getCity(), observation.getTime());

        return observation;
    }

    // ---------- DELETE ----------

    @Caching(evict = {
            @CacheEvict(value = WeatherQueryService.CACHE_AVG_TEMP,  allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_AMPLITUDE, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_STORM,     allEntries = true)
    })
    public void deleteWeatherObservations(OffsetDateTime start, OffsetDateTime stop, String city) {
        delete(WEATHER_MEASUREMENT, start, stop, city);
    }

    @Caching(evict = {
            @CacheEvict(value = WeatherQueryService.CACHE_AVG_TEMP,  allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_AMPLITUDE, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_STORM,     allEntries = true)
    })
    public void deleteAirQualityObservations(OffsetDateTime start, OffsetDateTime stop, String city) {
        delete(AIR_QUALITY_MEASUREMENT, start, stop, city);
    }

    @Caching(evict = {
            @CacheEvict(value = WeatherQueryService.CACHE_AVG_TEMP,  allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_AMPLITUDE, allEntries = true),
            @CacheEvict(value = WeatherQueryService.CACHE_STORM,     allEntries = true)
    })
    public void deleteAll() {
        log.warn("DELETE ALL invoked — wiping both measurements from bucket '{}'", bucket);
        delete(WEATHER_MEASUREMENT, EPOCH_START, FAR_FUTURE, null);
        delete(AIR_QUALITY_MEASUREMENT, EPOCH_START, FAR_FUTURE, null);
        log.warn("DELETE ALL completed");
    }

    private void delete(String measurement, OffsetDateTime start, OffsetDateTime stop, String city) {
        StringBuilder predicate = new StringBuilder()
                .append("_measurement=\"").append(measurement).append("\"");

        if (city != null && !city.isBlank()) {
            predicate.append(" AND city=\"").append(city).append("\"");
        }

        DeleteApi deleteApi = influxDBClient.getDeleteApi();
        DeletePredicateRequest request = new DeletePredicateRequest()
                .start(start)
                .stop(stop)
                .predicate(predicate.toString());

        deleteApi.delete(request, bucket, organization);
        log.info("Deleted from {} where {} between {} and {}", measurement, predicate, start, stop);
    }
}
