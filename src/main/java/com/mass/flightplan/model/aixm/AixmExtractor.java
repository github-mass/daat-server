package com.mass.flightplan.model.aixm;

import com.mass.flightplan.util.XPathDocumentExtractor;
import org.springframework.lang.NonNull;

public interface AixmExtractor<T> {

    T extract(@NonNull XPathDocumentExtractor docExtractor)
        throws Exception;

}
