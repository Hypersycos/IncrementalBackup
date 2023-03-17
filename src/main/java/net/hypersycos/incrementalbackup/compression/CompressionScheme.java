package net.hypersycos.incrementalbackup.compression;

import net.hypersycos.incrementalbackup.util.AlphaNumericString;

import java.io.IOException;
import java.util.Objects;

public abstract class CompressionScheme
{
    /**
     * Returns the unique id of the net.hypersycos.incrementalbackup.compression scheme
     */
    public abstract AlphaNumericString getId();
    public abstract AlphaNumericString generateFlags();
    public abstract void consumeFlags();
    public abstract byte[] compress(byte[] data) throws IOException;
    public abstract byte[] decompress(byte[] data) throws IOException;

    @Override
    public boolean equals(Object obj)
    {
        return obj.getClass() == getClass() && obj.hashCode() == hashCode();
    }

    @Override
    public int hashCode()
    {
        return getId().get().hashCode();
    }

    public static CompressionScheme getScheme(String id, String flags)
    {
        if (Objects.equals(id, "z"))
        {
            return new ZipScheme();
        }
        else
        {
            return new NoCompress();
        }
    }
}
