package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface VacPdfSanitizer {

    @NonNull
    Resource sanitizePdf(@NonNull Resource pdf) throws IOException, InterruptedException;


    /**
     * Implementation that uses ocrmypdf through WSL on Windows
     */
    @Log4j2
    @Builder
    class WslOcrMyPdfSanitizer
            implements VacPdfSanitizer {
        private final ThreadPoolTaskExecutor executor;
        private final Duration processTimeOut;

        @Builder
        WslOcrMyPdfSanitizer(ThreadPoolTaskExecutor executor, Duration processTimeOut) {
            this.executor = executor;
            this.processTimeOut = processTimeOut;

            if (executor.getCorePoolSize() < 3) {
                throw new IllegalArgumentException("Executor pool for conversion task mst have size at least 3, but is " + executor.getPoolSize());
            }
        }

        @Override
        public Resource sanitizePdf(Resource pdf)
                throws IOException, InterruptedException
        {
//            ProcessBuilder pb = new ProcessBuilder("wsl.exe", "ocrmypdf", "-l", "eng+fra", "--force-ocr", "--output-type", "pdf", "-", "-");
            ProcessBuilder pb = new ProcessBuilder(
                    "wsl.exe", "ocrmypdf", "-l", "eng+fra", "--force-ocr", "--remove-vectors", "--clean-final", "--oversample", "800", "-", "-"
            );
            log.debug("Starting command {}", pb.command());

            final Process p = pb.start();

            executor.execute(() -> {
                try (InputStream is = pdf.getInputStream()) {
                    byte[] buf = new byte[1 << 8];
                    OutputStream os = p.getOutputStream();
                    int written = 0;
                    for(int read; !Thread.currentThread().isInterrupted() && (-1 != (read = is.read(buf))); written += read){
                        os.write(buf, 0, read);
                    }
                    log.debug("Wrote {} bytes to ocrmypdf input", written);
                    // need to close so ocrmypdf knows to start working!
                    p.getOutputStream().close();
                } catch (Exception e) {
                    log.error("Could not write resource to ocrmypdf process", e);
                }
            });
            executor.execute(() -> {
                try {
                    InputStream is = p.getErrorStream();
                    final byte[] buf = new byte[1 << 6];
                    for (int read; !Thread.currentThread().isInterrupted() && (-1 != (read = is.read(buf))); ) {
                        System.out.write(buf, 0, read);
                    }
                } catch (Exception ioex) {
                    //ignore
                }
            });

            Future<Resource> converted = executor.submit(
                    () -> {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(50 << 10);
                        InputStream is = p.getInputStream();
                        byte[] buf = new byte[1 << 8];
                        for (int read; -1 != (read = is.read(buf)); ) {
                            baos.write(buf, 0, read);
                        }

                        baos.close();

                        return new ByteArrayResource(baos.toByteArray());
                    }
            );

            final Instant start = Instant.now();

            try {
                if (p.waitFor(processTimeOut.toMillis(), TimeUnit.MILLISECONDS) ) {
                    if(p.exitValue() != 0){
                        converted.cancel(true);
                        throw new IOException("ocrmypdf process failed with code " + p.exitValue());
                    }

                    Duration conversion = Duration.between(start, Instant.now());
                    Resource ret;

                    try {
                        ret = converted.get(3, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException iex){
                        throw iex;
                    }
                    catch (Exception x) {
                        throw new IOException("Timeout getting ocrmypdf result " + x);
                    }

                    Duration total = Duration.between(start, Instant.now());

                    log.info("Conversion complete; took {}ms (conversion={}ms), output {} bytes", total.toMillis(), conversion.toMillis(), ret.contentLength());

                    return ret;
                } else {
                    throw new IOException("ocrmypdf process timed out after " + processTimeOut);
                }
            } finally {
                p.destroy();
            }
        }
    }
}
