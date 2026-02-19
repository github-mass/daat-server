package com.mass.daat.model.aixm;

import com.mass.daat.util.XPathDocumentExtractor;
import org.intellij.lang.annotations.Language;
import org.opengis.referencing.operation.TransformException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mass.daat.model.NodeUtils.mapAttributes;
import static com.mass.daat.model.NodeUtils.mapChildren;

public class AirspaceExtractor
    implements AixmExtractor<List<Airspace>>
{
    @Language("XPath")
    private final String xPathExpression;

    public AirspaceExtractor(@NonNull @Language("XPath") String xpathExpression) {
        this.xPathExpression = xpathExpression;
    }

    @Override
    @org.springframework.lang.NonNull
    public List<Airspace> extract(@org.springframework.lang.NonNull XPathDocumentExtractor docExtractor)
        throws XPathExpressionException, TransformException
    {
            List<Node> nl = docExtractor.extractNodes(xPathExpression);

            List<Airspace> ret = new ArrayList<>();
            AirspaceGeometryBuilder agb = new AirspaceGeometryBuilder();

            for (Node n : nl) {
                String aseId = mapAttributes(mapChildren(n).get("AseUid")).get("mid");
                ret.add(agb.buildAirspace(aseId, docExtractor));
            }

            return ret;
    }

    @NonNull
    public Optional<Airspace> tryExtractOne(@NonNull XPathDocumentExtractor docExtractor)
        throws XPathExpressionException, TransformException
    {
        return extract(docExtractor).stream().findFirst();
    }

    @Nullable
    public Airspace extractOne(@NonNull XPathDocumentExtractor docExtractor)
        throws XPathExpressionException, TransformException
    {
        return tryExtractOne(docExtractor).orElse(null);
    }

    public static AixmExtractor<Airspace> forAirspaceById(String airspaceId){
        return new AirspaceExtractor("/AIXM-Snapshot/Ase[AseUid/@mid='" + airspaceId + "']")::extractOne;
    }

    public static AirspaceExtractor forType(@NonNull AirspaceType type){
        if(type.aixmLocalType() == null){
            return new AirspaceExtractor(
                "/AIXM-Snapshot/Ase[AseUid/codeType='%s']".formatted(type.aixmTypeCode())
            );
        } else {
            return new AirspaceExtractor(
                "/AIXM-Snapshot/Ase[AseUid/codeType='%s' and txtLocalType='%s']".formatted(type.aixmTypeCode(), type.aixmLocalType())
            );
        }
    }
}
