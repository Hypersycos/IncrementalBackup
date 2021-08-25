package engine;

import compression.CompressionScheme;
import handlers.BinaryHandler;
import handlers.ITypeHandler;
import util.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SwitchingIncrementalBackup extends IncrementalBackup
{
    Map<String, ITypeHandler> FileHandlers = new HashMap<>();
    ITypeHandler defaultHandler = new BinaryHandler();

    public SwitchingIncrementalBackup(Path directory, Path backupPath)
    {
        super(directory, backupPath);
    }

    public void register(ITypeHandler handler, String... types)
    {
        for (String type : types)
        {
            FileHandlers.put(type, handler);
        }
    }

    private ITypeHandler getFileHandler(Path file) throws IOException
    {
        String type = Files.probeContentType(file);
        if (type == null)
        {
            String name = file.getFileName().toString();
            int lastIndex = name.lastIndexOf('.');
            if (lastIndex > -1 && name.length() > lastIndex+1)
            {
                type = name.substring(lastIndex+1);
            }
        }
        return FileHandlers.getOrDefault(type, defaultHandler);
    }

    @Override
    protected void restoreFile(Path restorePath, List<Path> files) throws IOException
    {
        ITypeHandler handler = getFileHandler(files.get(0));
        if (handler == null)
        {
            super.restoreFile(restorePath, files);
        }
        else
        {
            byte[] data = handler.combineAll(files);
            File file = restorePath.toFile();
            if (!file.exists() && !file.createNewFile()) throw new IOException("Unable to save "+restorePath);
            Files.write(restorePath, data);
        }
    }

    @Override
    protected void backupFile(Path backupPath, BackupPath name, Path file, List<Path> links) throws IOException
    {
        ITypeHandler handler = getFileHandler(file);
        if (handler == null)
        {
            super.backupFile(backupPath, name, file, links);
        }
        else
        {
            byte[] newData = Files.readAllBytes(file);
            if (links == null || links.size() == 0)
            {
                Files.write(backupPath, newData);
            }
            else
            {
                byte[] oldData = handler.combineAll(links);
                if (super.isDifferent(oldData, newData))
                {
                    Pair<byte[], CompressionScheme> data = handler.getDifference(oldData, newData);
                    if (data != null)
                    {
                        name.setCompression(data.second());
                        Files.write(backupPath.resolve(name.toName()), data.second().compress(data.first()));
                    }
                }
            }
        }
    }
}
