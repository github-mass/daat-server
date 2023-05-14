package com.mass.flightplan.web;

import com.mass.flightplan.geo.ProximityResponse;
import com.mass.flightplan.geo.ProximityService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping(path = {"api/proximity", "api/v1/proximity"})
public class ProximityController {

    private final @NonNull ProximityService service;

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ProximityResponse getProximityInfo(@RequestParam("lon") double longitude, @RequestParam("lat") double latitude){
        return service.computeFor(new Point(longitude, latitude));
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ProximityResponse postProximityInfo(@RequestBody GeoJsonPoint location){
        return service.computeFor(location);
    }

}
