package net.hypersycos.incrementalbackup.engine;

import net.hypersycos.incrementalbackup.compression.CompressionScheme;
import net.hypersycos.incrementalbackup.compression.NoCompress;

public class BackupPath
{
    public int getMinorVersion()
    {
        return minorVersion;
    }

    public String getName()
    {
        return name;
    }

    public boolean isRemoved()
    {
        return isRemoved;
    }

    public CompressionScheme getCompression()
    {
        return compression;
    }

    private int minorVersion;
    private String name;
    private boolean isRemoved;

    public void setCompression(CompressionScheme compression)
    {
        this.compression = compression;
    }

    private CompressionScheme compression;

    /**
     * Converts filename to details
     * @throws NumberFormatException Thrown if characters up to first . are not an integer
     * @throws IndexOutOfBoundsException Thrown if file does not have at least three .s
     */
    public BackupPath(String fileName) throws NumberFormatException, IndexOutOfBoundsException
    {
        int firstDot = fileName.indexOf(".");
        minorVersion = Integer.parseInt(fileName.substring(0,firstDot));
        isRemoved = fileName.charAt(firstDot+1) == 'r';
        int nextDot = fileName.substring(firstDot+1).indexOf(".")+firstDot+1;
        if (isRemoved)
        {
            compression = new NoCompress();
        }
        else
        {
            int dash = fileName.indexOf("-");
            String id = fileName.substring(firstDot+1,dash);
            String flags = fileName.substring(dash+1,nextDot);
            compression = CompressionScheme.getScheme(id, flags);
        }
        name = fileName.substring(nextDot+1);
    }

    public BackupPath(int minorVersion, String name, boolean isRemoved)
    {
        this.minorVersion = minorVersion;
        this.name = name;
        this.isRemoved = isRemoved;
        this.compression = new NoCompress();
    }

    public BackupPath(){}

    public String toName()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(minorVersion);
        builder.append(".");
        if (isRemoved)
        {
            builder.append("r");
        }
        else
        {
            builder.append(compression.getId().get());
            builder.append("-");
            builder.append(compression.generateFlags().get());
        }
        builder.append(".");
        builder.append(name);
        return builder.toString();
    }
}
