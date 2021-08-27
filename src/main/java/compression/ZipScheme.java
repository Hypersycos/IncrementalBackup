package compression;

import util.AlphaNumericString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
        try(ZipOutputStream zos = new ZipOutputStream(baos))
        {
            ZipEntry entry = new ZipEntry("data");
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try(ZipInputStream zis = new ZipInputStream(bais))
        {
            zis.getNextEntry();
            return zis.readAllBytes();
        }
    }
}
