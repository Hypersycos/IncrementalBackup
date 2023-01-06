package net.hypersycos.incrementalbackup.handlers;

import net.hypersycos.incrementalbackup.compression.CompressionScheme;
import net.hypersycos.incrementalbackup.engine.BackupPath;
import net.hypersycos.incrementalbackup.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class ITypeHandler
{
    /**
     * Combine will apply the updates from newData to oldData. oldData's buffer should be positioned at the end of
     * the current data. Similarly, after execution, the buffer's position should be at the end of the now-modified data.
     * @param oldData ByteBuffer containing the full data to modify
     * @param newData A byte-array containing the instructions for modification
     * @return a ByteBuffer containing the modified data (usually the same as oldData). Different if overflow occurs.
     */
    public abstract ByteBuffer combine(ByteBuffer oldData, byte[] newData);
    public int getInitBufferSize(int initDataLength)
    {
        return Math.min(initDataLength*100, 100*1024*1024);
    }
    //the buffer's position must be at the end of the file after combine.
    public byte[] combineAll(List<Path> files) throws IOException
    {
        if (files == null || files.size() == 0) return null;
        BackupPath initMeta = new BackupPath(files.get(0).getFileName().toString());
        byte[] initData = initMeta.getCompression().decompress(Files.readAllBytes(files.get(0)));
        if (files.size() == 1) return initData;

        ByteBuffer buffer = ByteBuffer.allocate(getInitBufferSize(initData.length));
        buffer.put(initData);
        for (Path file : files.subList(1, files.size()))
        {
            BackupPath meta = new BackupPath(file.getFileName().toString());
            buffer = combine(buffer, meta.getCompression().decompress(Files.readAllBytes(file)));
        }
        buffer.flip();
        byte[] toReturn = new byte[buffer.limit()];
        buffer.get(toReturn, 0, buffer.limit());
        return toReturn;
    }

    public boolean verify(byte[] oldData, byte[] diff, byte[] newData)
    {
        ByteBuffer buffer = ByteBuffer.allocate(getInitBufferSize(oldData.length));
        buffer.put(oldData);
        ByteBuffer combined = combine(buffer, diff);
        return this.verify(combined, ByteBuffer.wrap(newData));
    }

    protected boolean verify(ByteBuffer combined, ByteBuffer newData)
    {
        combined.flip();
        byte[] test = new byte[combined.limit()];
        combined.get(test, 0, combined.limit());
        return Arrays.equals(test, newData.array());
    }

    /**
     * getDifference returns the data to be written to a file, representing the additions of newData to oldData
     * @param oldData old copy of file in byte array. Cannot be null.
     * @param newData new copy of file in byte array
     * @return returns the difference, to be used in combine when restoring, and the net.hypersycos.incrementalbackup.compression scheme to use to store
     */
    public abstract Pair<byte[], CompressionScheme> getDifference(byte[] oldData, byte[] newData);
    public abstract CompressionScheme getInitCompression(byte[] data);
    public abstract Set<CompressionScheme> getCompressionSchemes();

    protected static ByteBuffer extend(ByteBuffer old)
    {
        ByteBuffer newBuffer = ByteBuffer.allocate(old.capacity() * 2);
        newBuffer.put(old);
        return newBuffer;
    }

    protected static boolean memcmp(byte[] a, int a_offset, byte[] b, int b_offset, int length)
    {
        if ((a == null) && (b == null))
        {
            return true;
        }
        if ((a == null) || (b == null) || a.length - a_offset != b.length - b_offset)
        {
            return false;
        }
        for (int i = 0; i < length; ++i, ++a_offset, ++b_offset)
        {
            if (a[a_offset] != b[b_offset])
            {
                return false;
            }
        }
        return true;
    }

    protected static byte[] bufferToTrimmedArray(ByteBuffer buffer)
    {
        buffer.flip();
        byte[] toReturn = new byte[buffer.limit()];
        buffer.get(toReturn, 0, buffer.limit());
        return toReturn;
    }

    protected static byte[] intDigitsToBytes(int i, int numDigits)
    {
        byte[] bytes = new byte[numDigits];
        for (int j = 0; j < numDigits; j++)
        {
            bytes[j] = (byte) (i >> 8*(numDigits-j-1));
        }
        return bytes;
    }
}
