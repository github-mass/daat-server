package com.mass.flightplan.model.zicad;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;

public class ZicadDownloadTest {

    @Test
    public void testZicadDownload()
        throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException
    {
        URL source = new URL("https://www.geoportail.gouv.fr/depot/layers/ORTHOIMAGERY.ORTHOPHOS.RESTRICTEDAREAS/kml/Arrete_ZICAD_10-2023.kml");

        // specifying values here only to satisfy @NonNull constraints
        ZicadImporter zimp = ZicadImporter.builder()
            .source(source)
            .sourceName("Test source")
            .build()
        ;

        URLConnection conn = zimp.connect(source);
        assertThat(conn.getContentLengthLong()).isGreaterThan(0);
    }
}
