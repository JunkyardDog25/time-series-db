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
@Measurement(name = "air_quality")
@NoArgsConstructor @AllArgsConstructor
public class AirQualityObservation {

    @Column(tag = true)
    private String city;

    @Column(tag = true)
    private String country;

    @Column
    private Double pm25;

    @Column
    private Double pm10;

    @Column
    private Double no2;

    @Column
    private Double o3;

    @Column
    private Double co;

    @Column(timestamp = true)
    private Instant time;
}
