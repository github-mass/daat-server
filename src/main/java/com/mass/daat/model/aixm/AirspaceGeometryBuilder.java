package com.mass.daat.model.aixm;

import com.mass.daat.model.NodeUtils;
import com.mass.daat.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.intellij.lang.annotations.Language;
import org.locationtech.jts.geom.*;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.w3c.dom.Node;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.xml.xpath.XPathExpressionException;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import static com.mass.daat.model.ModelUtils.*;
import static com.mass.daat.model.NodeUtils.*;
import static java.lang.Double.parseDouble;
import static java.lang.Math.PI;
import static java.lang.Math.abs;

@RequiredArgsConstructor
@Slf4j
public class AirspaceGeometryBuilder {

    @SneakyThrows(XPathExpressionException.class)
    public Airspace buildAirspace(@NonNull String airspaceId, @NonNull XPathDocumentExtractor dex)
    {
        Node ase = dex.extractNode(airspacePathExpression(airspaceId));

        if (ase == null) {
            throw new IllegalArgumentException("No such airspace: " + airspaceId);
        }

        var builder = Airspace.builder();
        var nm = mapChildren(ase);

        builder.id(airspaceId);

        String aixmCode = mapChildren(nm.get("AseUid")).get("codeType").getTextContent();
        String localType = Optional.ofNullable(nm.get("txtLocalType")).map(Node::getTextContent).orElse(null);
        final AirspaceType aseType = AirspaceType.aixmParse(aixmCode, localType).orElseThrow(() -> new IllegalArgumentException("Could not determine airspace type: codeType=%s, txtLocalType=%s".formatted(aixmCode, localType)));
        builder.type(aseType.code());

        builder.code(nm.get("txtName").getTextContent());

        Optional.ofNullable(nm.get("txtRmk")).map(Node::getTextContent).ifPresent(builder::remarks);

        Optional.ofNullable(nm.get("Att")).map(NodeUtils::mapChildren).map(m -> m.get("codeWorkHr")).map(Node::getTextContent).ifPresent(builder::activationType);
        Optional.ofNullable(nm.get("Att")).map(NodeUtils::mapChildren).map(m -> m.get("txtRmkWorkHr")).map(Node::getTextContent).ifPresent(builder::activationRemarks);

        //check for complex geometry
        Node n = dex.extractNode(airspaceDerivedGeometryPathExpression(airspaceId));

        Geometry g;
        if (n == null) {
            // OK, simple case
            builder.geometry(g = buildSimpleGeometry(ase, dex, builder));
        }
        else {
            // OK, less simple case
            builder.geometry(g = buildComplexGeometry(n, dex, builder));
        }

        Optional.ofNullable(g.getUserData())
                .filter(Map.class::isInstance)
                .map(o -> (String) ((Map<?, ?>) o).get("FNT"))
                .filter(StringUtils::hasText)
                .map(s -> Set.copyOf(List.of(s.split(","))))
                .ifPresent(builder::frontiers);

        return builder.build();
    }

