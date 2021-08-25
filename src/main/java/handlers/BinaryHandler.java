package handlers;

import compression.CompressionScheme;
import compression.NoCompress;
import compression.ZipScheme;
import util.Pair;

import java.nio.ByteBuffer;
import java.util.Set;

public class BinaryHandler extends ITypeHandler
{
    final int block_size;
    static final int id_length = 4; //4 = max 1TiB, 3 4GiB, 2 16MiB, assuming block_size=256
    final int compression_threshold;

    //This handler naively splits the file into blocks of size block_size, and compares the differences between
    //the blocks. This will perform well on files with fixed positions and structures, and poorly on more dynamic files,
    //or files with many small updates scattered around.

    public BinaryHandler()
    {
        this.block_size = 256;
        this.compression_threshold = 1024;
    }

    public BinaryHandler(int block_size, int compression_threshold)
    {
        this.block_size = block_size;
        this.compression_threshold = compression_threshold;
    }

    @Override
    public ByteBuffer combine(ByteBuffer oldData, byte[] newData)
    {
        ByteBuffer newBuffer = ByteBuffer.wrap(newData);
        int num_removed = newBuffer.getInt(); //first 4 bytes represent number of blocks removed
        int final_block_size = newBuffer.getInt(); //next 4 bytes represent the size of the final block
        // useful in case the file size doesn't divide neatly into block_size and we don't touch the final block,
        // so we can't calculate from remaining()
        int length = (int)Math.ceil(oldData.position() / (float)block_size) - num_removed; //new file length, assuming no blocks added
        while (newBuffer.hasRemaining())
        {
            int block_id = 0;
            for (int i = 0; i < id_length; i++)
            {
                block_id += Byte.toUnsignedInt(newBuffer.get()) << 8*(id_length-i-1);
            }

            if (block_id >= length) length = block_id+1;

            int my_block_size = Math.min(newBuffer.remaining(), block_size);
            byte[] block = new byte[my_block_size];
            newBuffer.get(block, 0, my_block_size);
            oldData.put(block_id*block_size, block);
            oldData.position(0);
            //reset to position 0 so we index bytebuffer correctly
        }
        oldData.position((length-1)*block_size+final_block_size);
        return oldData;
    }

    @Override
    public Pair<byte[], CompressionScheme> getDifference(byte[] oldData, byte[] newData)
    {
        ByteBuffer diffs = ByteBuffer.allocate(Math.max(oldData.length,newData.length)*2 + 8);
        //this buffer should? always be big enough to avoid overflow
        ByteBuffer oldBuffer = ByteBuffer.wrap(oldData);
        ByteBuffer newBuffer = ByteBuffer.wrap(newData);
        int num_removed = 0; //assume either block size is the same or greater
        if (oldData.length > newData.length)
        { //if the older file is longer, compare the block counts of both
            int oldSize = (int)Math.ceil(oldData.length / (float)block_size);
            int newSize = (int)Math.ceil(newData.length / (float)block_size);
            num_removed = oldSize - newSize;
        }
        diffs.putInt(num_removed); //store number of removed blocks, so combine can correctly adjust size
        // and so there is a difference between e.g. nulled and removed.
        diffs.putInt(0); //value irrelevant, just reserving the space for final_block_size to be added.
        int i = 0;
        int final_block_size = block_size;
        while (newBuffer.hasRemaining() && oldBuffer.hasRemaining())
        { //comparing blocks of both files
            //final blocks can have different sizes, so must treat both separately
            int new_size = Math.min(block_size, newBuffer.remaining());
            final_block_size = new_size; //always set final_block_size, since the last block will be the last to set it
            byte[] newBlock = new byte[new_size+id_length];
            int old_size = Math.min(block_size, oldBuffer.remaining());
            byte[] oldBlock = new byte[old_size];
            newBuffer.get(newBlock, id_length, new_size);
            oldBuffer.get(oldBlock, 0, old_size);
            if (!memcmp(newBlock, id_length, oldBlock, 0, new_size))
            { //if the blocks are different, store block id
                for (int j = 0; j < id_length; j++)
                {
                    newBlock[j] = (byte) (i >> 8*(id_length-j-1));
                }
                //then store the new block
                diffs.put(newBlock);
            }
            i += 1;
        }
        while (newBuffer.hasRemaining())
        { //only occurs if new file contains more blocks
            int new_size = Math.min(block_size, newBuffer.remaining());
            final_block_size = new_size; //always set final_block_size, since the last block will be the last to set it
            byte[] newBlock = new byte[new_size+id_length];
            newBuffer.get(newBlock, id_length, new_size);
            // trivially, the block has "changed"
            for (int j = 0; j < id_length; j++)
            {
                newBlock[j] = (byte) (i >> 8*(id_length-j-1));
            }
            diffs.put(newBlock);
            i += 1;
        }
        //store eof position, then store final_block_size
        int temp = diffs.position();
        diffs.position(4);
        diffs.putInt(final_block_size);
        diffs.position(temp);
        //trim array to fit the file size exactly
        byte[] toReturn = bufferToTrimmedArray(diffs);
        //we compress above 1KiB
        if (toReturn.length >= compression_threshold)
        {
            return new Pair<>(toReturn, new ZipScheme());
        }
        else
        {
            return new Pair<>(toReturn, new NoCompress());
        }
    }

    @Override
    public Set<CompressionScheme> getCompressionSchemes()
    {
        return Set.of(new ZipScheme(), new NoCompress());
    }
}
