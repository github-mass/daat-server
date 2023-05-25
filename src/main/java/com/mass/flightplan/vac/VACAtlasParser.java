package com.mass.flightplan.vac;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.openjdk.nashorn.api.tree.*;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@RequiredArgsConstructor
@Log4j2
@Deprecated
public class VACAtlasParser {

    private final VACAtlasProperties atlasProperties;
    private final RestTemplate rt;

    @SuppressWarnings("Duplicates")
    @NonNull
    public Map<String, String> fetchAirportMap()
        throws IOException, ExecutionException
    {
        String js;
        try {
            js = rt.getForObject(atlasProperties.getAirportListJsUrl(), String.class);
        } catch (RestClientException e) {
            throw new IOException("Could not fetch airport map from " + atlasProperties.getAirportListJsUrl(), e);
        }

        log.debug("Got airport list JS: {}", js);

        Parser p = Parser.create();
        CompilationUnitTree cut = p.parse("vacAtlasAirportList", js, null);

        TreeVisitor<Void, List<Object>> arrayExtractor = new SimpleTreeVisitorES6<>() {
            @Override
            public Void visitFunctionCall(final FunctionCallTree node, final List<Object> list) {
                node.getArguments().stream().map(LiteralTree.class::cast).map(LiteralTree::getValue).forEach(list::add);
                return null;
            }
        };

        Function<String, List<Object>> varExtractor = varName -> {
            List<Object> stash = new ArrayList<>();
            cut.accept(new SimpleTreeVisitorES6<Void, Void>() {
                @Override
                public Void visitVariable(final VariableTree node, Void nil) {
                    if (varName.equals(((IdentifierTree) node.getBinding()).getName())) {
                        node.getInitializer().accept(arrayExtractor, stash);
                    }
                    return null;
                }
            }, null);
            return stash;
        };

        List<Object> airportIds = varExtractor.apply("vaerosoussection");
        List<Object> airportNames = varExtractor.apply("vaeroportlong");

        if(airportIds == null || airportIds.isEmpty()){
            throw new ExecutionException(new IllegalArgumentException("Could not find airport IDs in " + js));
        }
        else if(airportNames == null || airportNames.isEmpty()){
            throw new ExecutionException(new IllegalArgumentException("Could not find airport names in " + js));
        }
        else if(airportIds.size() != airportNames.size()){
            throw new ExecutionException(new IllegalArgumentException(String.format(
                    "Airport IDs list and airport names list differ in size (%d != %d): %s",
                    airportIds.size(), airportNames.size(), js
            )));
        }

        Map<String, String> ret = new HashMap<>();
        for(int ii = 0; ii < airportIds.size(); ii++){
            ret.put(airportIds.get(ii).toString(), airportNames.get(ii).toString());
        }

        return ret;
    }

    @SuppressWarnings("Duplicates")
    @NonNull
    public Map<String, String> fetchHelipadMap()
        throws IOException, ExecutionException
    {
        String js;
        try {
            js = rt.getForObject(atlasProperties.getHelipadListJsUrl(), String.class);
        } catch (RestClientException e) {
            throw new IOException("Could not load helipad list from " + atlasProperties.getHelipadListJsUrl(), e);
        }

        log.debug("Got helipad list JS: {}", js);

        Parser p = Parser.create();
        CompilationUnitTree cut = p.parse("vacAtlasHelipadList", js, null);

        TreeVisitor<Void, List<Object>> arrayExtractor = new SimpleTreeVisitorES6<>() {
            @Override
            public Void visitFunctionCall(final FunctionCallTree node, final List<Object> list) {
                node.getArguments().stream().map(LiteralTree.class::cast).map(LiteralTree::getValue).forEach(list::add);
                return null;
            }
        };

        Function<String, List<Object>> varExtractor = varName -> {
            List<Object> stash = new ArrayList<>();
            cut.accept(new SimpleTreeVisitorES6<Void, Void>() {
                @Override
                public Void visitVariable(final VariableTree node, Void nil) {
                    if (varName.equals(((IdentifierTree) node.getBinding()).getName())) {
                        node.getInitializer().accept(arrayExtractor, stash);
                    }
                    return null;
                }
            }, null);
            return stash;
        };

        List<Object> helipadIds = varExtractor.apply("vaerosoussection");
        List<Object> helipadNames = varExtractor.apply("vaeroportlong");

        if(helipadIds == null || helipadIds.isEmpty()){
            throw new ExecutionException(new IllegalArgumentException("Could not find helipad IDs in " + js));
        }
        else if(helipadNames == null || helipadNames.isEmpty()){
            throw new ExecutionException(new IllegalArgumentException("Could not find helipad names in " + js));
        }
        else if(helipadIds.size() != helipadNames.size()){
            throw new ExecutionException(new IllegalArgumentException(String.format(
                    "Helipad IDs list and helipad names list differ in size (%d != %d): %s",
                    helipadIds.size(), helipadNames.size(), js
            )));
        }

        Map<String, String> ret = new HashMap<>();
        for(int ii = 0; ii < helipadIds.size(); ii++){
            ret.put(helipadIds.get(ii).toString(), helipadNames.get(ii).toString());
        }

        return ret;
    }

    public Resource fetchAirportVacCard(@NonNull String airportCode)
            throws IOException
    {
        String url = atlasProperties.getAirportCardUrlTemplate().replace("{code}", airportCode);
        log.debug("Downloading card for airport {} from {}...", airportCode, url);

        ResponseEntity<Resource> resp;
        try {
            resp = rt.getForEntity(url, Resource.class);
        } catch (RestClientException e) {
            throw new IOException("Could not load airport list from " + url, e);
        }

        if(!resp.getStatusCode().is2xxSuccessful()){
            throw new IOException(String.format(
                    "Could not get VAC card for airport '%s' from '%s': got response %s",
                    airportCode, url, resp.getStatusCode()
            ));
        }

        if(!MediaType.APPLICATION_PDF.isCompatibleWith(resp.getHeaders().getContentType())){
            throw new IOException(String.format(
                    "Invalid VAC card for airport %s at %s: expected PDF, but got %s",
                    airportCode, url, resp.getHeaders().getContentType()
            ));
        }

        return new ResourceWithUriDescriptor(resp.getBody(), URI.create(url));
    }

    public Resource fetchHelipadVacCard(@NonNull String helipadCode)
            throws IOException
    {
        String url = atlasProperties.getHelipadCardUrlTemplate().replace("{code}", helipadCode);
        log.debug("Downloading card for helipad {} from {}...", helipadCode, url);

        ResponseEntity<Resource> resp;
        try {
            resp = rt.getForEntity(url, Resource.class);
        } catch (RestClientException e) {
            throw new IOException("Could not download HVAC entry from " + url, e);
        }

        if(!resp.getStatusCode().is2xxSuccessful()){
            throw new IOException(String.format(
                    "Could not get VAC card for helipad '%s' from '%s': got response %s",
                    helipadCode, url, resp.getStatusCode()
            ));
        }

        if(!MediaType.APPLICATION_PDF.isCompatibleWith(resp.getHeaders().getContentType())){
            throw new IOException(String.format(
                    "Invalid VAC card for helipad %s at %s: expected PDF, but got %s",
                    helipadCode, url, resp.getHeaders().getContentType()
            ));
        }

        return new ResourceWithUriDescriptor(resp.getBody(), URI.create(url));
    }

}
