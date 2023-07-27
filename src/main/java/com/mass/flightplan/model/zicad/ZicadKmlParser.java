package com.mass.flightplan.model.zicad;

import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.kml.KMLReader;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mass.flightplan.model.NodeUtils.*;

@Slf4j
public class ZicadKmlParser {


    public @NonNull
    Stream<ZicadZone> parse(@NonNull InputStream is)
        throws IOException
    {
        XPathDocumentExtractor extr = XPathDocumentExtractor.from(is).namespaceAware(false).build();

        try {
            checkSchema(extr);
            return parsePlacemarks(extr);
        }
        catch (XPathExpressionException e) {
            log.error("", e);
            return Stream.of();
        }
    }

    private void checkSchema(XPathDocumentExtractor extr)
        throws XPathExpressionException
    {
        Node schema = extr.extractNode("/kml/Document/Schema");

        if (schema == null) {
            throw new IllegalArgumentException("Could not find schema node");
        }

        Collection<String> expected = new HashSet<>(Set.of("comm", "ministere", "dateeffet", "area", "site"));
        Collection<Node> found = childElements(schema).collect(Collectors.toCollection(ArrayList::new));

        for (Iterator<Node> it = found.iterator(); it.hasNext(); ) {
            Node n = it.next();

            String name = Optional.of(n).map(Node::getAttributes).map(atts -> atts.getNamedItem("name")).map(Node::getTextContent).orElse(null);
            if (name != null && expected.remove(name)) {
                it.remove();
            }
        }

        if (!expected.isEmpty()) {
            throw new IllegalArgumentException("Expected fields not found in KML schema: " + expected);
        }
        if (!found.isEmpty()) {
            log.warn("Unexpected elements in KML schema: {}", found);
        }
    }

    Stream<ZicadZone> parsePlacemarks(XPathDocumentExtractor extr)
        throws XPathExpressionException
    {
        PrecisionModel pm = new PrecisionModel(1000000);
        KMLReader geoReader = new KMLReader(new GeometryFactory(pm));

        return extr.extractNodes("/kml/Document/Folder/Placemark")
                   .stream()
                   .map(n -> {
                       try {
                           return parsePlacemark(n, geoReader);
                       }
                       catch (Exception e) {
                           log.warn("Could not parse placemark", e);
                           return null;
                       }
                   })
                   .filter(Objects::nonNull);
    }

    ZicadZone parsePlacemark(Node placemark, KMLReader geoReader)
        throws ParseException
    {
        Node schemadata = child(placemark, "ExtendedData").flatMap(n -> child(n, "SchemaData"))
                                                          .orElseThrow(() -> new IllegalArgumentException("No schemadata found in : " + nodeToString(placemark)));

        Node geomNode = child(placemark, "MultiGeometry")
            .orElseThrow(() -> new IllegalArgumentException("No MultiGeometry found in : " + nodeToString(placemark)));

        SchemaData sd = new SchemaData(schemadata);
        Geometry geo = geoReader.read(nodeToString(geomNode).toString());

//        geo = DouglasPeuckerSimplifier.simplify(geo, 0.000001);

        var ret = sd.toZoneBuilder().geometry(geo).build();

        if (ret.commune().toLowerCase().contains("aurillac")) {
            System.out.println("stop");
        }

        return ret;
    }

    @Value
    private static class SchemaData {
        @lombok.NonNull Node node;

        ZicadZone.ZicadZoneBuilder toZoneBuilder() {
            Map<String, String> data = childElements(node)
                .collect(Collectors.toMap(
                    n -> n.getAttributes().getNamedItem("name").getTextContent(),
                    Node::getTextContent
                ));

            DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                .appendValue(ChronoField.DAY_OF_MONTH, 2)
                .appendLiteral('-')
                .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(ChronoField.YEAR, 4)
                .toFormatter();


            return ZicadZone.builder()
                            .commune(data.get("comm"))
                            .ministry(data.get("ministere"))
                            .effective(dtf.parse(data.get("dateeffet"), LocalDate::from).atStartOfDay().toInstant(ZoneOffset.UTC))
                            .areaId(data.get("area"))
                            .siteName(data.get("site"))
                ;
        }
    }

}
