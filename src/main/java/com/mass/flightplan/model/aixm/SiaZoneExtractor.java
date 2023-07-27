package com.mass.flightplan.model.aixm;

import com.mass.flightplan.model.ModelUtils;
import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.xml.xpath.XPathExpressionException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.mass.flightplan.model.NodeUtils.mapAttributes;
import static com.mass.flightplan.model.NodeUtils.mapChildren;
import static java.util.function.Predicate.not;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class SiaZoneExtractor
    implements AixmExtractor<List<Airspace>>
{
    public static SiaZoneExtractor forAirspaceType(@NonNull AirspaceType type){
        return new SiaZoneExtractor("TypeEspace='%s'".formatted(type.siaTypeEspace()), type);
    }

    @Language("XPath")
    private final String espacePredicate;
    private final AirspaceType resultAirspaceType;

    /*
        We're parsing from the (French) SIA XML dump here.
        Because the English one doesn't have names.
        And we can't lookup the names because the PRNs have duplicate IDs across continental France and overseas territories. :-\

        Here's the format:
             Espace -> Partie -> Volume

            <Espace pk="303232" lk="[NT][PRN 010]">
                <Territoire pk="1100" lk="[NT]"/>
                <TypeEspace>PRN</TypeEspace>   <-- we get all EspaceS by checking this
                <Nom>010</Nom>                  <-- we want this
            </Espace>

            <Partie pk="301172" lk="[NT][PRN 010][.]">
                <Espace pk="303232" lk="[NT][PRN 010]"/>   <-- we look up the PartieS based on Espace@pk
                <NomPartie>.</NomPartie>
                <NumeroPartie>10</NumeroPartie>
                <NomUsuel>TAHUA-MARAE TAPUTAPUATEA I OPOA</NomUsuel>    <-- we want this
                <Contour>1000010,Cloture=305032,-16.835833 -151.359167,cir(-16.835833 -151.359167:0.8:NM:0m:=)</Contour>
                <Geometrie>-16.822445,-151.359167    <-- we want this; be careful might have both points and polygons
                    -16.822512,-151.360555
                    -16.822712,-151.361929
                    -16.823044,-151.363275
                    ...
                    -16.822445,-151.359167</Geometrie>
            </Partie>


            <Volume pk="309183" lk="[NT][PRN 010][.][10]">
                <Partie pk="301172" lk="[NT][PRN 010][.]"/>  <--- we look up the VolumeS based on Partie@pk
                <Sequence>10</Sequence>
                <PlafondRefUnite>ft ASFC</PlafondRefUnite>   <-- we want this
                <Plafond>2000</Plafond>                      <-- and this, giving us the min height at which zone must be overflown
                <PlancherRefUnite>SFC</PlancherRefUnite>
                <Plancher>0</Plancher>
                <HorCode>HX</HorCode>                        <-- we want this too
                <Activite>Survol / Overflight</Activite>     <-- we'd like this I think (although seems always the same) but not sure where to put it
                <Remarque>FIR TAHITI#ILES SOUS LE VENT#Cette restriction ne s'applique pas :#- aux titulaires d'une autorisation spéciale du gestionnaire de la Zone de Site Protégée (ZSP) du "Paysage culturel Taputapuatea", ou, à défaut, de la Direction de la Culture et du Patrimoine (DCP).#- aux ACFT effectuant des opérations de secours et sauvetage.</Remarque>
                <-- we want the Remarque too
            </Volume>

     */

    @Override
    public List<Airspace> extract(@NotNull XPathDocumentExtractor dex)
        throws Exception
    {
        return extractEspaces(dex)
            .map(partieOp(dex))
            .map(volumeOp(dex))
            .map(p -> {
                try {
                    return p.toAirspace();
                }
                catch (Exception x) {
                    log.error("Could not convert prototype to airspace: {}", p, x);
                    throw x;
                }
            })
            .toList()
        ;
    }

    Stream<Prototype> extractEspaces(@NotNull XPathDocumentExtractor dex)
        throws XPathExpressionException
    {
        @Language("XPath")
        String xpath = "/SiaExport/Situation/EspaceS/Espace[" + espacePredicate + "]";

        return dex.extractNodes(xpath)
            .stream()
            .map(n -> {
                var prot = new Prototype();
                prot.idEspace(mapAttributes(n).get("pk"));
                prot.id(mapChildren(n).get("Nom").getTextContent());
                prot.code(prot.id());
                return prot;
            });
    }

    UnaryOperator<Prototype> partieOp(@NotNull XPathDocumentExtractor dex){
        @Language("XPath")
        String xpathtemplate = "/SiaExport/Situation/PartieS/Partie[Espace/@pk='%s']";

        return prot -> {
            try {
                Node partie = dex.extractNode(xpathtemplate.formatted(prot.idEspace()));
                Optional.ofNullable(partie).orElseThrow();

                prot.idPartie(mapAttributes(partie).get("pk"));

                var nc = mapChildren(partie);
                prot.name(nc.get("NomUsuel").getTextContent());
                String geom = nc.get("Geometrie").getTextContent();

                var gl = Arrays.stream(geom.split("\r?\n"))
                               .filter(not(String::isEmpty))
                               .map(s -> s.split(","))
                               .map(ss -> new Point(Double.parseDouble(ss[1]), Double.parseDouble(ss[0]))) //note, we're inverting coordinates here
                               .toList();
                prot.geometryPoints(gl);
            }
            catch (Exception x) {
                log.error("Partie extraction failed for {}", prot, x);
            }

            return prot;
        };
    }

    UnaryOperator<Prototype> volumeOp(@NotNull XPathDocumentExtractor dex){
        @Language("XPath")
        String xpathtemplate = "/SiaExport/Situation/VolumeS/Volume[Partie/@pk='%s']";

        return prot -> {
            try {
                Node volume = dex.extractNode(xpathtemplate.formatted(prot.idPartie()));
                Optional.ofNullable(volume).orElseThrow();

                prot.idVolume(mapAttributes(volume).get("pk"));

                var nc = mapChildren(volume);

                prot.ceilingAmount(nc.get("Plafond").getTextContent());
                prot.ceilingUnit(nc.get("PlafondRefUnite").getTextContent());
                prot.floorAmount(nc.get("Plancher").getTextContent());
                prot.floorUnit(nc.get("PlancherRefUnite").getTextContent());

                Optional.ofNullable(nc.get("HorCode")).map(Node::getTextContent).ifPresent(prot::activationType);
                Optional.ofNullable(nc.get("HorTxt")).map(Node::getTextContent).ifPresent(prot::activationRemarks);
                Optional.ofNullable(nc.get("Remarque")).map(Node::getTextContent).ifPresent(prot::remarks);
            }
            catch (Exception x) {
                log.error("Partie extraction failed for {}", prot, x);
            }

            return prot;
        };
    }

    @Data
    @ToString
    class Prototype {
        String idEspace;
        String idPartie;
        String idVolume;
        String id;
        String code;
        String name;
        String remarks;
        String activationType;
        String activationRemarks;

        String ceilingUnit, ceilingAmount;
        String floorUnit, floorAmount;

        List<Point> geometryPoints;


        public Airspace toAirspace() {
            Quantity<Length> ceiling = toQuantity(ceilingAmount, ceilingUnit), floor = toQuantity(floorAmount, floorUnit);
            Geometry geom = toGeometry(geometryPoints);

            return Airspace.builder()
                           .id(id)
                           .code(code)
                           .name(name)
                           .remarks(remarks)
                           .activationType(activationType)
                           .activationRemarks(activationRemarks)
                           .type(resultAirspaceType.code())
                           .adjustCeiling(ceiling)
                           .adjustFloor(floor)
                           .geometry(geom)
                           .build();
        }

        static Quantity<Length> toQuantity(String amount, String unit) {
            if ("SFC".equals(unit)) {
                return Quantities.getQuantity(0, ModelUtils.FEET_HEIGHT);
            }
            else if ("UNL".equals(unit)) {
                return Quantities.getQuantity(999, ModelUtils.FLIGHT_LEVEL);
            }
            else {
                return Quantities.getQuantity(
                    Double.parseDouble(amount), ModelUtils.parseLengthUnit(unit)
                );
            }
        }

        static Geometry toGeometry(List<Point> points) {
            GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
            if (points.size() == 1) {
                return gf.createPoint(new Coordinate(points.get(0).getX(), points.get(0).getY()));
            }
            else if(points.size() == 2){
                return gf.createLineString(new Coordinate[]{
                    new Coordinate(points.get(0).getX(), points.get(0).getY()),
                    new Coordinate(points.get(1).getX(), points.get(1).getY())
                });
            }
            else {
                Coordinate[] cs = points.stream()
                                        .map(p -> new Coordinate(p.getX(), p.getY()))
                                        .toArray(Coordinate[]::new);

                return gf.createPolygon(cs);
            }
        }
    }
}
