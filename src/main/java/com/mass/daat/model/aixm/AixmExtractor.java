package com.mass.daat.model.aixm;

import com.mass.daat.util.XPathDocumentExtractor;
import org.springframework.lang.NonNull;

public interface AixmExtractor<T> {

    T extract(@NonNull XPathDocumentExtractor docExtractor)
        throws Exception;

}
