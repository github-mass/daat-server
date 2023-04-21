package com.mass.flightplan.vac;

import lombok.NonNull;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collection;

public interface VacPdfExtractor {

    @NonNull Collection<? extends OcrBlock> extractPdfText(@NonNull Resource pdf)
        throws IOException, InterruptedException;

}
