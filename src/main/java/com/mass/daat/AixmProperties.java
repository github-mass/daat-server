package com.mass.daat;

import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "aixm")
@Data
@Accessors(fluent = false)
@Validated
@SuppressWarnings("DefaultAnnotationParam")
public class AixmProperties {

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    public AixmImportProperties importProps = new AixmImportProperties();

    public AixmImportProperties getImport(){
        return importProps;
    }

    public void setImport(AixmImportProperties p){
        this.importProps = p;
    }

    @NestedConfigurationProperty
    public AixmUpdaterProperties update = new AixmUpdaterProperties();

    @Data
    @Accessors(fluent = false)
    public static class AixmImportProperties {
        private List<AixmImportSource> sources = new ArrayList<>();
        private boolean parseSiaExport = true;
    }

    @Data
    @ToString
    @Accessors(fluent = false)
    public static class AixmImportSource {
        private String uri;
        private String description;
    }

    @Data
    @Accessors(fluent = false)
    public static class AixmUpdaterProperties {
        private boolean enabled = false;
        private long initialDelay = 0;
        private long fixedDelay = 60;
    }
}
