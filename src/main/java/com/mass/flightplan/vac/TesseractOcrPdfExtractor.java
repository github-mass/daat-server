//package com.mass.flightplan.vac;
//
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.log4j.Log4j2;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.rendering.ImageType;
//import org.apache.pdfbox.rendering.PDFRenderer;
//import org.bytedeco.leptonica.PIX;
//import org.bytedeco.leptonica.global.lept;
//import org.bytedeco.tesseract.TessBaseAPI;
//import org.bytedeco.tesseract.global.tesseract;
//import org.springframework.core.io.Resource;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//
//import javax.imageio.ImageIO;
//import java.awt.image.BufferedImage;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.*;
//
//import static java.time.Duration.between;
//import static java.time.Instant.now;
//import static org.bytedeco.leptonica.global.lept.pixDestroy;
//import static org.bytedeco.tesseract.global.tesseract.RIL_BLOCK;
//
//@RequiredArgsConstructor
//@Log4j2
//class TesseractOcrPdfExtractor
//    implements VacPdfExtractor
//{
//    private final @NonNull ThreadPoolTaskExecutor executor;
//    private final @NonNull Duration timeout;
//    private final int ocrDpi;
//
//
//    @Override
//    public @NonNull Collection<? extends TextBlock> extractPdfText(@NonNull Resource pdf)
//        throws IOException, InterruptedException
//    {
//        Instant start = now();
//
//        Path tmpDir = Files.createTempDirectory("flightplan-ocr");
//
//        var future = executor
//            .submitCompletable(() -> convertPdfToImages(pdf, timeout, tmpDir))
//            .thenCompose(files -> executor.submitCompletable(() -> performImageOcr(files, pdf.getDescription(), timeout)));
//
//        try {
//            Collection<? extends TextBlock> ret = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
//            log.info("OCR extraction for {} complete after {}ms", pdf, between(start, now()).toMillis());
//
//            return ret;
//        }
//        catch (TimeoutException tex) {
//            future.cancel(true);
//            throw new IOException("OCR extraction process timed out after " + timeout);
//        }
//        catch (ExecutionException | CompletionException eex) {
//            if (eex.getCause() instanceof IOException) {
//                throw (IOException) eex.getCause();
//            }
//            else {
//                throw new IOException("OCR extraction failed for " + pdf, eex.getCause());
//            }
//        }
//    }
//
//    private @NonNull List<ByteBuffer> convertPdfToImages(@NonNull Resource pdf, @NonNull Duration timeout, Path tmpDir)
//        throws IOException, InterruptedException, CompletionException
//    {
//        Instant start = now();
//
//        PDDocument doc;
//        try (var in = pdf.getInputStream()) {
//            doc = PDDocument.load(in);
//        }
//
//        CompletableFuture<ByteBuffer>[] cfStash = new CompletableFuture[doc.getNumberOfPages()];
//
//        log.debug("Converting {} PDF pages to grayscale images with {} DPI", doc.getNumberOfPages(), ocrDpi);
//        for (int ii = 0; ii < doc.getNumberOfPages(); ii++) {
//            final int page = ii;
//            cfStash[page] = executor.submitCompletable(() -> {
//                try {
//                    PDFRenderer renderer = new PDFRenderer(doc);
//                    BufferedImage img = renderer.renderImage(page, ocrDpi / 72f, ImageType.GRAY);
//                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                    ImageIO.write(img, "tif", baos);
//
//                    return ByteBuffer.wrap(baos.toByteArray());
//                }
//                catch (Exception x) {
//                    throw new IOException("Failed to convert page " + page + " on " + pdf + " to image", x);
//                }
//            });
//        }
//
//        CompletableFuture.allOf(cfStash).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
//
//        List<ByteBuffer> ret = new ArrayList<>();
//
//        for (var cf : cfStash) {
//            try {
//                ret.add(cf.get());
//            }
//            catch (ExecutionException eex) {
//                throw new AssertionError();
//            }
//        }
//
//        log.debug("Finished converting {} to {} images in {}ms", pdf, doc.getNumberOfPages(), between(start, now()).toMillis());
//        doc.close();
//
//        return ret;
//    }
//
//    private @NonNull Collection<? extends TextBlock> performImageOcr(
//        @NonNull Collection<ByteBuffer> images, String documentName, @NonNull Duration timeout
//    )
//    {
//        @SuppressWarnings("unchecked")
//        CompletableFuture<Collection<? extends TextBlock>>[] stash = new CompletableFuture[images.size()];
//        int pageNum = 0;
//        for (var image : images) {
//            final int index = pageNum;
//            stash[index] = executor.submitCompletable(() -> performImageOcr(image, documentName, index));
//            pageNum++;
//        }
//
//        CompletableFuture.allOf(stash).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join(); //throws
//
//        var ret = Arrays.stream(stash)
//                        .map(cf -> {
//                            try {
//                                return cf.get();
//                            }
//                            catch (Throwable t) {
//                                t.printStackTrace();
//                            }
//                            return Set.<TextBlock>of();
//                        })
//                        .flatMap(Collection::stream)
//                        .toList();
//
//        log.debug("Extracted {} blocks from {} pages", ret.size(), images.size());
//
//        return ret;
//    }
//
//    private @NonNull Collection<? extends TextBlock> performImageOcr(@NonNull ByteBuffer imageData, String documentName, int pageNum)
//        throws IOException
//    {
//        Collection<TextBlock> ret = new ArrayList<>();
//
//        TessBaseAPI tessApi = new TessBaseAPI();
//
//        int code;
//        if (0 != (code = tessApi.Init(null, "eng+fra"))) {
//            throw new IllegalStateException("Could not initialise tesseract API: " + code);
//        }
//
//        try {
//            tessApi.SetPageSegMode(tesseract.PSM_SPARSE_TEXT);
//            tessApi.SetVariable("preserve_interword_spaces", "true");
//            tessApi.SetVariable("textord_space_size_is_variable", "true");
//
//            final int[] left = new int[1], top = new int[1], bottom = new int[1], right = new int[1];
//            PIX image = lept.pixReadMem(imageData, imageData.remaining());
//            tessApi.SetImage(image);
//
//            double imageHeight = tessApi.GetInputImage().h();
//            double imageWidth = tessApi.GetInputImage().w();
//
//            tessApi.SetSourceResolution(ocrDpi);
//
//            final int recog = tessApi.Recognize(null);
//            int pageBlockCount = 0;
//
//            var it = tessApi.GetIterator();
//            it.Begin();
//            while (it.Next(RIL_BLOCK)) {
//                it.BoundingBox(RIL_BLOCK, 5, left, top, right, bottom);
//                var txt = it.GetUTF8Text(RIL_BLOCK);
//                ret.add(
//                    TextBlock.builder()
//                             .page(pageNum).text(txt.getString().trim()) //NOTE the trim()
//                             .left(left[0] / imageWidth).right(right[0] / imageWidth)
//                             .top(top[0] / imageHeight).bottom(bottom[0] / imageHeight)
//                             .build()
//                );
//                pageBlockCount++;
////                    log.debug("Got BLOCK at ({}, {}) -> ({}, {}):%n{}", top[0], left[0], bottom[0], right[0], txt.getString());
//            }
//
//            it.deallocate();
//            pixDestroy(image);
//
//            log.debug("Extracted {} OCR blocks from page {}", pageBlockCount, pageNum);
//        }
//        catch (Exception x) {
//            throw new IOException("Failed to perform OCR on page " + pageNum + " of " + documentName, x);
//        }
//        finally {
//            tessApi.End();
//        }
//
//        return mergeBlocksOnSameLine(ret);
//    }
//
//    /*
//        Can't find a way to do this in Tesseract... which there ought to be.
//     */
//    private Collection<? extends TextBlock> mergeBlocksOnSameLine(Collection<? extends TextBlock> blocks) {
//        if (blocks.size() < 2) {
//            return blocks;
//        }
//
//        List<TextBlock> list = new ArrayList<>(blocks);
//        list.sort(TextBlock.TOP_FIRST_THEN_LEFT);
//
//        for (int prev = 0, curr = 1; curr < list.size(); curr++) {
//            TextBlock pb = list.get(prev), cb = list.get(curr);
//            if (pb.vRelDist(cb) < -MIN_VOVERLAP_REQUIRED_FOR_MERGE && pb.hRelDist(cb) < MAX_HDIST_REQUIRED_FOR_MERGE) {
//                list.set(prev, TextBlock.merge(pb, cb));
//                list.set(curr, null);
//            }
//            else {
//                prev = curr;
//            }
//        }
//
//        return list.stream().filter(Objects::nonNull).toList();
//    }
//
//    private static final double MIN_VOVERLAP_REQUIRED_FOR_MERGE = .75;
//    private static final double MAX_HDIST_REQUIRED_FOR_MERGE = .05;
//
//}
