package com.mass.flightplan;

import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "zicad")
@Data
@Accessors(fluent = false)
@Validated
@SuppressWarnings("DefaultAnnotationParam")
public class ZicadProperties {

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    public ZicadImportProperties importProps = new ZicadImportProperties();

    public ZicadImportProperties getImport(){
        return importProps;
    }

    public void setImport(ZicadImportProperties p){
        this.importProps = p;
    }

    @NestedConfigurationProperty
    public ZicadUpdaterProperties update = new ZicadUpdaterProperties();

    @Data
    @Accessors(fluent = false)
    public static class ZicadImportProperties {
        private List<ZicadImportSource> sources = new ArrayList<>();
    }

    @Data
    @ToString
    @Accessors(fluent = false)
    public static class ZicadImportSource {
        private String uri;
        private String description;
    }

    @Data
    @Accessors(fluent = false)
    public static class ZicadUpdaterProperties {
        private boolean enabled = false;
        private long initialDelay = 0;
        private long fixedDelay = 60;
    }
}
