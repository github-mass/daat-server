package com.mass.flightplan.vac;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;
import org.bytedeco.tesseract.global.tesseract;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static org.bytedeco.leptonica.global.lept.pixDestroy;
import static org.bytedeco.leptonica.global.lept.pixRead;
import static org.bytedeco.tesseract.global.tesseract.RIL_BLOCK;

@RequiredArgsConstructor
@Log4j2
class WslOcrPdfExtractor
    implements VacPdfExtractor
{
    private final @NonNull ThreadPoolTaskExecutor executor;
    private final @NonNull Duration timeout;
    private final boolean cleanUp;

    @PostConstruct
    void validate() {
        if (executor.getCorePoolSize() < 4) {
            throw new IllegalArgumentException("Executor pool needs at least 4 core threads, but has: " + executor.getCorePoolSize());
        }
    }

    @Override
    public @NonNull Collection<? extends OcrBlock> extractPdfText(@NonNull Resource pdf)
        throws IOException, InterruptedException
    {
        final Path workingDir = Files.createTempDirectory("flightplan-ocr");
        Instant start = now();

        var future = executor
            .submitCompletable(() -> convertPdfToImages(pdf, workingDir, timeout))
            .thenCompose(files -> executor.submitCompletable(() -> performImageOcr(files, timeout)));

        try {
            Collection<? extends OcrBlock> ret = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("OCR extraction for {} complete after {}ms", pdf, between(start, now()).toMillis());

            if (cleanUp) {
                log.debug("Marking temp files for deletion on exit...");
                workingDir.toFile().deleteOnExit(); //executed in reverse order of registration
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(workingDir)) {
                    for (Path p : ds) p.toFile().deleteOnExit();
                } catch (Exception x) {
                    log.warn("Error while iterating working directory [ignoring]", x);
                }
            }
            else {
                try {
                    storeOutput(ret, workingDir);
                } catch (Throwable x) {
                    log.warn("Failed to store OCR output", x);
                }
            }

            return ret;
        } catch (TimeoutException tex) {
            future.cancel(true);
            throw new IOException("OCR extraction process timed out after " + timeout);
        } catch (ExecutionException eex) {
            throw new IOException("OCR process encountered an error", eex.getCause());
        }
    }

    private @NonNull List<? extends Resource> convertPdfToImages(@NonNull Resource pdf, @NonNull Path workingDir, @NonNull Duration timeout)
        throws IOException, InterruptedException, TimeoutException
    {
        checkInterrupt();

        Path pdfFile;
        if (!pdf.isFile()) {
            Path pdfTmp = Files.createTempFile(workingDir, "base", ".pdf");
            log.debug("Writing PDF data to tmp file");
            try (InputStream is = pdf.getInputStream(); OutputStream out = Files.newOutputStream(pdfTmp)) {
                StreamUtils.copy(is, out);
            }
            pdfFile = pdfTmp;
        }
        else {
            pdfFile = pdf.getFile().toPath();
        }

        //requires poppler-utils
        List<String> cmd = List.of(
            "wsl.exe", "--exec",
            "pdftoppm",
            "-r", "400", //400 dpi. Not too big, not too small. (Same as ocrmypdf default)
            "-png", "-gray", // PNG for no particular reason, but we want grayscale for better OCR
            "-aa", "yes", "-aaVector", "yes", //AntiAlias fonts and vectors, because it sounds like a good idea
            toWslPath(pdfFile), //source, converted from windows path to WSL path
            toWslPath(workingDir.resolve("pdf-image")) //destination, converted from windows to WSL path. Will create 1 file per PDF page.
        );

        checkInterrupt();

        //run the command
        Process proc = setupProcess(cmd);

        executor.execute(() -> {
            try {
                InputStream is = proc.getErrorStream();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(StreamUtils.nonClosing(is)))) {
                    for (String line; !Thread.currentThread().isInterrupted() && (line = br.readLine()) != null; ) {
                        log.debug("PDFTOPPM: {}", line);
                    }
                }
            } catch (Exception x) {
                log.warn(x);
            }
        });

        if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException();
        }
        if (proc.exitValue() != 0) {
            throw new IOException("PDFtoPPM process failed with code " + proc.exitValue());
        }

        //collect files
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(workingDir)) {
            List<? extends Resource> ret = StreamSupport.stream(ds.spliterator(), false)
                                                        .filter(file -> file.getFileName().toString().startsWith("pdf-image"))
                                                        .map(FileSystemResource::new)
                                                        .toList();

            log.info("Generated {} page images from {}", ret.size(), pdf);

            return ret;
        }
    }

