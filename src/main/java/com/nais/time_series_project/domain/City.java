package com.nais.time_series_project.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum City {

    BELGRADE   ("Beograd",     "RS", "continental",   44.8125, 20.4612),
    NOVI_SAD   ("Novi Sad",    "RS", "continental",   45.2517, 19.8369),
    NIS        ("Nis",         "RS", "continental",   43.3209, 21.8954),
    SUBOTICA   ("Subotica",    "RS", "continental",   46.1005, 19.6651),
    KRAGUJEVAC ("Kragujevac",  "RS", "continental",   44.0143, 20.9112),
    VIENNA     ("Bec",         "AT", "continental",   48.2082, 16.3738),
    BUDAPEST   ("Budimpesta",  "HU", "continental",   47.4979, 19.0402),
    ATHENS     ("Atina",       "GR", "mediterranean", 37.9838, 23.7275),
    BERLIN     ("Berlin",      "DE", "continental",   52.5200, 13.4050),
    STOCKHOLM  ("Stokholm",    "SE", "coastal",       59.3293, 18.0686);

    private final String displayName;
    private final String country;
    private final String climateZone;
    private final double latitude;
    private final double longitude;
}
