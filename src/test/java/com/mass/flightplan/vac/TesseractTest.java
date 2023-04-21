package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.PIXA;
import org.bytedeco.tesseract.TessBaseAPI;
import org.bytedeco.tesseract.Tesseract;
import org.bytedeco.tesseract.global.tesseract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.bytedeco.leptonica.global.lept.pixDestroy;
import static org.bytedeco.leptonica.global.lept.pixRead;
import static org.bytedeco.tesseract.global.tesseract.RIL_BLOCK;

@Log4j2
public class TesseractTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "./vac-atlas/test/pdf-image-2.png"
    })
    void runOcr(String imagePath)
        throws Exception
    {
        /*
            Run with ENV:
                TESSDATA_PREFIX=//wsl.localhost/Debian/usr/share/tesseract-ocr/4.00/tessdata
            (or equivalent)
         */

        TessBaseAPI tessApi = new TessBaseAPI();

        int code; if(0 != (code=tessApi.Init(null, "eng+fra"))){
            throw new IllegalStateException("Could not initialise tesseract API: " + code);
        }

        final PIX image = pixRead(imagePath);
        tessApi.SetPageSegMode(tesseract.PSM_SPARSE_TEXT);

        tessApi.SetImage(image);
        tessApi.SetSourceResolution(400);

        final int recog = tessApi.Recognize(null);
        if(recog != 0){
            pixDestroy(image);
            tessApi.End();
            throw new IllegalStateException("recognise failed: " + recog);
        }

        final int[] left = new int[1], top = new int[1], bottom = new int[1], right = new int[1];
        try(var it = tessApi.GetIterator()){
            it.Begin();
            while(it.Next(RIL_BLOCK)){
                it.BoundingBox(RIL_BLOCK, 5, left, top, right, bottom);
                try(var txt = it.GetUTF8Text(RIL_BLOCK)){

                    System.out.printf("Got BLOCK at (%d, %d) -> (%d, %d):%n%s", top[0], left[0], bottom[0], right[0], txt.getString());
                }
            }
        }

        tessApi.End();
        pixDestroy(image);
    }

}
