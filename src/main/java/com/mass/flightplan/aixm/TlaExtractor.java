package com.mass.flightplan.aixm;

import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;
import tech.units.indriya.quantity.Quantities;

import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.List;

import static com.mass.flightplan.aixm.AixmUtils.*;

@RequiredArgsConstructor
public class TlaExtractor
    implements AixmExtractor<List<TakeOffLandingArea>>
{
    private final @lombok.NonNull String adUid;

    @Override
    public List<TakeOffLandingArea> extract(@NonNull XPathDocumentExtractor dex)
        throws XPathExpressionException
    {
        List<Node> nl = dex.extractNodes(tlofPathExpression());

        /*
            <Tla>
                <TlaUid mid="39700460">
                    <AhpUid mid="20579132">
                        <codeId>LFH466</codeId>
                    </AhpUid>
                    <txtDesig>LFH466 - TLOF</txtDesig>
                </TlaUid>
                <geoLat>460416.00N</geoLat>
                <geoLong>0063104.00E</geoLong>
                <codeDatum>WGE</codeDatum>
                <valElev>1535</valElev> //OPT
                <uomDistVer>FT</uomDistVer>
                <valCrc>F90BAD8C</valCrc>
                <valLen>12.2</valLen> //OPT
                <valWid>20</valWid> // OPT
                <uomDim>M</uomDim>
                <codeComposition>OTHER</codeComposition>
                <txtRmk>Diamètre 12.2 - enrobé -</txtRmk>
            </Tla>
         */

        List<TakeOffLandingArea> ret = new ArrayList<>();

        for (Node n : nl) {
            var m = mapChildren(n);
            var b = TakeOffLandingArea.builder();

            var desig = mapChildren(m.get("TlaUid")).get("txtDesig").getTextContent();
            b.designation(desig);

            var coord = new Point(
                lonToDecimal(m.get("geoLong").getTextContent()), latToDecimal(m.get("geoLat").getTextContent())
            );
            b.coordinates(coord);

            if (m.containsKey("valElev")) {
                var elevUnit = parseLengthUnit(m.get("uomDistVer").getTextContent());
                var elev = Quantities.getQuantity(Double.parseDouble(m.get("valElev").getTextContent()), elevUnit);
                b.elevation(elev);
            }

            if (m.containsKey("valLen")) {
                var dimUnit = parseLengthUnit(m.get("uomDim").getTextContent());
                var len = Quantities.getQuantity(Double.parseDouble(m.get("valLen").getTextContent()), dimUnit);
                var wid = len;
                if (m.containsKey("valWid")) {
                    wid = Quantities.getQuantity(Double.parseDouble(m.get("valWid").getTextContent()), dimUnit);
                }

                b.length(len).width(wid);
            }

            if (m.containsKey("codeComposition")) {
                b.composition(m.get("codeComposition").getTextContent());
            }

            if (m.containsKey("txtRmk")) {
                b.remark(m.get("txtRmk").getTextContent());
            }

            ret.add(b.build());
        }

        return List.copyOf(ret);
    }

    private String tlofPathExpression() {
        // language=XPath
        return "/AIXM-Snapshot/Tla[TlaUid/AhpUid/@mid='" + adUid + "']";
    }
}
