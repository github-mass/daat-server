package com.mass.daat.model.aixm;

import com.mass.daat.model.NodeUtils;
import com.mass.daat.util.XPathDocumentExtractor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SiaContactExtractor
    implements AixmExtractor<Map<String, String>>
{
    @Override
    public Map<String, String> extract(@NotNull XPathDocumentExtractor dex)
        throws XPathExpressionException
    {
        List<Node> nodes = dex.extractNodes(xpathExpression());

        return nodes.stream()
            .map(NodeUtils::mapChildren)
            .filter(m -> m.containsKey("Exploitant") && m.containsKey("Nom"))
            .collect(Collectors.toMap(
                m -> m.get("Nom").getTextContent(),
                m -> m.get("Exploitant").getTextContent()
            ));
    }

    @Language("XPath")
    protected String xpathExpression(){
        return "/SiaExport/Situation/HelistationS/Helistation";
    }
}
