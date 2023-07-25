package com.mass.flightplan.model.aixm;

import com.mass.flightplan.geo.AltitudeService;
import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.geotools.measure.Units;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.operation.TransformException;
import tech.units.indriya.quantity.Quantities;

import javax.xml.xpath.XPathExpressionException;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ExtractorsTest {

    AltitudeService altitudeService = x -> Quantities.getQuantity(69, Units.FOOT);

    private XPathDocumentExtractor dex;

    @BeforeEach
    public void loadDocument()
        throws IOException
    {
        Path aixmlFile = Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/AIXM4.5_all_FR_OM_2023-04-20.xml");
        dex = XPathDocumentExtractor.from(aixmlFile).namespaceAware(false).build();
    }

    @Test
    public void testHeliportsExtraction()
        throws ExecutionException
    {
        List<Heliport> heliports = new HeliportsExtractor().extract(dex);

        log.info("Found {} heliport entries", heliports.size());
        heliports.forEach(System.out::println);
        log.info("Found {} heliport entries", heliports.size());
    }

    @Test
    public void testAerodromesExtraction()
        throws ExecutionException
    {
        List<Aerodrome> aerodromes = new AerodromesExtractor(altitudeService).extract(dex);

        log.info("Found {} aerodromes entries", aerodromes.size());
        aerodromes.forEach(System.out::println);
        log.info("Found {} aerodromes entries", aerodromes.size());
    }

    @Test
    public void test_R_AirspaceExtraction()
        throws XPathExpressionException, TransformException
    {
        var list = AirspaceExtractor.forType(AirspaceType.RESTRICTED).extract(dex);
        log.info("Found {} R airspace entries", list.size());
        list.forEach(System.out::println);
        log.info("Found {} R airspace entries", list.size());
    }

    @Test
    public void test_D_AirspaceExtraction()
        throws XPathExpressionException, TransformException
    {
        var list = AirspaceExtractor.forType(AirspaceType.DANGEROUS).extract(dex);
        log.info("Found {} D airspace entries", list.size());
        list.forEach(System.out::println);
        log.info("Found {} D airspace entries", list.size());
    }

    @Test
    public void test_P_AirspaceExtraction()
        throws XPathExpressionException, TransformException
    {
        var list = AirspaceExtractor.forType(AirspaceType.PROHIBITED).extract(dex);
        log.info("Found {} P airspace entries", list.size());
        list.forEach(System.out::println);
        log.info("Found {} P airspace entries", list.size());

    }

    @Test
    public void testHelipadAdminExtraction()
        throws IOException, XPathExpressionException
    {
        XPathDocumentExtractor dex = XPathDocumentExtractor.from(Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/XML_SIA_2023-04-20.xml")).build();

        var map = new SiaContactExtractor().extract(dex);

        assertThat(map).isNotNull();
        assertThat(map).isNotEmpty();

        System.out.println(map);
    }

    @Test
    @Disabled
    public void manualAerodromeTest()
        throws ExecutionException
    {
        List<Aerodrome> list = new AerodromesExtractor(altitudeService) {
            @Override
            protected @NotNull String xpathExpression() {
                return "/AIXM-Snapshot/Ahp[AhpUid/codeId='LFPG']";
            }
        }.extract(dex);

        list.forEach(System.out::println);

        Arrays.stream(list.get(0).ctr().geometry().getCoordinates())
              .forEach(c -> System.out.printf("%s, %s%n", c.x, c.y));
    }

    @Test
    @Disabled
    void manualAirspaceTest()
        throws Exception
    {
//        List<Airspace> list = new AirspaceExtractor(
//            "/AIXM-Snapshot/Ase[AseUid/codeType='CTR' and AseUid/codeId='BORA BORA MOTU MUTE']"
//        ).extract(dex);
//        List<Airspace> list = new AirspaceExtractor(
//            "/AIXM-Snapshot/Ase[AseUid/codeType='CTR' and txtName='STRASBOURG']"
//        ).extract(dex);
        List<Airspace> list = List.of(
//            AirspaceExtractor.forAirspaceById("1560431").extract(dex),
//            AirspaceExtractor.forAirspaceById("1563789").extract(dex),
            AirspaceExtractor.forAirspaceById("1562465").extract(dex)
        );

        Assertions.assertThat(list).isNotEmpty();

        list.forEach(as -> {
            System.out.println(as);
            System.out.println(as.geometry().getUserData());
            outputGeom(as.geometry());
            System.out.println("---------------------------");
        });

        Geometry g;

//        g = list.stream().map(Airspace::geometry).reduce(Geometry::union).get();
//            ;
//
//        g = JTSFactoryFinder
//            .getGeometryFactory()
//            .buildGeometry(list.stream().map(Airspace::geometry).toList()).union();

//        g = OverlayNGRobust.union(list.stream().map(Airspace::geometry).toList());

//        outputGeom(g);
//        clipGeom(g);
    }

    static void outputGeom(Geometry g) {
        System.out.println(g.getClass().getSimpleName());
        Arrays.stream(g.getCoordinates())
              .forEach(c -> System.out.printf("%s, %s%n", c.x, c.y));
    }

    static void clipGeom(Geometry g) {
        String s = Arrays.stream(g.getCoordinates())
                         .map(c -> "%s, %s".formatted(c.x, c.y))
                         .collect(Collectors.joining(System.getProperty("line.separator")));
        Transferable t = new StringSelection(s);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, (clipboard, contents) -> {
        });
        System.err.println("Geometry's coordinates copied to clipboard");
    }
}
