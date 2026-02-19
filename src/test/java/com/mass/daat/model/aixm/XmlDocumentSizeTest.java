package com.mass.daat.model.aixm;

import com.mass.daat.util.XPathDocumentExtractor;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mass.daat.model.NodeUtils.mapChildren;

@Slf4j
public class XmlDocumentSizeTest {

    @Test
    @Disabled
    void testLoadDocument()
        throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Path xmlFile = Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/XML_SIA_2023-04-20.xml");

        System.gc();
        final long memUsedBefore = Runtime.getRuntime().totalMemory();

        Document doc = db.parse(xmlFile.toFile());

        db = null;
        System.gc();
        final long memUsedAfter = Runtime.getRuntime().totalMemory();

        System.out.println("XML file size: " + DataSize.of(Files.size(xmlFile), DataUnit.BYTES).toKilobytes() + "kB");
        System.out.println("In-memory document size: " + DataSize.of(memUsedAfter - memUsedBefore, DataUnit.BYTES).toKilobytes() + "kB");

        //make sure we have a ref
        System.out.println(doc.getDocumentElement().getTagName());
    }

    @SneakyThrows
    @Test
    @Disabled
    void getHpNodeByXPath_WithDefaultDocumentBuilder()
        throws XPathExpressionException, IOException
    {
        Path xml = Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/XML_SIA_2023-04-20.xml");

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xp = xpf.newXPath();
        XPathExpression xpe = xp.compile("/AIXM-Snapshot/Ahp[codeType='HP']");
//        XPathExpression xpe = xp.compile("/AIXM-Snapshot/Ahp[codeType='HP' and AhpUid/codeId='LFH450']");

        System.gc();
        final long memUsedBefore = Runtime.getRuntime().totalMemory();


        try(InputStream is = Files.newInputStream(xml)) {
            NodeList nl = (NodeList) xpe.evaluate(new InputSource(is), XPathConstants.NODESET);

            System.out.printf("Found %d nodes%n", nl.getLength());

            for(int ii = 0; ii < nl.getLength(); ii++){
                Node n = nl.item(ii);
                System.out.printf("Node at %d: %s%n", ii, mapChildren(mapChildren(n).get("AhpUid")).get("codeId").getTextContent());
            }
        }

        System.gc();
        final long memUsedAfter = Runtime.getRuntime().totalMemory();
        System.out.println("Used memory: " + DataSize.of(memUsedAfter - memUsedBefore, DataUnit.BYTES).toKilobytes() + "kB");
    }

    @Test
    @Disabled
    void getHpNodeByXPath_WithVtd()
        throws Exception
    {
        TimeUnit.SECONDS.sleep(10);

        DocumentBuilder db = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
        Node fragment = db.newDocument().createDocumentFragment();

        Path xml = Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/XML_SIA_2023-04-20.xml");

        final byte[] xmlData = Files.readAllBytes(xml);

        VTDGen vtdGen = new VTDGen();
        vtdGen.setDoc(xmlData);
        vtdGen.parse(false); // arg = namespace awareness

        VTDNav nav = vtdGen.getNav();
        AutoPilot ap = new AutoPilot(nav);
        int count=0;

        ap.selectXPath("/AIXM-Snapshot/Ahp[codeType='HP']");
        while(ap.evalXPath() != -1){
            long OL = nav.getElementFragment();
            InputStream is = new ByteArrayInputStream(xmlData, (int) (OL & 0xffffff), (int) (OL >> 32));
            Node n = fragment.getOwnerDocument().importNode(db.parse(is).getDocumentElement(), true);
            fragment.appendChild(n);
            count++;
        }

        log.info("found {} elements", count);

        DOMSource domSource = new DOMSource(fragment);
        StreamResult result = new StreamResult(System.out);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(domSource, result);

        TimeUnit.SECONDS.sleep(10);
    }

    @Test
    @Disabled
    void getHpNodeByXPath_WithCustomExtractor()
        throws Exception
    {
        Path xml = Path.of("data/aixm/export_xml_bd_sia_2023-04-20-p2/XML_SIA_2023-04-20.xml");

        XPathDocumentExtractor extractor = XPathDocumentExtractor.from(xml).namespaceAware(false).build();

        List<Node> nl = extractor.extractNodes("/AIXM-Snapshot/Ahp[codeType='HP']");

        log.info("Found {} nodes", nl.size());

        nl.stream().map(n -> mapChildren(n).get("txtName").getTextContent()).forEach(log::info);
    }

}
