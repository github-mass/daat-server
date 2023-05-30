package com.mass.flightplan.aixm;

import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;
import tech.units.indriya.quantity.Quantities;

import javax.xml.xpath.XPathExpressionException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.mass.flightplan.aixm.AixmUtils.*;
import static java.lang.Double.parseDouble;

@Slf4j
public class HeliportsExtractor
    implements AixmExtractor<List<Heliport>>
{
    @NonNull
    @Language("XPath")
    protected String xpathExpression(){
        return "/AIXM-Snapshot/Ahp[codeType='HP']";
    }

    @Override
    public List<Heliport> extract(@NonNull XPathDocumentExtractor dex)
        throws ExecutionException
    {
        try {
            List<Node> nl = dex.extractNodes(xpathExpression());

            List<Heliport> ret = new ArrayList<>();
            for(Node node: nl){
                try {
                    ret.add(extractHeliport(node, dex));
                }
                catch (Exception x) {
                    throw new ExecutionException(
                        "Extraction failed for heliport %s".formatted(mapAttributes(mapChildren(node).get("AhpUid")).get("mid")),
                        x
                    );
                }
            }

            return ret;
        }
        catch (XPathExpressionException e) {
            //shouldn't happen
            log.atError().log("Failed to extract heliports", e);
            return List.of();
        }
    }

    @SneakyThrows
    private Heliport extractHeliport(Node ahp, XPathDocumentExtractor dex)
    {
        /*
            <Ahp>
                <AhpUid mid="1522342">
                    <codeId>LFH218</codeId>
                </AhpUid>
                <OrgUid mid="1520800">
                    <txtName>FRANCE</txtName>
                </OrgUid>
                <txtName>VAL D'ISERE LA DAILLE</txtName>
                <codeType>HP</codeType>
                <geoLat>452726.02N</geoLat>
                <geoLong>0065803.85E</geoLong>
                <codeDatum>WGE</codeDatum>
                <valElev>5910</valElev>
                <valGeoidUndulation>179</valGeoidUndulation>
                <uomDistVer>FT</uomDistVer>
                <valCrc>D0F1468E</valCrc>
                <valMagVar>2.35</valMagVar>
                <dateMagVar>2020</dateMagVar>
                <valMagVarChg>0.16</valMagVarChg>
                <Aht>
                    <codeWorkHr>OTHER</codeWorkHr>
                    <txtRmkWorkHr>NIL</txtRmkWorkHr>
                </Aht>
            </Ahp>

         */
        var builder = Heliport.builder();
        var nm = mapChildren(ahp);

        String adId = mapAttributes(nm.get("AhpUid")).get("mid");

        builder.id(adId);
        builder.code(mapChildren(nm.get("AhpUid")).get("codeId").getTextContent());
        builder.name(nm.get("txtName").getTextContent());
        builder.coordinates(new Point(
            lonToDecimal(nm.get("geoLong").getTextContent()),
            latToDecimal(nm.get("geoLat").getTextContent())
        ));
        builder.elevation(Quantities.getQuantity(
            parseDouble(nm.get("valElev").getTextContent()),
            parseLengthUnit(nm.get("uomDistVer").getTextContent())
        ));
        if(nm.containsKey("valGeoidUndulation")) {
            builder.geoidUndulation(Quantities.getQuantity(
                parseDouble(nm.get("valGeoidUndulation").getTextContent()),
                parseLengthUnit(nm.get("uomDistVer").getTextContent())
            ));
        }

        Optional.ofNullable(nm.get("txtNameAdmin")).map(Node::getTextContent).ifPresent(builder::adminAuthority);
        Optional.ofNullable(nm.get("valMagVar")).map(Node::getTextContent).map(Double::parseDouble).ifPresent(builder::magVar);
        Optional.ofNullable(nm.get("dateMagVar")).map(Node::getTextContent).map(Year::parse).ifPresent(builder::magVarUpdated);

        TlaExtractor tex = new TlaExtractor(adId);
        builder.takeoffLandingAreas(tex.extract(dex));

        ContactInfoExtractor cex = new ContactInfoExtractor(adId);
        builder.contactInfos(cex.extract(dex));

        return builder.build();
    }
}
