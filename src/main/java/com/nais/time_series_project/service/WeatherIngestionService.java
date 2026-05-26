package com.nais.time_series_project.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.nais.time_series_project.client.OpenMeteoClient;
import com.nais.time_series_project.domain.City;
import com.nais.time_series_project.model.AirQualityObservation;
import com.nais.time_series_project.model.WeatherObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherIngestionService {

    private final InfluxDBClient influxDBClient;

    private final OpenMeteoClient openMeteoClient;

    public int ingestAllCities(LocalDate start, LocalDate end) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        int totalRecords = 0;

        for (City city : City.values()) {
            List<WeatherObservation> weather = openMeteoClient.fetchWeather(city, start, end);
            writeApi.writeMeasurements(WritePrecision.S, weather);
            log.info("Ingested {} weather records for {}", weather.size(), city.getDisplayName());

            List<AirQualityObservation> air = openMeteoClient.fetchAirQuality(city, start, end);
            writeApi.writeMeasurements(WritePrecision.S, air);
            log.info("Ingested {} air quality records for {}", air.size(), city.getDisplayName());

            totalRecords += weather.size() + air.size();
        }

        log.info("Total records ingested: {}", totalRecords);
        return totalRecords;
    }
}