    private Geometry buildComplexGeometry(Node derivedGeometryNode, XPathDocumentExtractor dex, Airspace.AirspaceBuilder builder)
        throws XPathExpressionException
    {
        /*
            <Adg>
                <AdgUid>
                    <AseUid mid="1560477">
                        <codeType>CTR</codeType>
                        <codeId>LFMI</codeId>
                    </AseUid>
                </AdgUid>
                <AseUidBase mid="1565879">
                    <codeType>CTR</codeType>
                    <codeId>LFMI1.1</codeId>
                </AseUidBase>
                <codeOpr>UNION</codeOpr>
                <AseUidComponent mid="1565893">
                    <codeType>CTR</codeType>
                    <codeId>LFMI1.2</codeId>
                </AseUidComponent>
                <codeOpr>UNION</codeOpr>
                <AseUidComponent mid="1610748">
                    <codeType>CTR</codeType>
                    <codeId>LFMI1.3</codeId>
                </AseUidComponent>
            </Adg>
         */


        List<Node> relevantChildren = toStream(derivedGeometryNode.getChildNodes())
            .filter(n -> n.getNodeType() == Node.ELEMENT_NODE)
            .filter(n -> {
                if ("AdgUid".equals(n.getNodeName())) {
                    log.atTrace().log("Building derived geometry for airspace {}", mapAttributes(mapChildren(n).get("AseUid")).get("mid"));
                    return false;
                }

                return true;
            })
            .toList();

        if (relevantChildren.size() % 2 != 1) {
            throw new IllegalArgumentException("Expected odd number of derived geometry children, but got " + relevantChildren.stream().map(Node::getNodeName).toList());
        }

        // AseUidBase
        String base = mapAttributes(relevantChildren.get(0)).get("mid");

        log.trace("Using base airspace: {}", base);
        Geometry geom = buildSimpleGeometry(base, dex, builder);

        for (int ii = 1; ii < relevantChildren.size(); ii += 2) {
            String op = relevantChildren.get(ii).getTextContent();
            String componentId = mapAttributes(relevantChildren.get(ii + 1)).get("mid");

            log.trace("building component airspace {}", componentId);
            Geometry other = buildSimpleGeometry(componentId, dex, builder);

            log.trace("Applying {}", op);

            switch (op) {
                case "UNION" -> {
                    geom = geom.union(other);
                    copyFrontiers(List.of(geom, other), geom);
                }
                case "INTERS" -> {
                    geom = geom.intersection(other);
                    copyFrontiers(List.of(geom, other), geom);
                }
                case "SUBTR" -> {
                    geom = geom.difference(other);
                    copyFrontiers(List.of(geom, other), geom);
                }
                default -> throw new IllegalArgumentException("Unexpected airspace composition type: " + op);
            }
        }

        return geom;
    }

    private Geometry buildSimpleGeometry(String aseId, XPathDocumentExtractor dex, Airspace.AirspaceBuilder builder)
        throws XPathExpressionException
    {
        Node n = dex.extractNode(airspacePathExpression(aseId));
        if (n == null) {
            throw new IllegalArgumentException("No such airspace: " + aseId);
        }

        return buildSimpleGeometry(n, dex, builder);
    }

