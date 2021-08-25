package compression;

import util.AlphaNumericString;

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
    public byte[] compress(byte[] data)
    {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data)
    {
        return data;
    }
}
