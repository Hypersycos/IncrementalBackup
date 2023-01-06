package net.hypersycos.incrementalbackup.compression;

import net.hypersycos.incrementalbackup.util.AlphaNumericString;

public class NoCompress extends CompressionScheme
{
    static AlphaNumericString empty = new AlphaNumericString("");

    @Override
    public AlphaNumericString getId()
    {
        return empty;
    }

    @Override
    public AlphaNumericString generateFlags()
    {
        return empty;
    }

    @Override
    public void consumeFlags() {}

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
