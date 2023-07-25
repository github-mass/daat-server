package com.mass.flightplan.model.aixm;

import com.mass.flightplan.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import org.opengis.referencing.operation.TransformException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.util.Optional;

import static com.mass.flightplan.model.NodeUtils.mapAttributes;
import static com.mass.flightplan.model.NodeUtils.mapChildren;

@RequiredArgsConstructor
public class CtrExtractor
    implements AixmExtractor<Optional<Airspace>>
{
    private final @lombok.NonNull String adIcaoCode;

    @Override
    public Optional<Airspace> extract(@NonNull XPathDocumentExtractor dex)
        throws XPathExpressionException, TransformException
    {
        return Optional.ofNullable(findCtr(dex));
    }

    @Nullable
    private Airspace findCtr(XPathDocumentExtractor dex)
        throws XPathExpressionException
    {
        final Node n = dex.extractNode(ctrAirspacePathExpression());

        if (n == null) {
            return null;
        }

        String aseId = mapAttributes(mapChildren(n).get("AseUid")).get("mid");

        return new AirspaceGeometryBuilder().buildAirspace(aseId, dex);
    }

    private String ctrAirspacePathExpression() {
        // language=XPath
        return "/AIXM-Snapshot/Ase[AseUid/codeType='CTR' and AseUid/codeId='" + adIcaoCode + "']";

    }
}
