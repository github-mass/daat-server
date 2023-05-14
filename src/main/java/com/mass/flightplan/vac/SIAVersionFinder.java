package com.mass.flightplan.vac;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Log4j2
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SIAVersionFinder {

    private static final String EAIP_VERSION_PROPERTY_NAME = "vac-atlas.e-aip-version";

    @Value("${sia.siteplan-url}")
    private final String siaSitePlanUrl;
    private final boolean autoUpdateEnabled;
    private final ConfigurableEnvironment env;
    private final ContextRefresher refresher;


    private String lastFoundVersion = "";

    @PostConstruct
    public void register(){
        env.getPropertySources().addFirst(dynamicPropertySource);
    }


    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    @EventListener(ContextStartedEvent.class)
    public void autoUpdateSiaVersion(){
        if(autoUpdateEnabled) {
            updateSiaVersion();
        }
    }

    public void updateSiaVersion(){
        try {
            String version = findSiaVersion();

            if (lastFoundVersion == null || !lastFoundVersion.equals(version)) {
                log.warn("Found new SIA version: {} (prev={})", version, lastFoundVersion);
                lastFoundVersion = version;
                refresher.refresh();
            }
        }
        catch (Exception x) {
            log.error("SIA Version update failed", x);
        }
    }

    private String findSiaVersion()
        throws IOException
    {
        Document doc = Jsoup.connect(siaSitePlanUrl).get();
        String anchorText = "Atlas VAC";
        String link = doc.select("a:containsOwn("+anchorText+")").attr("href");

        if(link.isEmpty()){
            throw new IOException("Could not find anchor with text '" + anchorText + "' at " + siaSitePlanUrl);
        }

        Pattern p = Pattern.compile("dvd/(eAIP_\\d+_\\w+_\\d+)/Atlas-VAC");
        Matcher m = p.matcher(link);
        if(!m.find()){
            throw new IOException("Could not find pattern " + p.pattern() + " in link " + link);
        }

        String version = m.group(1);
        log.debug("Found SIA version: {}", version);
        return version;
    }

    private final PropertySource<String> dynamicPropertySource = new PropertySource<>(SIAVersionFinder.class.getName()) {
        @Override
        public Object getProperty(@NotNull String name) {
            if(EAIP_VERSION_PROPERTY_NAME.equals(name)){
                return SIAVersionFinder.this.lastFoundVersion;
            } else {
                return null;
            }
        }
    };
}
