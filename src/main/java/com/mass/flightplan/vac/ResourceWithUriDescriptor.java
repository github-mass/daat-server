package com.mass.flightplan.vac;

import lombok.NonNull;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

class ResourceWithUriDescriptor
    implements Resource
{
    private final Resource delegate;
    private final URI uri;

    public ResourceWithUriDescriptor(@NonNull Resource delegate, @NonNull URI uri){
        this.delegate = delegate;
        this.uri = uri;
    }

    @Override
    public URL getURL()
        throws IOException
    {
        return uri.toURL();
    }

    @Override
    public URI getURI()
    {
        return uri;
    }

    @Override
    public boolean exists() {
        return delegate.exists();
    }

    @Override
    public File getFile()
        throws IOException
    {
        return delegate.getFile();
    }

    @Override
    public long contentLength()
        throws IOException
    {
        return delegate.contentLength();
    }

    @Override
    public long lastModified()
        throws IOException
    {
        return delegate.lastModified();
    }

    @Override
    public Resource createRelative(String relativePath)
        throws IOException
    {
        return delegate.createRelative(relativePath);
    }

    @Override
    public String getFilename() {
        return delegate.getFilename();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public InputStream getInputStream()
        throws IOException
    {
        return delegate.getInputStream();
    }

    @Override
    public boolean isReadable() {
        return delegate.isReadable();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isFile() {
        return delegate.isFile();
    }

    @Override
    public ReadableByteChannel readableChannel()
        throws IOException
    {
        return delegate.readableChannel();
    }

    @Override
    public byte[] getContentAsByteArray()
        throws IOException
    {
        return delegate.getContentAsByteArray();
    }

    @Override
    public String getContentAsString(Charset charset)
        throws IOException
    {
        return delegate.getContentAsString(charset);
    }

    @Override
    public String toString() {
        return delegate.toString() + " from " + uri.toString();
    }
}
