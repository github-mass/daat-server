package com.mass.flightplan.model;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class NodeUtils {
    /**
     * <p>Returns a map whose keys are the names of the child elements of the specified node, and the values the child nodes themselves.</p>
     * <p><em>WARNING:</em> If there are multiple child elements with the same name, <em>only the last one is retained</em>.</p>
     * <p>If the node does not have child elements, returns an empty map.</p>
     *
     * @param n the node whose child elements should be accessed.
     * @return A mapping of child element name -> child element.
     */
    public static Map<String, Node> mapChildren(Node n) {
        return childElements(n)
            .collect(Collectors.toMap(Node::getNodeName, Function.identity(), (n1, n2) -> n2)); //silently dropping duplicate children here
    }

    public static Map<String, String> mapAttributes(Node n) {
        NamedNodeMap attrs = n.getAttributes();
        return IntStream.range(0, attrs.getLength())
                        .mapToObj(attrs::item)
                        .collect(Collectors.toMap(Node::getNodeName, Node::getTextContent));
    }

    public static Stream<Node> childElements(Node n){
        return toStream(n.getChildNodes())
            .filter(n0 -> n0.getNodeType() == Node.ELEMENT_NODE);
    }

    public static Optional<Node> child(Node parent, String name){
        return childElements(parent).filter(n -> n.getNodeName().equals(name)).findFirst();
    }

    public static Stream<Node> toStream(NodeList nl) {
        return IntStream.range(0, nl.getLength()).mapToObj(nl::item);
    }

    static final TransformerFactory TF;
    static {
        TF = TransformerFactory.newInstance();
    }

    public static CharSequence nodeToString(Node n) {
        try {
            Transformer t = TF.newTransformer();

            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(n), new StreamResult(sw));

            return sw.toString();
        }
        catch (Exception x) {
            log.warn("Error converting node to string", x);
            return null;
        }
    }
}
