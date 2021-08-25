package compression;

import util.AlphaNumericString;

public abstract class CompressionScheme
{
    /**
     * Returns the unique id of the compression scheme
     */
    public abstract AlphaNumericString getId();
    public abstract AlphaNumericString generateFlags();
    public abstract void consumeFlags();
    public abstract byte[] compress(byte[] data);
    public abstract byte[] decompress(byte[] data);

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
        return switch (id)
                {
                    default -> new NoCompress();
                    case "z" -> new ZipScheme();
                };
    }
}
