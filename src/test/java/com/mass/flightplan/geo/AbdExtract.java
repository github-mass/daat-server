package com.mass.flightplan.geo;

import com.mass.flightplan.model.ModelUtils;
import com.mass.flightplan.model.aixm.AirspaceGeometryBuilder;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.geojson.GeoJSONWriter;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.springframework.core.io.FileSystemResource;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mass.flightplan.model.ModelUtils.*;
import static java.lang.Double.parseDouble;

public class AbdExtract {

    /*
        Manual test for extracting coordinates from AirspaceBoundaryDefinition elements.
        Airspace boundary ID hardcoded in XSLT file.
     */
    @Test
    @Disabled
    void extractAbd()
        throws TransformerException, IOException
    {
        String xmlSource = "./data/aixm/export_xml_bd_sia_2023-07-13-b5/AIXM4.5_all_FR_OM_2023-07-13.xml";
        String sheet = "/abdTransform.xslt";

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer(new StreamSource(getClass().getResourceAsStream(sheet)));
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter sw = new StringWriter();
        trans.transform(new StreamSource(new FileSystemResource(xmlSource).getInputStream()), new StreamResult(sw));

        String[] lines = sw.toString().split("\r?\n");

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(10000));
        List<Coordinate> points = new ArrayList<>();

        Pattern p = Pattern.compile("(\\w+)::([^,]+),([^,]+)");
        for(String line: lines){
            Matcher m = p.matcher(line);
            boolean b = m.matches();
            assert b;

            double lat = ModelUtils.latToDecimal(m.group(2));
            double lon = ModelUtils.lonToDecimal(m.group(3));

            points.add(new Coordinate(lon, lat));
        }
        points.add(points.get(0));

        Geometry pog = gf.createPolygon(points.toArray(Coordinate[]::new));

        pog = DouglasPeuckerSimplifier.simplify(pog, 0.0001);

//        pog.buffer(0);

        for(Coordinate c: pog.getCoordinates()){
            System.out.printf("%s, %s%n", c.getX(), c.getY());
        }

