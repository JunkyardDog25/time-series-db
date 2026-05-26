package com.nais.time_series_project.model;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
@Measurement(name = "weather_observation")
public class WeatherObservation {

    @Column(tag = true)
    private String city;

    @Column(tag = true)
    private String country;

    @Column(tag = true)
    private String climateZone;

    @Column
    private Double temperatureC;

    @Column
    private Double humidityPct;

    @Column
    private Double pressurehPa;

    @Column
    private Double windSpeedMs;

    @Column
    private Double windDirectionDeg;

    @Column
    private Double precipitationMm;

    @Column
    private Double cloudCoverPct;

    @Column(timestamp = true)
    private Instant time;
}
