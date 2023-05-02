package com.mass.flightplan.vac;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface VAChartParser {
    AirportInfo parseAirportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException;

    HelipadInfo parseHeliportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException;
}
