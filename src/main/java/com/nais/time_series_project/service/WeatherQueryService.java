package com.nais.time_series_project.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherQueryService {

    public static final String CACHE_AVG_TEMP = "avg-temperature";
    public static final String CACHE_AMPLITUDE = "daily-amplitude";
    public static final String CACHE_STORM = "storm-events";

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * Q1 — filter + group + aggregate + sort
     * Average temperature per city over the last N days, sorted descending.
     */
    @Cacheable(value = CACHE_AVG_TEMP, key = "#days")
    public List<Map<String, Object>> averageTemperatureByCity(int days) {
        log.info("CACHE MISS — querying InfluxDB for avg-temperature (days={})", days);

        String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "weather_observation")
                  |> filter(fn: (r) => r._field == "temperatureC")
                  |> group(columns: ["city"])
                  |> mean()
                  |> sort(columns: ["_value"], desc: true)
                """, bucket, days);

        return runQuery(flux, List.of("city", "_value"));
    }

    /**
     * Q2 — windowed aggregation + grouping
     * Daily temperature amplitude (max - min) per city for the last N days.
     */
    @Cacheable(value = CACHE_AMPLITUDE, key = "#days")
    public List<Map<String, Object>> dailyTemperatureAmplitude(int days) {
        log.info("CACHE MISS — querying InfluxDB for daily-amplitude (days={})", days);

        String flux = String.format("""
                maxT = from(bucket: "%s")
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "weather_observation" and r._field == "temperatureC")
                  |> aggregateWindow(every: 1d, fn: max, createEmpty: false)
                  |> set(key: "stat", value: "max")
 
                minT = from(bucket: "%s")
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "weather_observation" and r._field == "temperatureC")
                  |> aggregateWindow(every: 1d, fn: min, createEmpty: false)
                  |> set(key: "stat", value: "min")
 
                union(tables: [maxT, minT])
                  |> pivot(rowKey: ["_time", "city"], columnKey: ["stat"], valueColumn: "_value")
                  |> map(fn: (r) => ({ r with amplitude: r.max - r.min }))
                  |> keep(columns: ["_time", "city", "amplitude"])
                  |> sort(columns: ["amplitude"], desc: true)
                """, bucket, days, bucket, days);

        return runQuery(flux, List.of("_time", "city", "amplitude"));
    }

    /**
     * Q3 — filter + window aggregation + conditional filter + sort
     * Detects 6h windows where pressure spread exceeded the threshold (storm-approach indicator).
     * Returns only cities with at least minOccurrences such events.
     */
    @Cacheable(value = CACHE_STORM, key = "'d=' + #days + ',thr=' + #thresholdHpa + ',min=' + #minOccurrences")
    public List<Map<String, Object>> stormApproachEvents(int days, double thresholdHpa, int minOccurrences) {
        log.info("CACHE MISS — querying InfluxDB for storm-events (days={}, thr={}, min={})", days, thresholdHpa, minOccurrences);

        String flux = String.format(Locale.US, """
                from(bucket: "%s")
                  |> range(start: -%dd)
                  |> filter(fn: (r) => r._measurement == "weather_observation" and r._field == "pressurehPa")
                  |> aggregateWindow(every: 6h, fn: spread, createEmpty: false)
                  |> filter(fn: (r) => r._value > %.1f)
                  |> group(columns: ["city"])
                  |> count()
                  |> filter(fn: (r) => r._value >= %d)
                  |> sort(columns: ["_value"], desc: true)
                """, bucket, days, thresholdHpa, minOccurrences);

        return runQuery(flux, List.of("city", "_value"));
    }

    private List<Map<String, Object>> runQuery(String flux, List<String> columns) {
        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux);

        List<Map<String, Object>> results = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    Object value = record.getValueByKey(col);

                    if (value instanceof Instant instant) {
                        value = instant.toString();
                    }
                    row.put(col, value);
                }
                results.add(row);
            }
        }
        return results;
    }
}
