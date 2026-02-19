package com.mass.daat.model.aixm;

import com.mass.daat.model.NodeUtils;
import com.mass.daat.util.XPathDocumentExtractor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.Language;
import org.springframework.lang.NonNull;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ContactInfoExtractor
    implements AixmExtractor<Map<String, String>>
{
    private final @lombok.NonNull String adUid;

    @Override
    public Map<String, String> extract(@NonNull XPathDocumentExtractor docExtractor)
        throws XPathExpressionException
    {
        List<Node> nodes = docExtractor.extractNodes(contactInfoPathExpression());

        /*
            <Aha>
                <AhaUid mid="1574774">
                    <AhpUid mid="1521822">
                        <codeId>FMEE</codeId>
                    </AhpUid>
                    <codeType>POST</codeType>
                    <noSeq>1</noSeq>
                </AhaUid>
                <txtAddress>74 Avenue Roland Garros#Aérogare passagers#97438 SAINTE MARIE</txtAddress>
            </Aha>
         */

        return nodes.stream()
            .map(NodeUtils::mapChildren)
            .collect(Collectors.toMap(
                m -> NodeUtils.mapChildren(m.get("AhaUid")).get("codeType").getTextContent(),
                m -> m.get("txtAddress").getTextContent()
            ));
    }

    @Language("XPath")
    private String contactInfoPathExpression(){
        return "/AIXM-Snapshot/Aha[AhaUid/AhpUid/@mid='%s']".formatted(adUid);
    }
}
