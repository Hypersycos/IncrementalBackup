package compression;

import util.AlphaNumericString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.*;

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
            return zis.readAllBytes();
        }
    }
}
