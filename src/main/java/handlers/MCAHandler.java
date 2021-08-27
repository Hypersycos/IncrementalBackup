package handlers;

import compression.CompressionScheme;
import compression.NoCompress;
import compression.ZipScheme;
import util.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.ZipInputStream;

public class MCAHandler extends ITypeHandler
{
    BinaryHandler binaryHandler = new BinaryHandler();

    record ChunkLocation(int offset, byte sectorCount){
        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ChunkLocation that = (ChunkLocation) o;
            return offset == that.offset && sectorCount == that.sectorCount;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(offset, sectorCount);
        }
    }

    @Override
    public ByteBuffer combine(ByteBuffer oldData, byte[] newData)
    {
        Set<Integer> modified = new HashSet<>();
        ByteBuffer swap = ByteBuffer.allocate(oldData.capacity());
        ByteBuffer newBuffer = ByteBuffer.wrap(newData);
        Pair<ChunkLocation[], int[]> oldHeader = getHeader(oldData);
        int end = 0;
        while(newBuffer.hasRemaining())
        {
            int i = newBuffer.getInt();
            modified.add(i);
            ChunkLocation locationData = new ChunkLocation(newBuffer.getInt(), newBuffer.get());
            int timestamp = newBuffer.getInt();
            if (timestamp == oldHeader.second()[i])
            {
                Pair<Integer, byte[]> chunk = getChunk(i, oldHeader, oldData);
                if (locationData.sectorCount > 0)
                {
                    swap.putInt(locationData.offset * 4096, chunk.first());
                    swap.put(locationData.offset * 4096 + 4, chunk.second());
                    int chunk_end = locationData.offset + locationData.sectorCount;
                    if (chunk_end > end)
                    {
                        end = chunk_end;
                    }
                }
            }
            else if (locationData.sectorCount() > 0)
            {
                int chunk_length_bytes = newBuffer.getInt();
                byte[] old_chunk = getChunk(i, oldHeader, oldData).second();
                ByteBuffer temp = ByteBuffer.allocate(1024 * 1024 + 4); //chunk can't be bigger than 1MiB
                temp.put(old_chunk);
                byte[] diff = new byte[chunk_length_bytes];
                newBuffer.get(diff, 0, chunk_length_bytes);
                byte[] newChunk = bufferToTrimmedArray(binaryHandler.combine(temp, diff));
                swap.putInt(locationData.offset * 4096, newChunk.length);
                swap.put(locationData.offset * 4096 + 4, newChunk);
                int chunk_end = locationData.offset + locationData.sectorCount;
                if (chunk_end > end)
                {
                    end = chunk_end;
                }
            }
            for (int j = 0; j < 3; j++)
            {
                swap.put(i*4+j, (byte) (locationData.offset >> 8*(3-j-1)));
            }
            swap.put(i*4+3, locationData.sectorCount);
            swap.putInt(i*4+4096, timestamp);
        }
        for (int i = 0; i < 1024; i++)
        {
            if (modified.contains(i)) continue;
            int locationData = oldData.getInt(i*4);
            swap.putInt(i*4, locationData); //location data
            swap.putInt(i*4+4096, oldData.getInt(i*4+4096)); //timestamp
            if (locationData != 0)
            {
                Pair<Integer, byte[]> chunk = getChunk(i, oldHeader, oldData);
                swap.putInt(oldHeader.first()[i].offset * 4096, chunk.first());
                swap.put(oldHeader.first()[i].offset * 4096 + 4, chunk.second());
                int chunk_end = oldHeader.first()[i].offset + oldHeader.first()[i].sectorCount;
                if (chunk_end > end)
                {
                    end = chunk_end;
                }
            }
        }
        swap.position(end*4096);
        return swap;
    }

    @Override
    public int getInitBufferSize(int initDataLength)
    {
        return 16*1024*1024; //I've never seen a region file much above 8MiB, even in modded worlds.
    }

    private ChunkLocation[] getLocations(ByteBuffer buffer)
    {
        ChunkLocation[] locations = new ChunkLocation[1024];
        for (int i = 0; i < 1024; i++)
        {
            int location = 0;
            for (int j = 0; j < 3; j++)
            {
                location += Byte.toUnsignedInt(buffer.get()) << 8*(2-j);
            }
            byte sectorCount = buffer.get();
            locations[i] = new ChunkLocation(location, sectorCount);
        }
        return locations;
    }

    private int[] getTimeStamps(ByteBuffer buffer)
    {
        int[] timestamps = new int[1024];
        for (int i = 0; i < 1024; i++)
        {
            timestamps[i] = buffer.getInt();
        }
        return timestamps;
    }

    private Pair<ChunkLocation[], int[]> getHeader(ByteBuffer buffer)
    {
        buffer.position(0);
        return new Pair<>(getLocations(buffer), getTimeStamps(buffer));
    }

    private Pair<Integer, byte[]> getChunk(int i, Pair<ChunkLocation[], int[]> header, ByteBuffer buffer)
    {
        buffer.position(0);
        int offset = header.first()[i].offset * 4096;
        int length = buffer.getInt(offset);
        byte[] data = new byte[length];
        buffer.get(offset+4, data, 0, length);
        return new Pair<>(length, data);
    }

    @Override
    public Pair<byte[], CompressionScheme> getDifference(byte[] oldData, byte[] newData)
    {
        ByteBuffer oldBuffer = ByteBuffer.wrap(oldData);
        ByteBuffer newBuffer = ByteBuffer.wrap(newData);
        ByteBuffer diffs = ByteBuffer.allocate(Math.max(newData.length, oldData.length)*2);
        Pair<ChunkLocation[], int[]> oldHeader = getHeader(oldBuffer);
        Pair<ChunkLocation[], int[]> newHeader = getHeader(newBuffer);
        for (int i = 0; i < 1024; i++)
        {
            if (oldHeader.second()[i] != newHeader.second()[i] || !oldHeader.first()[i].equals(newHeader.first()[i]))
            {
                diffs.putInt(i);
                diffs.putInt(newHeader.first()[i].offset());
                diffs.put(newHeader.first()[i].sectorCount());
                diffs.putInt(newHeader.second()[i]);
                if (newHeader.first()[i].sectorCount != 0 && oldHeader.second()[i] != newHeader.second()[i])
                {
                    Pair<Integer, byte[]> oldChunk = getChunk(i, oldHeader, oldBuffer);
                    Pair<Integer, byte[]> newChunk = getChunk(i, newHeader, newBuffer);
                    byte[] chunk_diff = binaryHandler.getDifference(oldChunk.second(), newChunk.second()).first();
                    diffs.putInt(chunk_diff.length);
                    diffs.put(chunk_diff);
                }
            }
        }
        //chunk data is already compressed, and so doesn't compress well.
        return new Pair<>(bufferToTrimmedArray(diffs), new NoCompress());
        /*byte[] toReturn = bufferToTrimmedArray(diffs);
        //we compress above 1KiB
        if (toReturn.length >= binaryHandler.compression_threshold)
        {
            return new Pair<>(toReturn, new ZipScheme());
        }
        else
        {
            return new Pair<>(toReturn, new NoCompress());
        }*/
    }

    @Override
    public CompressionScheme getInitCompression(byte[] data)
    {
        return new ZipScheme();
    }

    @Override
    public Set<CompressionScheme> getCompressionSchemes()
    {
        return binaryHandler.getCompressionSchemes();
    }

    private byte[] decompressChunk(byte[] chunk, byte decompressionType)
    {
        //1 == gzip, 2 == zlib, 3 == uncompressed
        if (decompressionType == 1)
        {
            throw new IllegalArgumentException("Gzipped chunk");
        }
        else if (decompressionType == 2)
        {
            ByteArrayInputStream bais = new ByteArrayInputStream(chunk);
            try (ZipInputStream zis = new ZipInputStream(bais))
            {
                zis.getNextEntry();
                return zis.readAllBytes();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else if (decompressionType == 3)
        {
            return chunk;
        }
        throw new IllegalArgumentException("Unknown decompression type");
    }
}
