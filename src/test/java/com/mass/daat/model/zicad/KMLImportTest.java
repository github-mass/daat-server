package com.mass.daat.model.zicad;

import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xsd.Parser;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class KMLImportTest {

    @Test
    void testGeoToolsKmlImport()
        throws IOException, ParserConfigurationException, SAXException
    {
        Path p = Path.of("./data/zicad/Arrete_ZICAD_01-2023.kml");

        Parser parser = new Parser(new KMLConfiguration()); //note use of class from v22 package

        try(InputStream is = Files.newInputStream(p)){
            Object o = parser.parse(is);

            System.out.println(o);
        }
    }

    @Test
    void testZicadParser()
        throws IOException
    {
        Path p = Path.of("./data/zicad/Arrete_ZICAD_01-2023.kml");

        ZicadKmlParser parser = new ZicadKmlParser();

        try(InputStream is = Files.newInputStream(p)){
            parser.parse(is).forEach(System.out::println);
        }
    }



}
