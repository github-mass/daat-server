package com.mass.flightplan.model.zicad;

import com.mass.flightplan.model.Dataset;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.lang.Nullable;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.time.Instant.now;

@Builder
@Slf4j
public class ZicadImporter {

    public static final String DATASET_TYPE = "ZICAD";

    private final @NonNull String sourceName;
    private final @Nullable String sourceDescription;
    private final @NonNull URL source;

    @Value
    @Builder
    public static class Result {
        Dataset dataset;
        List<ZicadZone> zones;
    }

    public Result perform()
        throws Exception
    {
        return performImport();
    }

    Result performImport()
        throws Exception
    {
        log.atLevel(Level.INFO).log("Starting ZICAD import from {}", source);
        Instant start = now();

        ZicadKmlParser parser = new ZicadKmlParser();
        List<ZicadZone> zones;

        try(InputStream is = connect(source).getInputStream()){
            zones = parser.parse(is).toList();
        }

        log.atLevel(Level.INFO).log("Parsed {} ZICAD zones from {} in {}s", zones.size(), source, Duration.between(start, now()).toMillis() / 1000d);

        Dataset ds = buildDataset();

        return Result.builder().zones(zones).dataset(ds).build();
    }

    Dataset buildDataset()
        throws Exception
    {
        URLConnection conn = connect(source);
        if(conn instanceof HttpURLConnection){
            ((HttpURLConnection)conn).setInstanceFollowRedirects(true);
        }
        long lastMod = conn.getLastModified();

        var builder = Dataset.builder();

        Instant now = now();

        builder.created(now);
        builder.sourceName(sourceName);
        builder.datasetType(DATASET_TYPE);
        builder.sourceDescription(sourceDescription);
        builder.created(Instant.ofEpochMilli(lastMod));
        builder.effective(Instant.ofEpochMilli(lastMod));

        return builder.build();

    }

    URLConnection connect(URL url)
        throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, KeyManagementException
    {
        URLConnection ret = url.openConnection();

        if(ret instanceof HttpsURLConnection sslConn) {
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(getClass().getResourceAsStream("/zicadcerts"), "password".toCharArray());
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            sslConn.setSSLSocketFactory(ctx.getSocketFactory());
        }

        return ret;
    }
}
