package com.texto.emailplatform.observability;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts response bytes without retaining the body.
 */
public final class CountingHttpServletResponse extends HttpServletResponseWrapper {

    private final AtomicLong count = new AtomicLong();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private long declaredLength = -1;

    public CountingHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setContentLength(int len) {
        this.declaredLength = len;
        super.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        this.declaredLength = len;
        super.setContentLengthLong(len);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter already called");
        }
        if (outputStream == null) {
            outputStream = new CountingServletOutputStream(super.getOutputStream(), count);
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream already called");
        }
        if (writer == null) {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            writer = new PrintWriter(new OutputStreamWriter(
                    new CountingServletOutputStream(super.getOutputStream(), count),
                    charset
            ));
        }
        return writer;
    }

    public long byteCount() {
        if (writer != null) {
            writer.flush();
        }
        long written = count.get();
        if (written > 0) {
            return written;
        }
        return Math.max(declaredLength, 0);
    }

    private static final class CountingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;
        private final AtomicLong count;

        private CountingServletOutputStream(ServletOutputStream delegate, AtomicLong count) {
            this.delegate = delegate;
            this.count = count;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count.incrementAndGet();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count.addAndGet(len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
