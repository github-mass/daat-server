package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.openjdk.nashorn.api.tree.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@Log4j2
public class VACAtlasParser {

    private final VACAtlasProperties atlasProperties;
    private final RestTemplate rt;

    public VACAtlasParser(@Autowired @NonNull RestTemplateBuilder builder, @NonNull VACAtlasProperties atlasProperties) {
        this.atlasProperties = atlasProperties;
        this.rt = builder.build();
    }

    @SuppressWarnings("Duplicates")
    @NonNull
    public Map<String, String> getAirportMap() {
        String js = rt.getForObject(atlasProperties.getAirportListJsUrl(), String.class);

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
            throw new IllegalArgumentException("Could not find airport IDs in " + js);
        }
        else if(airportNames == null || airportNames.isEmpty()){
            throw new IllegalArgumentException("Could not find airport names in " + js);
        }
        else if(airportIds.size() != airportNames.size()){
            throw new IllegalArgumentException(String.format(
                    "Airport IDs list and airport names list differ in size (%d != %d): %s",
                    airportIds.size(), airportNames.size(), js
            ));
        }

        Map<String, String> ret = new HashMap<>();
        for(int ii = 0; ii < airportIds.size(); ii++){
            ret.put(airportIds.get(ii).toString(), airportNames.get(ii).toString());
        }

        return ret;
    }

    @SuppressWarnings("Duplicates")
    @NonNull
    public Map<String, String> getHeliportMap() {
        String js = rt.getForObject(atlasProperties.getHeliportListJsUrl(), String.class);

        log.debug("Got heliport list JS: {}", js);

        Parser p = Parser.create();
        CompilationUnitTree cut = p.parse("vacAtlasHeliportList", js, null);

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

        List<Object> heliportIds = varExtractor.apply("vaerosoussection");
        List<Object> heliportNames = varExtractor.apply("vaeroportlong");

        if(heliportIds == null || heliportIds.isEmpty()){
            throw new IllegalArgumentException("Could not find heliport IDs in " + js);
        }
        else if(heliportNames == null || heliportNames.isEmpty()){
            throw new IllegalArgumentException("Could not find heliport names in " + js);
        }
        else if(heliportIds.size() != heliportNames.size()){
            throw new IllegalArgumentException(String.format(
                    "Heliport IDs list and heliport names list differ in size (%d != %d): %s",
                    heliportIds.size(), heliportNames.size(), js
            ));
        }

        Map<String, String> ret = new HashMap<>();
        for(int ii = 0; ii < heliportIds.size(); ii++){
            ret.put(heliportIds.get(ii).toString(), heliportNames.get(ii).toString());
        }

        return ret;
    }

    public Resource fetchAirportVacCard(@NonNull String airportCode)
            throws IOException
    {
        String url = atlasProperties.getAirportCardUrlTemplate().replace("{code}", airportCode);
        log.debug("Downloading card for airport {} from {}...", airportCode, url);

        ResponseEntity<Resource> resp = rt.getForEntity(url, Resource.class);

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

        return resp.getBody();
    }

    public Resource fetchHeliportVacCard(@NonNull String heliportCode)
            throws IOException
    {
        String url = atlasProperties.getHeliportCardUrlTemplate().replace("{code}", heliportCode);
        log.debug("Downloading card for heliport {} from {}...", heliportCode, url);

        ResponseEntity<Resource> resp = rt.getForEntity(url, Resource.class);

        if(!resp.getStatusCode().is2xxSuccessful()){
            throw new IOException(String.format(
                    "Could not get VAC card for heliport '%s' from '%s': got response %s",
                    heliportCode, url, resp.getStatusCode()
            ));
        }

        if(!MediaType.APPLICATION_PDF.isCompatibleWith(resp.getHeaders().getContentType())){
            throw new IOException(String.format(
                    "Invalid VAC card for heliport %s at %s: expected PDF, but got %s",
                    heliportCode, url, resp.getHeaders().getContentType()
            ));
        }

        return resp.getBody();
    }

}