    private Geometry buildSimpleGeometry(Node aseNode, XPathDocumentExtractor dex, Airspace.AirspaceBuilder builder)
        throws XPathExpressionException
    {
        var nodeMap = mapChildren(aseNode);
        String aseId = mapAttributes(nodeMap.get("AseUid")).get("mid");

        log.trace("Building simple geometry for airspace {}", aseId);

        //just check heights, rest will be handled elsewhere
        /*
            We're using custom units here (see AixmUtils), since we have to handle
            ft (height), ft (altitude) and Flight Level.
         */
        Quantity<Length> floor = Quantities.getQuantity(
            parseDouble(nodeMap.get("valDistVerLower").getTextContent()),
            parseLengthUnit(nodeMap.get("uomDistVerLower").getTextContent() + "_" + nodeMap.get("codeDistVerLower").getTextContent())
        );
        Quantity<Length> ceiling = Quantities.getQuantity(
            parseDouble(nodeMap.get("valDistVerUpper").getTextContent()),
            parseLengthUnit(nodeMap.get("uomDistVerUpper").getTextContent() + "_" + nodeMap.get("codeDistVerUpper").getTextContent())
        );
        builder.adjustCeiling(ceiling).adjustFloor(floor);

        Node n = dex.extractNode(airspaceBoundaryPathExpression(aseId));

        if (n == null) {
            throw new IllegalArgumentException("Could not find airspace boundary node for aseId: " + aseId);
        }

        /*
            <Abd>
                <AbdUid mid="1573642">
                    <AseUid mid="1566481">
                        <codeType>R</codeType>
                        <codeId>LFR73</codeId>
                    </AseUid>
                </AbdUid>
                <Circle>
                    <geoLatCen>424737N</geoLatCen>
                    <geoLongCen>0025955.00E</geoLongCen>
                    <codeDatum>WGE</codeDatum>
                    <valRadius>0.5</valRadius>
                    <uomRadius>NM</uomRadius>
                    <valCrc>2DAD55CC</valCrc>
                </Circle>
            </Abd>

            OR

            <Abd>
                <AbdUid mid="13793541">
                    <AseUid mid="13793537">
                        <codeType>CTR</codeType>
                        <codeId>LFRZ1</codeId>
                    </AseUid>
                </AbdUid>
                <Avx>
                    <codeType>GRC</codeType>
                    <geoLat>472337N</geoLat>
                    <geoLong>0021921W</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <valCrc>BD5ACC9B</valCrc>
                </Avx>

                <Avx>
                    <codeType>RHL</codeType>
                    <geoLat>491800N</geoLat>
                    <geoLong>0062114E</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <valCrc>4255F480</valCrc>
                </Avx>

                <Avx>
                    <codeType>CCA</codeType>
                    <geoLat>443719.00N</geoLat>
                    <geoLong>0004706.00W</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <geoLatArc>443840.00N</geoLatArc>
                    <geoLongArc>0004700.00W</geoLongArc>
                    <valRadiusArc>2.5</valRadiusArc>
                    <uomRadiusArc>KM</uomRadiusArc>
                    <valCrc>37EE5505</valCrc>
                </Avx>

                <Avx>
                    <codeType>CWA</codeType>
                    <geoLat>460441.00N</geoLat>
                    <geoLong>0044022.00E</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <geoLatArc>454444.00N</geoLatArc>
                    <geoLongArc>0050526.00E</geoLongArc>
                    <valRadiusArc>26.5</valRadiusArc>
                    <uomRadiusArc>NM</uomRadiusArc>
                    <valCrc>2326F266</valCrc>
                </Avx>

                <Avx>
                    <GbrUid mid="1545010">
                        <txtName>BELGIUM_FRANCE</txtName>
                    </GbrUid>
                    <codeType>FNT</codeType>
                    <geoLat>493233.00N</geoLat>
                    <geoLong>0054523.00E</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <valCrc>CCCEFC33</valCrc>
                </Avx>

            </Abd>

            codeType for Avx is one of:
                <xsd:enumeration value="ABE"/> Arc By Edge
                <xsd:enumeration value="CIR"/> -DEPRECATED-
                <xsd:enumeration value="GRC"/> Great Circle
                <xsd:enumeration value="RHL"/> Rhumbline
                <xsd:enumeration value="CCA"/> Counter-Clockwise Arc
                <xsd:enumeration value="CWA"/> Clockwise Arc
                <xsd:enumeration value="FNT"/> Frontier
                <xsd:enumeration value="OTHER"/> -
            Now WTF is a rhumbline?? Ah: an arc crossing all meridians of longitude at the same angle, that is, a path with constant bearing as measured relative to true north.
            We'll ignore CIR because deprecated, and ABE because no idea what that is.
         */

        List<Node> vertices = toStream(n.getChildNodes())
            .filter(n0 -> n0.getNodeType() == Node.ELEMENT_NODE)
            .filter(n0 -> !"AbdUid".equals(n0.getNodeName()))
            .toList();

//        GeometryBuilder gb = new GeometryBuilder(DefaultGeographicCRS.WGS84);
//        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
        /*
            Use fixed precision model. It helps with some airspace unions the parts of which
            aren't neatly aligned. And it doesn't matter that much for us to be perfectly accurate here.
         */
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(10000));


