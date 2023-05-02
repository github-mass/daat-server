package com.mass.flightplan.util;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class InvalidXmlCharFilterReader
    extends FilterReader
{
    public InvalidXmlCharFilterReader(Reader in) {
        super(in);
    }

    @Override
    public int read(char[] cbuf, int off, int len)
        throws IOException
    {
        int ret = super.read(cbuf, off, len);

        for(int ii = 0; ii < ret; ){
            if(isInvalidChar(cbuf[off+ii])){
                System.arraycopy(cbuf, off+ii+1, cbuf, off+ii, --ret - ii);
            } else {
                ii++;
            }
        }

        return ret;
    }

    private static boolean isInvalidChar(char c){
        // https://en.wikipedia.org/wiki/Valid_characters_in_XML
        // not all of them; I'm having issues with C0 chars.

        return inRange(c, '\u0001', '\u0008')
            || inRange(c, '\u000b', '\u000c')
            || inRange(c, '\u000e', '\u001f')
        ;
    }

    private static boolean inRange(char c, char fromInclusive, char toInclusive){
        return c >= fromInclusive && c <= toInclusive;
    }
}