//    private @NonNull CharSequence performImageOcr(@NonNull List<? extends Resource> images, @NonNull Path workingDir, @NonNull Duration timeout)
//        throws IOException, InterruptedException, TimeoutException
//    {
//        //create image list file
//        Path input_list = Files.createTempFile(workingDir, "tesseract-input", ".ll");
//        try(BufferedWriter bw = Files.newBufferedWriter(input_list)){
//            for(Resource res: images){
//                bw.write(toWslPath(res.getFile().toPath()));
//                bw.write(LINE_SEP);
//            }
//        }
//
//        //requires tesseract
//        List<String> cmd = List.of(
//            "wsl.exe", "--exec",
//            "tesseract",
//            toWslPath(input_list), //read input list
//            "-", //write txt to STDOUT
//            "-l", "eng+fra", //fra still useful for the couple of special chars
//            "--dpi", "400", //same as we specified for PDFtoPPM
//            "--psm", "11", //this is quite important, the segmentation mode
//            "-c", "preserve_interword_spaces=1" //this might help a bit with positional data
//        );
//        // This will make us run with the (older) Tesseract 4 engine, but that should be plenty.
//
//        checkInterrupt();
//        Process proc = setupProcess(cmd);
//
//        executor.execute(() -> {
//            try {
//                InputStream is = proc.getErrorStream();
//                try (BufferedReader br = new BufferedReader(new InputStreamReader(StreamUtils.nonClosing(is)))) {
//                    for (String line; !Thread.currentThread().isInterrupted() && (line = br.readLine()) != null; ) {
//                        log.debug("TESSERACT: {}", line);
//                    }
//                }
//            } catch (Exception x) {
//                log.warn(x);
//            }
//        });
//
//        //extract OCR output
//        Future<CharSequence> ocrOutputFuture = executor.submit(() -> {
//            StringBuilder ret = new StringBuilder();
//            InputStream is = proc.getInputStream();
//            //We don't want to close the stream, let it take care of itself
//            try (BufferedReader br = new BufferedReader(new InputStreamReader(StreamUtils.nonClosing(is)))) {
//                for (String line; !Thread.currentThread().isInterrupted() && null != (line = br.readLine()); ) {
//                    /*
//                        Note: we're skipping empty lines.
//                     */
//                    if (line.trim().length() > 0) {
//                        ret.append(line).append(LINE_SEP);
//                    }
//                }
//            }
//
//            return ret;
//        });
//
//        if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
//            throw new TimeoutException();
//        }
//        if (proc.exitValue() != 0) {
//            throw new IOException("OCR process failed with code " + proc.exitValue());
//        }
//
//        try {
//            CharSequence cs = ocrOutputFuture.get(5, TimeUnit.SECONDS);
//
//            try {
//                //save output for debugging
//                Path ocrOutput = Files.createTempFile(workingDir, "ocr_output", ".txt");
//                Files.writeString(ocrOutput, cs);
//            } catch (Throwable t) {
//                log.warn("Could not save OCR output", t);
//            }
//
//            return cs;
//        } catch (ExecutionException eex) {
//            throw new IOException("OCR execution failed", eex.getCause());
//        }
//    }

    private @NonNull Collection<? extends OcrBlock> performImageOcr(@NonNull Collection<? extends Resource> images, @NonNull Duration timeout)
        throws IOException, InterruptedException, ExecutionException, TimeoutException
    {
        @SuppressWarnings("unchecked")
        CompletableFuture<Collection<? extends OcrBlock>>[] stash = new CompletableFuture[images.size()];
        int pageNum = 0;
        for(Resource image: images){
            int index = pageNum;
            stash[index] = executor.submitCompletable(() -> performImageOcr(image, index));
            pageNum++;
        }

        CompletableFuture.allOf(stash).get(timeout.toMillis(), TimeUnit.MILLISECONDS); //throws

        var ret = Arrays.stream(stash)
                     .map(cf -> {
                         try {
                             return cf.get();
                         } catch (Throwable t) {
                             t.printStackTrace();
                         }
                         return Set.<OcrBlock>of();
                     })
                     .flatMap(Collection::stream)
                     .toList();

        log.debug("Extracted {} blocks from {} pages", ret.size(), images.size());

        return ret;
    }

    private @NonNull Collection<? extends OcrBlock> performImageOcr(@NonNull Resource imageRes, int pageNum)
        throws IOException, InterruptedException
    {
        Collection<OcrBlock> ret = new ArrayList<>();

        TessBaseAPI tessApi = new TessBaseAPI();

        int code;
        if (0 != (code = tessApi.Init(null, "eng+fra"))) {
            throw new IllegalStateException("Could not initialise tesseract API: " + code);
        }

        try {
            tessApi.SetPageSegMode(tesseract.PSM_SPARSE_TEXT);

            final int[] left = new int[1], top = new int[1], bottom = new int[1], right = new int[1];
            PIX image = pixRead(imageRes.getFile().getAbsolutePath());
            tessApi.SetImage(image);

            double imageHeight = tessApi.GetInputImage().h();
            double imageWidth = tessApi.GetInputImage().w();

            tessApi.SetSourceResolution(400);

            final int recog = tessApi.Recognize(null);
            int pageBlockCount = 0;

            var it = tessApi.GetIterator();
            it.Begin();
            while (it.Next(RIL_BLOCK)) {
                it.BoundingBox(RIL_BLOCK, 5, left, top, right, bottom);
                var txt = it.GetUTF8Text(RIL_BLOCK);
                ret.add(
                    OcrBlock.builder()
                            .page(pageNum).text(txt.getString().trim()) //NOTE the trim()
                            .left(left[0] / imageWidth).right(right[0] / imageWidth)
                            .top(top[0] / imageHeight).bottom(bottom[0] / imageHeight)
                            .build()
                );
                pageBlockCount++;
//                    log.debug("Got BLOCK at ({}, {}) -> ({}, {}):%n{}", top[0], left[0], bottom[0], right[0], txt.getString());
            }

            it.deallocate();
            pixDestroy(image);

            log.debug("Extracted {} OCR blocks from page {}", pageBlockCount, pageNum);
        } finally {
            tessApi.End();
        }

        return ret;
    }

    private void storeOutput(Collection<? extends OcrBlock> output, Path workingDir)
        throws IOException
    {
        Path p = workingDir.resolve("ocr_output.txt");
        try (var w = Files.newBufferedWriter(p)) {
            List<String> lines =
                output.stream()
                      .sorted(OcrBlock.TOP_FIRST_THEN_LEFT)
                      .map(b -> String.format("%03d::[%4f, %4f] -> [%4f, %4f]%n%s", b.page(), b.top(), b.left(), b.bottom(), b.right(), b.text()))
                      .toList();

            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }
    }

    private static String toWslPath(Path windowsPath) {
        windowsPath = windowsPath.toAbsolutePath();

        StringBuilder sb = new StringBuilder();
        sb.append("/mnt/").append(windowsPath.getRoot().toString().substring(0, 1).toLowerCase());
        //the root is not returned by the iterator
        for (Path fragment : windowsPath) {
            sb.append("/").append(fragment.toString());
        }

        return sb.toString();
    }

    private Process setupProcess(List<String> cmd)
        throws IOException
    {
        log.debug("Launching command: {}", cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        return pb.start();
    }

    private static void checkInterrupt()
        throws InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

    private static final String LINE_SEP = System.getProperty("line.separator");
}
