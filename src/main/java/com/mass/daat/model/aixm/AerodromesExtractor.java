package com.mass.daat.model.aixm;

import com.mass.daat.geo.AltitudeService;
import com.mass.daat.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.xml.xpath.XPathExpressionException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.mass.daat.model.ModelUtils.*;
import static com.mass.daat.model.NodeUtils.mapAttributes;
import static com.mass.daat.model.NodeUtils.mapChildren;
import static java.lang.Double.parseDouble;

@Slf4j
@RequiredArgsConstructor
public class AerodromesExtractor
    implements AixmExtractor<List<Aerodrome>>
{
    private final AltitudeService altitudeService;

    @NonNull
    @Language("XPath")
    protected String xpathExpression(){
        return "/AIXM-Snapshot/Ahp[codeType='AD']";
    }

    @Override
    @NonNull
    public List<Aerodrome> extract(@NonNull XPathDocumentExtractor dex)
        throws ExecutionException
    {
        try {
            List<Node> nl = dex.extractNodes(xpathExpression());

            List<Aerodrome> ret = new ArrayList<>();
            for(Node node: nl){
                try {
                    ret.add(extractAerodrome(node, dex));
                }
                catch (Exception x) {
                    throw new ExecutionException(
                        "Extraction failed for aerodrome %s".formatted(mapAttributes(mapChildren(node).get("AhpUid")).get("mid")),
                        x
                    );
                }
            }

            return ret;
        }
        catch (XPathExpressionException e) {
            //shouldn't happen
            log.error("Failed to extract aerodromes", e);
            return List.of();
        }
    }

    @SneakyThrows
    private Aerodrome extractAerodrome(Node ahp, XPathDocumentExtractor dex) {
        /*
            <Ahp>
                <AhpUid mid="1521690">
                    <codeId>LFNL</codeId>
                </AhpUid>
                <OrgUid mid="1520800">
                    <txtName>FRANCE</txtName>
                </OrgUid>
                <txtName>SAINT MARTIN DE LONDRES</txtName>
                <codeIcao>LFNL</codeIcao>
                <codeType>AD</codeType>
                <geoLat>434801.00N</geoLat>
                <geoLong>0034654.00E</geoLong>
                <codeDatum>WGE</codeDatum>
                <valElev>602</valElev>
                <valGeoidUndulation>165</valGeoidUndulation>
                <uomDistVer>FT</uomDistVer>
                <valMagVar>1.58</valMagVar>
                <dateMagVar>2020</dateMagVar>

                <txtNameCitySer>NIORT</txtNameCitySer> // opt
                <txtDescrSite>4 km ESE Niort</txtDescrSite> // opt
                <txtNameAdmin>MAIRIE DE LA VILLE DE NIORT</txtNameAdmin> //opt
            </Ahp>
         */

        var builder = Aerodrome.builder();
        var nm = mapChildren(ahp);

        String adId = mapAttributes(nm.get("AhpUid")).get("mid");
        String code = Optional.ofNullable(nm.get("codeIcao"))
                              .map(Node::getTextContent)
                              .orElse(mapChildren(nm.get("AhpUid")).get("codeId").getTextContent());

        builder.id(adId);
        builder.code(code);

        builder.name(nm.get("txtName").getTextContent());
        Point loc;
        builder.coordinates(loc = new Point(
            lonToDecimal(nm.get("geoLong").getTextContent()),
            latToDecimal(nm.get("geoLat").getTextContent())
        ));

        if(nm.containsKey("valElev")) {
            builder.elevation(Quantities.getQuantity(
                parseDouble(nm.get("valElev").getTextContent()),
                parseLengthUnit(nm.get("uomDistVer").getTextContent())
            ));
        }
        else {
            log.atInfo().addKeyValue("adId", adId).addKeyValue("code", code).log("No elevation specified, will attempt resolution");
            try {
                Quantity<Length> alt = altitudeService.getAltitudeAt(loc);
                builder.elevation(alt);
            }
            catch (Exception x) {
                log.error("Altitude lookup for coordinates {} failed", loc, x);
            }
        }

        if (nm.containsKey("valGeoidUndulation")) {
            builder.geoidUndulation(Quantities.getQuantity(
                parseDouble(nm.get("valGeoidUndulation").getTextContent()),
                parseLengthUnit(nm.get("uomDistVer").getTextContent())
            ));
        }

        Optional.ofNullable(nm.get("valMagVar")).map(Node::getTextContent).map(Double::parseDouble).ifPresent(builder::magVar);
        Optional.ofNullable(nm.get("dateMagVar")).map(Node::getTextContent).map(Year::parse).ifPresent(builder::magVarUpdated);

        Optional.ofNullable(nm.get("txtNameCitySer")).map(Node::getTextContent).ifPresent(builder::servedCity);
        Optional.ofNullable(nm.get("txtDescrSite")).map(Node::getTextContent).ifPresent(builder::siteDescription);
        Optional.ofNullable(nm.get("txtNameAdmin")).map(Node::getTextContent).ifPresent(builder::adminAuthority);

        ContactInfoExtractor cex = new ContactInfoExtractor(adId);
        builder.contactInfos(cex.extract(dex));

        RunwayExtractor rex = new RunwayExtractor(adId);
        builder.runways(rex.extract(dex));

        CtrExtractor ctrex = new CtrExtractor(code);
        builder.ctr(ctrex.extract(dex).orElse(null));

        return builder.build();
    }
}
