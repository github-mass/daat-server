package com.mass.flightplan.geo;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mass.flightplan.ApplicationConfiguration;
import com.mass.flightplan.db.AixmDbImporter;
import com.mass.flightplan.db.FlightplanDbConfig;
import com.mass.flightplan.model.Dataset;
import com.mass.flightplan.model.ModelUtils;
import com.mass.flightplan.model.NodeUtils;
import com.mass.flightplan.model.aixm.Airspace;
import com.mass.flightplan.model.aixm.AirspaceExtractor;
import com.mass.flightplan.model.aixm.AirspaceType;
import com.mass.flightplan.model.aixm.AixmImporter;
import com.mass.flightplan.util.DouglasPeuckerGeometryFixer;
import com.mass.flightplan.util.GeometryConverter;
import com.mass.flightplan.util.GeometryFixer;
import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Node;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@SpringBootTest(classes = {ApplicationConfiguration.class, FlightplanDbConfig.class, PrnTest.Cfg.class})
@ActiveProfiles("aixm-test")
@Slf4j
@Disabled("Manual tests")
public class PrnTest {

    private XPathDocumentExtractor aixm_dex, sia_dex;

    @TestConfiguration
    static class Cfg {
        @Bean
        AltitudeService altitudeService() {
            return coordinate -> Quantities.getQuantity(69, SI.METRE);
        }
    }

    @Autowired
    AixmDbImporter dbImporter;

    @Autowired
    GeometryConverter geometryConverter;

    @BeforeEach
    public void loadDocument()
        throws IOException
    {
        Path aixmlFile = Path.of("data/aixm/export_xml_bd_sia_2023-07-13-b5/AIXM4.5_all_FR_OM_2023-07-13.xml");
        Path siaFile = Path.of("data/aixm/export_xml_bd_sia_2023-07-13-b5/XML_SIA_2023-07-13.xml");
        aixm_dex = XPathDocumentExtractor.from(aixmlFile).namespaceAware(false).build();
        sia_dex = XPathDocumentExtractor.from(siaFile).namespaceAware(false).build();
    }

    List<Airspace> extractPrns()
        throws XPathExpressionException, TransformException
    {
        var list = AirspaceExtractor.forType(AirspaceType.NATURAL_RESERVE).extract(aixm_dex);

        list = list.stream().map(
            as -> {
                try {
                    Node n = sia_dex.extractNode("/SiaExport/Situation/PartieS/Partie[contains(@lk, '[PRN %s]')]".formatted(as.code()));
                    return Optional.ofNullable(n)
                                   .map(NodeUtils::mapChildren)
                                   .map(m -> m.get("NomUsuel"))
                                   .map(Node::getTextContent)
                                   .map(s -> as.toBuilder().name(s).build())
                                   .orElse(as);
                }
                catch (XPathExpressionException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        ).toList();

        return list;
    }

    @Test
    void listPrns()
        throws XPathExpressionException, TransformException
    {
        var list = extractPrns();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6)); // num of precision digits, veery important!

        list.stream().sorted(Comparator.comparingInt(as -> Integer.parseInt(as.code()))).forEach(prn -> {
            System.out.println(prn);
            try {
                System.out.println(om.writeValueAsString(prn.geometry()));
            }
            catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            System.out.println();
        });
    }

    @Test
    void importPrns()
        throws XPathExpressionException, TransformException, IOException
    {
        var list = extractPrns();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6)); // num of precision digits, veery important!

        list.stream().sorted(Comparator.comparingInt(as -> Integer.parseInt(as.code()))).forEach(prn -> {
            System.out.println(prn);
            try {
                System.out.println(om.writeValueAsString(prn.geometry()));
            }
            catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            System.out.println();
        });

        Dataset ds = Dataset.builder().datasetType("TEST").sourceName("PrnTest").build();
        AixmImporter.Result res = AixmImporter.Result.builder()
                                                     .dataset(ds).airspaces(list)
                                                     .build();

        var dse = dbImporter.importResult(res);

        System.out.println("All PRNs inserted successfully.");
        dbImporter.purge(dse);
    }

    @Test
    void testFixAmana()
        throws XPathExpressionException, TransformException, IOException
    {
        var list = extractPrns();

        Airspace amana = list.stream().filter(as -> Objects.equals(as.name(), "AMANA")).findFirst().orElseThrow();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6));

        log.info("Geometry before fixing: {}", om.writeValueAsString(amana.geometry()));

        GeometryFixer gf = new DouglasPeuckerGeometryFixer();
        amana = amana.toBuilder().geometry(gf.fix(amana.geometry())).build();

        log.info("Geometry after fixing: {}", om.writeValueAsString(amana.geometry()));
    }

    @Test
    void testFixKahouanne()
        throws XPathExpressionException, TransformException, IOException
    {
        var list = extractPrns();

        Airspace kahouanne = list.stream().filter(as -> Objects.equals(as.name(), "ILETS KAHOUANNE")).findFirst().orElseThrow();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6));

        log.info("Geometry before fixing: {}", om.writeValueAsString(kahouanne.geometry()));

        GeometryFixer gf = new DouglasPeuckerGeometryFixer();
        kahouanne = kahouanne.toBuilder().geometry(gf.fix(kahouanne.geometry())).build();

        log.info("Geometry after fixing: {}", om.writeValueAsString(kahouanne.geometry()));
    }

    public static void main(String[] args) {
        String coords = "05°45'00\"N , 053°55'00\"W - 05°45'30\"N , 053°53'15\"W - 05°34'21\"N , 053°27'58\"W - 05°33'40\"N , 053°28'04\"W - 05°33'00\"N , 053°28'15\"W - 05°33'01\"N , 053°28'12\"W - 05°33'00\"N , 053°30'00\"W - 05°34'55\"N , 053°36'12\"W - 05°35'34\"N , 053°37'14\"W - 05°37'37\"N , 053°36'29\"W - 05°40'00\"N , 053°47'00\"W - 05°44'30\"N , 053°51'34\"W - 05°44'15\"N , 053°58'00\"W - 05°44'55\"N , 054°04'00\"W - 05°45'00\"N , 053°55'00\"W";

        Stream.of(coords)
              .flatMap(s -> Arrays.stream(s.split("-")))
              .map(s -> s.split(","))
              .map(ss -> new double[]{ModelUtils.lonToDecimal(ss[1].trim()), ModelUtils.latToDecimal(ss[0].trim())})
//            .map(dd -> new Coordinate(dd[0], dd[1]))
              .forEach(dd -> System.out.printf("%f, %f%n", dd[0], dd[1]));

    }
}
