package net.hypersycos.incrementalbackup.compression;

import net.hypersycos.incrementalbackup.util.AlphaNumericString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ZipScheme extends CompressionScheme
{
    AlphaNumericString id = new AlphaNumericString("z");
    @Override
    public AlphaNumericString getId()
    {
        return id;
    }

    @Override
    public AlphaNumericString generateFlags()
    {
        return new AlphaNumericString("");
    }

    @Override
    public void consumeFlags()
    {

    }

    @Override
    public byte[] compress(byte[] data) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try(DeflaterOutputStream dos = new DeflaterOutputStream(baos))
        {
            dos.write(data);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try(InflaterInputStream zis = new InflaterInputStream(bais))
        {
            return readAllBytes(zis);
        }
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400; // 4KB
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                    outputStream.write(buf, 0, readLen);

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null) inputStream.close();
            else try {
                inputStream.close();
            } catch (IOException e) {
                exception.addSuppressed(e);
            }
        }
    }
}
