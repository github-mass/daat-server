package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public interface VacPdfExtractor {

    @NonNull Result extractPdfText(@NonNull Resource pdf)
        throws IOException, InterruptedException;

    @Builder
    @Value
    class Result {
        @NonNull List<TextBlock> words;
        @NonNull List<TextBlock> blocks;
        @NonNull List<TextBlock> blocksMergedInFlow;
        @NonNull List<TextBlock> blocksMergedInPage;
    }
}