        /*
            If only 1 vertex, should be a Circle, but I also have some with regular vertices,
            in case the "airspace" is just a point.
            Code should be able to handle it fine and would return point.
         */
        if (vertices.size() == 1 && "Circle".equals(vertices.get(0).getNodeName())) {

            Map<String, Node> circleNode = mapChildren(vertices.get(0));

            /*
                    <geoLatCen>424737N</geoLatCen>
                    <geoLongCen>0025955.00E</geoLongCen>
                    <codeDatum>WGE</codeDatum>
                    <valRadius>0.5</valRadius>
                    <uomRadius>NM</uomRadius>
                    <valCrc>2DAD55CC</valCrc>
             */
            Coordinate centre = new Coordinate(
                lonToDecimal(circleNode.get("geoLongCen").getTextContent()),
                latToDecimal(circleNode.get("geoLatCen").getTextContent())
            );

            Point p = gf.createPoint(centre);

            double radius = parseDouble(circleNode.get("valRadius").getTextContent());
            Unit<Length> unit = parseLengthUnit(circleNode.get("uomRadius").getTextContent());

            Polygon geom = (Polygon) bufferPoint(Quantities.getQuantity(radius, unit), DefaultGeographicCRS.WGS84, p);

            return geom;
        }
        else {
            String type = null, borderName = null;
            Coordinate previous = null;
            List<Coordinate> points = new ArrayList<>();
            Map<String, Object> userData = new HashMap<>();

            for (int ii = 0; ii <= vertices.size(); ii++) { //note that we're going one beyond in order to close the geometry
                nodeMap = mapChildren(vertices.get(ii % vertices.size()));
                Coordinate loc = new Coordinate(
                    lonToDecimal(nodeMap.get("geoLong").getTextContent()),
                    latToDecimal(nodeMap.get("geoLat").getTextContent())
                );

                if (ii == 0) {
                    points.add(loc);
                }
                else {
                    /*
                        Note that we're only extracting type *after* this block, so this one is from previous el.
                     */
                    switch (type) {
                        case "GRC", "RHL" -> {
                            // line string from previous to loc
                            points.add(loc);
                        }
                        case "FNT" -> {
                            // sme as GRC, but let's add user data
                            points.add(loc);

                            /*
                                //CAVEAT: frontier name was on the PREVIOUS element!
                                <Avx>
                                    <GbrUid mid="1545010">
                                        <txtName>BELGIUM_FRANCE</txtName>
                                    </GbrUid>
                                    <codeType>FNT</codeType>
                                    <geoLat>504148.00N</geoLat>
                                    <geoLong>0025346.00E</geoLong>
                                    <codeDatum>WGE</codeDatum>
                                    <valCrc>CFA7CC56</valCrc>
                                </Avx>
                             */
                            if (borderName != null) {
                                userData.merge("FNT", borderName, (s1, s2) -> s1 + "," + s2);
                            }
                        }
                        case "CCA", "CWA" -> {
                            /*
                                <codeType>CCA</codeType>
                                <geoLat>443719.00N</geoLat>
                                <geoLong>0004706.00W</geoLong>
                                <codeDatum>WGE</codeDatum>
                                <geoLatArc>443840.00N</geoLatArc>
                                <geoLongArc>0004700.00W</geoLongArc>
                                <valRadiusArc>2.5</valRadiusArc>
                                <uomRadiusArc>KM</uomRadiusArc>
                             */
                            var prevMap = mapChildren(vertices.get(ii - 1));
                            Coordinate arcCentre = new Coordinate(
                                lonToDecimal(prevMap.get("geoLongArc").getTextContent()),
                                latToDecimal(prevMap.get("geoLatArc").getTextContent())
                            );
                            Quantity<Length> arcRadius = Quantities.getQuantity(
                                parseDouble(prevMap.get("valRadiusArc").getTextContent()),
                                parseLengthUnit(prevMap.get("uomRadiusArc").getTextContent())
                            );

                            GeodeticCalculator gc = new GeodeticCalculator(DefaultEllipsoid.WGS84);

                            gc.setStartingGeographicPoint(arcCentre.x, arcCentre.y);

                            gc.setDestinationGeographicPoint(previous.x, previous.y);
                            double startRad = az2Rad(gc.getAzimuth());

                            gc.setDestinationGeographicPoint(loc.x, loc.y);
                            double endRad = az2Rad(gc.getAzimuth());

                            boolean cw = "CWA".equals(type);

                            while (cw && endRad < startRad) endRad += 2 * PI;
                            while (!cw && startRad < endRad) startRad += 2 * PI;

                            double arcLen = endRad - startRad;

                            int numPoints = numPointsForArcAngle(arcLen);

                            log.trace(
                                "{} [{}, {}] -> [{}, {}], centre: [{}, {}], angleStart: {}, angleEnd: {}, arcLen={}, numPoints={}",
                                type, previous.x, previous.y, loc.x, loc.y, arcCentre.x, arcCentre.y, startRad, endRad, arcLen, numPoints
                            );

                            CoordinateSequence cs = gf.getCoordinateSequenceFactory().create(numPoints + 1, 2);
                            for (int inc = 1; inc < numPoints; inc++) {
                                double theta = startRad + arcLen / numPoints * inc;

                                gc.setDirection(rad2Az(theta), arcRadius.to(SI.METRE).getValue().doubleValue());
                                Point2D control = gc.getDestinationGeographicPoint();
                                points.add(new Coordinate(control.getX(), control.getY()));
                            }

                            points.add(loc);
                        }
                        default -> {
                            throw new UnsupportedOperationException("Cannot handle vertex: " + type);
                        }
                    }
                }

                previous = loc;
                type = nodeMap.get("codeType").getTextContent();

                if ("FNT".equals(type)) {
                    borderName = Optional.ofNullable(nodeMap.get("GbrUid"))
                                         .map(NodeUtils::mapChildren)
                                         .map(m -> m.get("txtName"))
                                         .map(Node::getTextContent)
                                         .orElse(null);
                }
                else {
                    borderName = null;
                }
            }

            Geometry geom;

            if(points.isEmpty()){
                throw new IllegalStateException("No coordinates");
            }
            else if(points.size() < 3){
                // if no more than 2 points, it's just a point
                geom = gf.createPoint(points.get(0));
            }
            else {
                geom = gf.createPolygon(points.toArray(Coordinate[]::new));
            }

            geom.setUserData(userData);

            return geom;
        }
    }

    private void copyFrontiers(Collection<? extends Geometry> from, Geometry to) {
        String fnts = from.stream()
                          .map(Geometry::getUserData)
                          .filter(Map.class::isInstance)
                          .map(o -> (String) ((Map<?, ?>) o).get("FNT"))
                          .filter(Objects::nonNull)
                          .distinct()
                          .collect(Collectors.joining(","));

        if (StringUtils.hasText(fnts)) {
            to.setUserData(Map.of("FNT", fnts));
        }
    }

    private String airspacePathExpression(String aseId) {
        // language=XPath
        return "/AIXM-Snapshot/Ase[AseUid/@mid='" + aseId + "']";
    }

    private String airspaceDerivedGeometryPathExpression(String aseId) {
        // language=XPath
        return "/AIXM-Snapshot/Adg[AdgUid/AseUid/@mid='" + aseId + "']";
    }

    @Language("XPath")
    private String airspaceBoundaryPathExpression(String aseId, String... moreAseIds) {
        StringBuilder sb = new StringBuilder("/AIXM-Snapshot/Abd[AbdUid/AseUid/@mid='" + aseId + "'");
        for (String otherId : moreAseIds) {
            sb.append(" or AbdUid/AseUid/@mid='").append(otherId).append("'");
        }
        sb.append("]");

        return sb.toString();
    }

    private static int numPointsForArcAngle(double angle) {
        angle = abs(angle) / PI;

        if (angle < .5) {
            return 8;
        }
        else if (angle < 1) {
            return 16;
        }
        else if (angle < 1.5) {
            return 24;
        }
        else {
            return 32;
        }
    }

    // https://stackoverflow.com/questions/44249945/how-to-use-geometricshapefactory-in-geotools-to-create-a-circle-on-map#
    public static Geometry bufferPoint(Quantity<Length> distance, CoordinateReferenceSystem origCRS, Geometry geom) {
        Geometry pGeom = geom;
        MathTransform toTransform, fromTransform = null;
        // reproject the geometry to a local projection
        Unit<Length> unit = distance.getUnit();
        if (!(origCRS instanceof ProjectedCRS)) {

            double x = geom.getCoordinate().x;
            double y = geom.getCoordinate().y;

            String code = "AUTO:42001," + x + "," + y;
            // System.out.println(code);
            CoordinateReferenceSystem auto;
            try {
                auto = CRS.decode(code);
                toTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, auto);
                fromTransform = CRS.findMathTransform(auto, DefaultGeographicCRS.WGS84);
                pGeom = JTS.transform(geom, toTransform);
                unit = SI.METRE;
            }
            catch (MismatchedDimensionException | TransformException | FactoryException e) {
                log.error("", e);
            }

        }
        else {
            unit = (Unit<Length>) origCRS.getCoordinateSystem().getAxis(0).getUnit();
        }

        // buffer
        Geometry out = pGeom.buffer(distance.to(unit).getValue().doubleValue());
        Geometry retGeom = out;
        // reproject the geometry to the original projection
        if (!(origCRS instanceof ProjectedCRS)) {
            try {
                retGeom = JTS.transform(out, fromTransform);

            }
            catch (MismatchedDimensionException | TransformException e) {
                log.error("", e);
            }
        }
        return retGeom;
    }

}
