package com.mass.flightplan.model.aixm;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@UtilityClass
public class SiaXmlFileMagic {

    private static final XMLInputFactory xif = XMLInputFactory.newFactory();

    @NonNull
    public SiaXmlFileType identify(@NonNull Path p)
        throws IllegalArgumentException
    {
        return identify(p.toFile());
    }

    @NonNull
    public SiaXmlFileType identify(@NonNull Resource resource) {
        String rootName = null;
        try(InputStream is = resource.getInputStream()){
            rootName = xmlRootElementName(is);
        }
        catch (IOException e) {
            log.warn("Could not open resource {}", resource, e);
        }

        return resolve(rootName);
    }

    @NonNull
    public SiaXmlFileType identify(@NonNull File file)
        throws IllegalArgumentException
    {
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Not a file or can't read: " + file);
        }

        String rootElementName = xmlRootElementName(file);
        return resolve(rootElementName);
    }

    private SiaXmlFileType resolve(String rootName){
        if ("AIXM-Snapshot".equalsIgnoreCase(rootName)) {
            return SiaXmlFileType.AIXM_SNAPSHOT;
        }
        else if ("SiaExport".equalsIgnoreCase(rootName)) {
            return SiaXmlFileType.SIA_EXPORT;
        }
        else {
            return SiaXmlFileType.UNKNOWN;
        }
    }

    @Nullable
    private String xmlRootElementName(File f) {
        try (InputStream is = Files.newInputStream(f.toPath(), StandardOpenOption.READ)) {
            return xmlRootElementName(is);
        }
        catch (IOException e) {
            log.atLevel(Level.WARN).setMessage("Failed to open").addKeyValue("path", f.toPath()).setCause(e).log();
        }

        return null;
    }

    @Nullable
    private String xmlRootElementName(InputStream is) {
        try {
            XMLStreamReader xsr = xif.createXMLStreamReader(is);

            while (xsr.hasNext()) {
                if (xsr.next() == XMLStreamConstants.START_ELEMENT) {
                    return xsr.getLocalName();
                }
            }
        }
        catch (Exception x) {
            log.atTrace().log("XML streaming error: {}", x.getMessage());
        }

        return null;
    }

}
