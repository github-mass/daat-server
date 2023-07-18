package com.mass.flightplan.geo;

import com.mass.flightplan.aixm.AixmUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String xmlSource = "./aixm/export_xml_bd_sia_2023-07-13-b5/AIXM4.5_all_FR_OM_2023-07-13.xml";
        String sheet = "/abdTransform.xslt";

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer(new StreamSource(getClass().getResourceAsStream(sheet)));
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter sw = new StringWriter();
        trans.transform(new StreamSource(new FileSystemResource(xmlSource).getInputStream()), new StreamResult(sw));

        String[] lines = sw.toString().split("\r?\n");

        Pattern p = Pattern.compile("(\\w+)::([^,]+),([^,]+)");
        for(String line: lines){
            Matcher m = p.matcher(line);
            boolean b = m.matches();
            assert b;

            double lat = AixmUtils.latToDecimal(m.group(2));
            double lon = AixmUtils.lonToDecimal(m.group(3));

            System.out.printf("%s, %s%n", lon, lat);
        }
    }
}