        String json = GeoJSONWriter.toGeoJSON(pog);
        System.out.println(json);

//        com.esri.core.geometry.Polygon pog = new com.esri.core.geometry.Polygon();
//        pog.startPath(points.get(0).getX(), points.get(0).getY());
//        for(int ii = 1; ii < points.size(); ii++){
//            pog.lineTo(points.get(ii).getX(), points.get(ii).getY());
//        }
//
//        boolean simple = OperatorSimplify.local().isSimpleAsFeature(pog, null, null);
//        System.err.println("Is simple? " + simple);
//        if(!simple){
//            pog = (com.esri.core.geometry.Polygon) OperatorSimplify.local().execute(pog, null, false, null);
//        }
//
//        String geojson = OperatorExportToGeoJson.local().execute(pog);
//        System.out.println(geojson);
    }

    @Test
    @Disabled
    void testIsSimple(){
        String json = "{\n" +
            "        \"type\" : \"Polygon\",\n" +
            "        \"coordinates\" : [ \n" +
            "            [ \n" +
            "                [ \n" +
            "                    2.69666666666667, \n" +
            "                    49.11\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.84138888888889, \n" +
            "                    48.9938888888889\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.83833333333333, \n" +
            "                    48.96\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.83001686907019, \n" +
            "                    48.9601534072213\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.82178937893484, \n" +
            "                    48.959283541668\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.81389091896348, \n" +
            "                    48.9575354002199\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.80653622080818, \n" +
            "                    48.9549565180575\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.7999251509597, \n" +
            "                    48.9516170107807\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.79423725921547, \n" +
            "                    48.947607658301\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.78962689302831, \n" +
            "                    48.943037425742\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.78611111111111, \n" +
            "                    48.9380555555556\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.64666666666667, \n" +
            "                    48.9166666666667\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.64135038891131, \n" +
            "                    48.9205365940184\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.63526728452163, \n" +
            "                    48.9238697303033\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.62846868033614, \n" +
            "                    48.9265361034479\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.62112132885071, \n" +
            "                    48.9284702757376\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.61340552212843, \n" +
            "                    48.9296247725778\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.6055106474912, \n" +
            "                    48.9299712540616\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.59763051372652, \n" +
            "                    48.9295012145896\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.58995856561707, \n" +
            "                    48.9282261928821\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.58268310766726, \n" +
            "                    48.9261774870797\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.57598265764798, \n" +
            "                    48.9234053821284\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.57002154701781, \n" +
            "                    48.9199779089455\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56494587853111, \n" +
            "                    48.9159791666321\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.5608799416465, \n" +
            "                    48.911507249914\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.55792317403387, \n" +
            "                    48.9066718337713\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.55614774295659, \n" +
            "                    48.9015914756048\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.55527777777778, \n" +
            "                    48.8963888888889\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.55944444444444, \n" +
            "                    48.8216666666667\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56012872295706, \n" +
            "                    48.8172660221543\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56123207587619, \n" +
            "                    48.8129167253688\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56302554180255, \n" +
            "                    48.8086688728939\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56548884146126, \n" +
            "                    48.804570090224\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.56859420442425, \n" +
            "                    48.8006663202435\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.57230668666484, \n" +
            "                    48.7970013096537\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.57658456726001, \n" +
            "                    48.7936161204101\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.58138888888889, \n" +
            "                    48.7905555555556\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.61194444444444, \n" +
            "                    48.8005555555556\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.61888888888889, \n" +
            "                    48.6986111111111\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.48888888888889, \n" +
            "                    48.6188888888889\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.37083333333333, \n" +
            "                    48.5713888888889\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.26805555555556, \n" +
            "                    48.5708333333333\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.12138888888889, \n" +
            "                    48.6094444444444\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.11583333333333, \n" +
            "                    48.6611111111111\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.16583333333333, \n" +
            "                    48.7083333333333\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.15972222222222, \n" +
            "                    48.7291666666667\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.15166666666667, \n" +
            "                    48.7555555555556\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.14916666666667, \n" +
            "                    48.7647222222222\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.11722222222222, \n" +
            "                    48.7583333333333\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.08055555555556, \n" +
            "                    48.7616666666667\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.08055555555556, \n" +
            "                    48.7869444444444\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.08944444444444, \n" +
            "                    48.7972222222222\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.10055555555556, \n" +
            "                    48.8097222222222\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.07777777777778, \n" +
            "                    48.9111111111111\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.32111111111111, \n" +
            "                    49.1036111111111\n" +
            "                ], \n" +
            "                [ \n" +
            "                    2.69666666666667, \n" +
            "                    49.11\n" +
            "                ]\n" +
            "            ]\n" +
            "        ]\n" +
            "    }";

//        MapGeometry geom = OperatorImportFromGeoJson.local().execute(GeoJsonImportFlags.geoJsonImportDefaults, Geometry.Type.Polygon, json, null);
//
//        boolean isSimple = OperatorSimplify.local().isSimpleAsFeature(geom.getGeometry(), null, null);
//
//        System.out.println("Is simple? " + isSimple);

        Geometry geom = GeoJSONReader.parseGeometry(json);

        geom = DouglasPeuckerSimplifier.simplify(geom, 0);

        json = GeoJSONWriter.toGeoJSON(geom);
        System.out.println(json);
    }

    @Test
    public void testCircle(){
        /*
            <Circle>
                       <geoLatCen>142814.00N</geoLatCen>
                       <geoLongCen>0610302.00W</geoLongCen>
                       <codeDatum>WGE</codeDatum>
                       <valRadius>1.2</valRadius>
                       <uomRadius>NM</uomRadius>
                   </Circle>
       */

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(10000));

        Coordinate centre = new Coordinate(
            lonToDecimal("0610302.00W"),
            latToDecimal("142814.00N")
        );

        Point p = gf.createPoint(centre);

        double radius = parseDouble("1.2");
        Unit<Length> unit = parseLengthUnit("NM");

        Polygon geom = (Polygon) AirspaceGeometryBuilder.bufferPoint(Quantities.getQuantity(radius, unit), DefaultGeographicCRS.WGS84, p);

        var json = GeoJSONWriter.toGeoJSON(geom);
        System.out.println(json);
    }

}
