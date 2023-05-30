package com.mass.flightplan.aixm;

import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.event.Level;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.xml.xpath.XPathExpressionException;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Stream;

import static com.mass.flightplan.aixm.AixmUtils.*;

@RequiredArgsConstructor
@Slf4j
public class RunwayExtractor
    implements AixmExtractor<List<Runway>>
{
    private final @lombok.NonNull String adUid;

    @Override
    @NonNull
    public List<Runway> extract(@NonNull XPathDocumentExtractor source)
        throws XPathExpressionException, TransformException
    {
        var rwys = findRunways(source);
        log.atLevel(Level.TRACE).addKeyValue("adUid", adUid).log("Found {} runways: {}", rwys.size(), rwys);

        return rwys.values().stream().map(Runway.RunwayBuilder::build).toList();
    }

    private Map<String, Runway.RunwayBuilder> findRunways(XPathDocumentExtractor dex)
        throws XPathExpressionException, TransformException
    {
        List<Node> nl = dex.extractNodes(runwayPathExpression(adUid));

        /*
            <Rwy>
                <RwyUid mid="1528507">
                    <AhpUid mid="1521468">
                        <codeId>LFID</codeId>
                    </AhpUid>
                    <txtDesig>11/29</txtDesig>
                </RwyUid>
                <valLen>850</valLen>
                <valWid>50</valWid>
                <uomDimRwy>M</uomDimRwy>
                <codeComposition>GRASS</codeComposition>
            </Rwy>
         */

        Map<String, Runway.RunwayBuilder> ret = new HashMap<>();

        for (Node rwy : nl) {
            assert rwy.getNodeType() == Node.ELEMENT_NODE : rwy;

            var m = mapChildren(rwy);
            var b = Runway.builder();

            var rwyId = mapAttributes(m.get("RwyUid")).get("mid");

            log.trace("Parsing runway {}", rwyId);

            var designation = mapChildren(m.get("RwyUid")).get("txtDesig").getTextContent();

            var sizeUnit = parseLengthUnit(m.get("uomDimRwy").getTextContent());
            Quantity<Length> length = Quantities.getQuantity(Double.parseDouble(m.get("valLen").getTextContent()), sizeUnit);
            Quantity<Length> width = Quantities.getQuantity(Double.parseDouble(m.get("valWid").getTextContent()), sizeUnit);

            var composition = Optional.ofNullable(m.get("codeComposition")).map(Node::getTextContent).orElse(null);

            b.designation(designation)
             .length(length)
             .width(width)
             .surface(composition)
             .paved(isPaved(composition));

            findElevations(rwyId, b, dex);
            findCoordinatesAndBearings(rwyId, b, dex);

            ret.put(designation, b);
        }

        return ret;
    }

    private void findElevations(String runwayId, Runway.RunwayBuilder builder, XPathDocumentExtractor dex)
        throws XPathExpressionException
    {
        List<Node> nl = dex.extractNodes(runwayCentrePathExpression(runwayId));

        /*
        <Rcp>
            <RcpUid mid="1532605">
                <RwyUid mid="1528507">
                    <AhpUid mid="1521468">
                        <codeId>LFID</codeId>
                    </AhpUid>
                    <txtDesig>11/29</txtDesig>
                </RwyUid>
                <geoLat>435430.67N</geoLat>
                <geoLong>0002319.11E</geoLong>
            </RcpUid>
            <codeDatum>WGE</codeDatum>
            <valElev>441</valElev>
            <uomDistVer>FT</uomDistVer>
        </Rcp>
         */

        Stream<Quantity<Length>> rwyAlts = nl.stream()
                                   .filter(n -> n.getNodeType() == Node.ELEMENT_NODE)
                                   .map(AixmUtils::mapChildren)
                                   .filter(m -> m.containsKey("valElev"))
                                   .map(m -> Quantities.getQuantity(Double.parseDouble(m.get("valElev").getTextContent()), AixmUtils.parseLengthUnit(m.get("uomDistVer").getTextContent())));

        DoubleSummaryStatistics dss = new DoubleSummaryStatistics();
        rwyAlts.mapToDouble(l -> l.to(Units.METRE).getValue().doubleValue()).forEach(dss);

        if (dss.getCount() == 0) {
            log.debug("No RunwayCentrePositions found for runway {}", runwayId);
        }
        else {
            builder.minElevation(Quantities.getQuantity(dss.getMin(), Units.METRE)).maxElevation(Quantities.getQuantity(dss.getMax(), Units.METRE));
        }
    }

    private void findCoordinatesAndBearings(String runwayId, Runway.RunwayBuilder builder, XPathDocumentExtractor dex)
        throws XPathExpressionException, TransformException
    {
        List<Node> nl = dex.extractNodes(runwayDirectionExpression(runwayId));

        /*
            We should get one or two of these:

            <Rdn>
                <RdnUid mid="1536039">
                    <RwyUid mid="1528507">
                        <AhpUid mid="1521468">
                            <codeId>LFID</codeId>
                        </AhpUid>
                        <txtDesig>11/29</txtDesig>
                    </RwyUid>
                    <txtDesig>29</txtDesig>
                </RdnUid>
                <geoLat>435429.17N</geoLat>
                <geoLong>0002324.26E</geoLong>
                <valTrueBrg>291.96</valTrueBrg>
                <valMagBrg>292.75</valMagBrg>
            </Rdn>

            If we have one, we just take the coordinate and bearing from it.
            If we have two, we calculate the centre point between the two and take either bearing.
         */

        List<Node> rdns = nl.stream().filter(n -> n.getNodeType() == Node.ELEMENT_NODE).toList();

        if (rdns.size() < 1 || rdns.size() > 2) {
            throw new IllegalArgumentException("Expected 1 or 2 Rdn elements for runway " + runwayId + ", but found " + rdns.size());
        }

        Point rwyCoord;
        Double trueBearing, magBearing;

        var map = mapChildren(rdns.get(0));
        if (rdns.size() == 1) {
            if (map.containsKey("geoLong")) {
                rwyCoord = new Point(lonToDecimal(map.get("geoLong").getTextContent()), latToDecimal(map.get("geoLat").getTextContent()));
            }
            else {
                rwyCoord = null;
            }

            trueBearing = Optional.ofNullable(map.get("valTrueBrg")).map(Node::getTextContent).map(Double::parseDouble).orElse(null);
            magBearing = Optional.ofNullable(map.get("valMagBrg")).map(Node::getTextContent).map(Double::parseDouble).orElse(null);
        }
        else {
            trueBearing = Optional.ofNullable(map.get("valTrueBrg")).map(Node::getTextContent).map(Double::parseDouble).orElse(null);
            magBearing = Optional.ofNullable(map.get("valMagBrg")).map(Node::getTextContent).map(Double::parseDouble).orElse(null);

            DirectPosition2D pos1 = null, pos2 = null;
            if (map.containsKey("geoLong")) {
                pos1 = new DirectPosition2D(lonToDecimal(map.get("geoLong").getTextContent()), latToDecimal(map.get("geoLat").getTextContent()));
            }

            map = mapChildren(rdns.get(1));
            if(map.containsKey("geoLong")) {
                pos2 = new DirectPosition2D(lonToDecimal(map.get("geoLong").getTextContent()), latToDecimal(map.get("geoLat").getTextContent()));
            }

            if(pos1 != null && pos2 != null){
                GeodeticCalculator gc = new GeodeticCalculator();
                gc.setStartingPosition(pos1);
                gc.setDestinationPosition(pos2);

                List<Point2D> path = gc.getGeodeticPath(1);

                rwyCoord = new Point(path.get(1).getX(), path.get(1).getY());
            }
            else {
                rwyCoord = Stream.of(pos1, pos2).filter(Objects::nonNull).map(p -> new Point(p.x, p.y)).findFirst().orElse(null);
            }
        }

        if(magBearing == null && trueBearing == null) {
//            throw new IllegalArgumentException("Both mag bearing and true bearing are missing for runway %s".formatted(runwayId));
            //this can actually happen; Seen it for hydrobase.
        }

        builder.coordinates(rwyCoord).magBearing(magBearing).trueBearing(trueBearing);
    }

    String runwayPathExpression(String adUid) {
        // language=XPath
        return "/AIXM-Snapshot/Rwy[RwyUid/AhpUid/@mid='" + adUid + "']";
    }

    String runwayCentrePathExpression(String runwayId) {
        // language=XPath
        return "/AIXM-Snapshot/Rcp[RcpUid/RwyUid/@mid='" + runwayId + "']";
    }

    String runwayDirectionExpression(String runwayId) {
        // language=XPath
        return "/AIXM-Snapshot/Rdn[RdnUid/RwyUid/@mid='" + runwayId + "']";
    }
}
