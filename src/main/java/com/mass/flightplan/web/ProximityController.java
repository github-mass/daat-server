package com.mass.flightplan.web;

import com.mass.flightplan.aixm.AixmUtils;
import com.mass.flightplan.geo.ProximityResponse;
import com.mass.flightplan.geo.ProximityService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping(path = {"api/proximity", "api/v1/proximity"})
@CrossOrigin
public class ProximityController {

    private final @NonNull ProximityService service;

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getProximityInfo(@RequestParam("lon") String longitude, @RequestParam("lat") String latitude) {
        try {
            double dLon = lonToDecimal(longitude);
            double dLat = latToDecimal(latitude);

            ProximityResponse resp = service.computeFor(new Point(dLon, dLat));
            return ResponseEntity.ok(resp);
        }
        catch (IllegalArgumentException iaex) {
            return ResponseEntity.badRequest().body(iaex.getMessage());
        }
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ProximityResponse postProximityInfo(@RequestBody GeoJsonPoint location){
        return service.computeFor(location);
    }

    static double lonToDecimal(String lon){
        if(!StringUtils.hasText(lon)){
            throw new IllegalArgumentException("No longitude specified");
        }

        try {
            return Double.parseDouble(lon);
        }
        catch (NumberFormatException nfex) {
            //ignore
        }
        try {
            //degrees, minutes and seconds, with decimal parts, trailing one optional, without spaces
            lon = lon.replaceAll("\\s+", "");
            return AixmUtils.lonToDecimal(lon);
        }
        catch (NumberFormatException nfex) {
            //ignore
        }

        throw new IllegalArgumentException("Unknown format for longitude: " + lon);
    }

    static double latToDecimal(String lat){
        if(!StringUtils.hasText(lat)){
            throw new IllegalArgumentException("No latitude specified");
        }

        try {
            return Double.parseDouble(lat);
        }
        catch (NumberFormatException nfex) {
            //ignore
        }
        try {
            //degrees, minutes and seconds, with decimal parts, trailing one optional, without spaces
            lat = lat.replaceAll("\\s+", "");
            return AixmUtils.latToDecimal(lat);
        }
        catch (NumberFormatException nfex) {
            //ignore
        }

        throw new IllegalArgumentException("Unknown format for latitude: " + lat);
    }

}
