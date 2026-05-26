package com.nais.time_series_project.service;

import com.nais.time_series_project.model.WeatherObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demonstrates direct Redis usage (independent of Spring Cache abstraction).
 * <p>
 * Uses two classic key-value patterns:
 *  - Counters via INCR — track API call frequency per endpoint.
 *  - Capped lists via LPUSH + LTRIM — keep the last N observations per city.
 * <p>
 * Key naming convention:
 *  - api:calls:{endpoint}           → Long counter
 *  - recent:obs:{city}              → List<String>, capped at MAX_RECENT_PER_CITY
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisStatsService {

    private static final String CALLS_KEY_PREFIX = "api:calls:";
    private static final String RECENT_KEY_PREFIX = "recent:obs:";
    private static final long MAX_RECENT_PER_CITY = 10;

    private final StringRedisTemplate redis;

    public void incrementApiCallCount(String endpoint) {
        redis.opsForValue().increment(CALLS_KEY_PREFIX + endpoint);
    }

    public Map<String, Long> getAllApiCallCounts() {
        Set<String> keys = redis.keys(CALLS_KEY_PREFIX + "*");
        Map<String, Long> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        for (String key : keys) {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                result.put(key.substring(CALLS_KEY_PREFIX.length()), Long.parseLong(value));
            }
        }
        return result;
    }

    public void resetApiCallCounts() {
        Set<String> keys = redis.keys(CALLS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.info("Reset {} API call counters", keys.size());
        }
    }

    // ---------- Capped recent-observations list ----------

    public void pushRecentObservation(WeatherObservation observation) {
        if (observation.getCity() == null) {
            return;
        }
        String key = RECENT_KEY_PREFIX + observation.getCity();
        String summary = String.format("[%s] Temperature=%.1f°C, Humidity=%.0f%%, Pressure=%.1f hPa",
                observation.getTime(),
                observation.getTemperatureC(),
                observation.getHumidityPct(),
                observation.getPressurehPa());

        redis.opsForList().leftPush(key, summary);
        redis.opsForList().trim(key, 0, MAX_RECENT_PER_CITY - 1);
    }

    public List<String> getRecentObservations(String city) {
        return redis.opsForList().range(RECENT_KEY_PREFIX + city, 0, -1);
    }
}
