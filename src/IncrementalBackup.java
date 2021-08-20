import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class IncrementalBackup
{
    private static final String removedString = "removed";
    private Path backupPath;
    private int fullBackupSequence = 0;
    private int incrementalBackupSequence = 0;
    private Path directory;
    private Set<Path> trackedFiles = new HashSet<>();

    public synchronized void restore(int fullSequence, int incrementalSequence, Path restoreDirectory) throws IOException
    {
        restoreDirectory(backupPath, fullSequence, incrementalSequence, restoreDirectory);
    }

    private class Pair<X, Y> {
        public final X x;
        public final Y y;
        public Pair(X x, Y y)
        {
            this.x = x;
            this.y = y;
        }
    }

    private class Triple<X, Y, Z> {
        public final X x;
        public final Y y;
        public final Z z;
        public Triple(X x, Y y, Z z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private Triple<String, Integer, Integer> getBackupFileDetails(File file)
    {
        String fullName = file.getName();
        String fileName;
        int major;
        int minor;

        int lastDot = fullName.lastIndexOf('.');
        if (fullName.substring(lastDot + 1).equals(removedString))
        { //expect two more
            int secondLast = fullName.substring(0, lastDot).lastIndexOf('.');
            minor = Integer.parseInt(fullName.substring(secondLast));
            int thirdLast = fullName.substring(0, secondLast).lastIndexOf('.');
            major = Integer.parseInt(fullName.substring(thirdLast+1,lastDot));
            fileName = fullName.substring(0, thirdLast);
        }
        else
        {
            minor = Integer.parseInt(fullName.substring(lastDot));
            int secondLast = fullName.substring(0, lastDot).lastIndexOf('.');
            major = Integer.parseInt(fullName.substring(secondLast+1,lastDot));
            fileName = fullName.substring(0, secondLast);
        }
        return new Triple<>(fileName, major, minor);
    }

    /**
     * Recursively restores up a directory
     * @param directory Directory being restored from
     * @param restoreDirectory Directory to copy to
     * @throws NoSuchAlgorithmException
     * @throws NullPointerException
     */
    private void restoreDirectory(Path directory, int fullSequence, int incrementalSequence, Path restoreDirectory) throws NullPointerException, IOException
    {
        Set<Path> directories = new HashSet<>();
        Map<String, Pair<Integer, File>> files = new HashMap<>();
        for (File file : Objects.requireNonNull(directory.toFile().listFiles()))
        {
            Path path = file.toPath();
            if (file.isDirectory())
            {
                directories.add(path);
            }
            else
            {
                Triple<String, Integer, Integer> details = getBackupFileDetails(file);
                if (details.y != fullSequence || details.z > incrementalSequence) continue;
                if (files.containsKey(details.x))
                {
                    if (files.get(details.x).x < details.z)
                    {
                        files.put(details.x, new Pair<>(details.z, file));
                    }
                }
                else
                {
                    files.put(details.x, new Pair<>(details.z, file));
                }
            }
        }
        for (String name : files.keySet())
        {
            File file = files.get(name).y;
            if (file.getName().substring(file.getName().lastIndexOf('.'+1)).equals(removedString)) continue;

            Path newFile = restoreDirectory.resolve(backupPath.relativize(directory)+name);
            if (!Files.exists(newFile.getParent()))
            {
                if (!newFile.getParent().toFile().mkdirs())
                {
                    throw new IOException("Unable to create "+newFile.getParent().toString());
                }
            }
            Files.copy(file.toPath(), newFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Performs a full backup, copying all files, incrementing fullBackupSequence and resetting incrementalBackupSequence
     * @throws NoSuchAlgorithmException
     */
    public synchronized void performFullBackup() throws NoSuchAlgorithmException
    {
        fullBackupSequence++;
        incrementalBackupSequence = 0;
        Set<Path> newTrackedFiles = Collections.synchronizedSet(new HashSet<>());
        Set<Exception> exceptions = Collections.synchronizedSet(new HashSet<>());
        backupDirectory(directory, newTrackedFiles, exceptions, true);
        trackedFiles = newTrackedFiles;
    }

    /**
     * Performs an incremental backup, copying only files which have changed, incrementing incrementalBackupSequence
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public synchronized void performIncrementalBackup() throws NoSuchAlgorithmException, IOException
    {
        incrementalBackupSequence++;
        Set<Path> newTrackedFiles = Collections.synchronizedSet(new HashSet<>());
        Set<Exception> exceptions = Collections.synchronizedSet(new HashSet<>());
        backupDirectory(directory, newTrackedFiles, exceptions, false);
        for (Path path : trackedFiles)
        {
            if (!newTrackedFiles.contains(path))
            {
                //instead of creating an empty file, create a unique marker for a removed file.
                //Is the distinction ever useful? I don't know, but maybe.
                getBackupParentPath(path).resolve(path.getFileName()+"."+fullBackupSequence+"."+incrementalBackupSequence+removedString).toFile().createNewFile();
            }
        }
        trackedFiles = newTrackedFiles;

        if (exceptions.size() > 0)
        {
            for (Exception e : exceptions)
            {
                System.err.println(e.toString());
            }
        }
    }

    /**
     * Recursively backs up a directory, keeping track of all existing files and any exceptions
     * @param directory Directory to back up
     * @param trackedFiles Synchronised list of all files backed up
     * @param failures Synchronised list of all expected exceptions
     * @param isFullBackup Is this a full or incremental backup?
     * @throws NoSuchAlgorithmException
     * @throws NullPointerException
     */
    private void backupDirectory(Path directory, Set<Path> trackedFiles, Set<Exception> failures, boolean isFullBackup) throws NoSuchAlgorithmException, NullPointerException
    {
        for (File file : Objects.requireNonNull(directory.toFile().listFiles()))
        {
            Path path = file.toPath();
            if (file.isDirectory())
            {
                backupDirectory(path, trackedFiles, failures, isFullBackup);
            }
            else
            {
                try
                {
                    if (isFullBackup)
                    {
                        copyFile(path);
                    }
                    else
                    {
                        backupFile(path);
                    }
                    trackedFiles.add(path);
                }
                catch (IOException e)
                {
                    failures.add(e);
                }
            }
        }
    }

    /**
     * If a file has changed, then save a copy.
     * @param file File to backup
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    protected void backupFile(Path file) throws NoSuchAlgorithmException, IOException
    {
        File backup = getLatestBackup(file);
        if (hasChanged(backup, file.toFile()))
        { //we need to save
            copyFile(file);
        }
    }

    /**
     * Save a copy of a file
     * @param file File to backup
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    private void copyFile(Path file) throws IOException
    {
        Path parentDirectory = getBackupParentPath(file);
        Path newFile = parentDirectory.resolve(file.getFileName()+"."+fullBackupSequence+"."+incrementalBackupSequence);
        if (!Files.exists(parentDirectory))
        {
            if (!parentDirectory.toFile().mkdirs())
            {
                throw new IOException("Unable to create "+parentDirectory.toString());
            }
        }
        Files.copy(file, newFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Returns the latest backup file for a given file
     * @param path path to file
     * @return File of the latest backup
     */
    private File getLatestBackup(Path path)
    {
        File[] backups = getBackupsFromPath(path);
        File latest = null;
        int bestNumber = -1;
        for (File file : backups)
        {
            int index = file.getName().lastIndexOf('.');
            int incSequenceNumber = Integer.parseInt(file.getName().substring(index + 1));
            if (incSequenceNumber > bestNumber)
            {
                bestNumber = incSequenceNumber;
                latest = file;
            }
        }
        return latest;
    }

    /**
     * Returns the latest backup file for a given file after maxFull (but before the next full backup)
     * @param path path to file
     * @param maxFull The full backup number to use
     * @return File of the latest backup
     */
    private File getLatestBackup(Path path, int maxFull)
    {
        File[] backups = getBackupsFromPath(path, maxFull);
        File latest = null;
        int bestNumber = -1;
        for (File file : backups)
        {
            int index = file.getName().lastIndexOf('.');
            int incSequenceNumber = Integer.parseInt(file.getName().substring(index + 1));
            if (incSequenceNumber > bestNumber)
            {
                bestNumber = incSequenceNumber;
                latest = file;
            }
        }
        return latest;
    }

    /**
     * Returns the latest backup file for a given file, between maxFull and maxInc
     * @param path path to file
     * @param maxFull The full backup number to use
     * @param maxInc The latest incremental backup to use
     * @return File of the latest backup
     */
    private File getLatestBackup(Path path, int maxFull, int maxInc)
    {
        File[] backups = getBackupsFromPath(path, maxFull);
        File latest = null;
        int bestNumber = -1;
        for (File file : backups)
        {
            int index = file.getName().lastIndexOf('.');
            int incSequenceNumber = Integer.parseInt(file.getName().substring(index + 1));
            if (incSequenceNumber <= maxInc && incSequenceNumber > bestNumber)
            {
                bestNumber = incSequenceNumber;
                latest = file;
            }
        }
        return latest;
    }

    /**
     * Obtains the parent directory of the file in the backup directory
     * @param path path to file
     * @return parent directory of path in backupPath
     */
    private Path getBackupParentPath(Path path)
    {
        return backupPath.resolve(directory.relativize(path.getParent()));
    }

    /**
     * Returns all backup copies since the last full backup for a given file
     * @param path path to file
     * @return Array containing all relevant backup files
     */
    private File[] getBackupsFromPath(Path path)
    {
        Path parentPath = getBackupParentPath(path);
        String fileName = path.getFileName().toString();
        return parentPath.toFile().listFiles((dir, name) -> name.matches(Pattern.quote(fileName + "." + fullBackupSequence + ".") + "[0-9]+"));
    }

    /**
     * Returns all backup copies since the maxFull full backup for a given file
     * @param path path to file
     * @param maxFull full backup to use
     * @return Array containing all relevant backup files
     */
    private File[] getBackupsFromPath(Path path, int maxFull)
    {
        Path parentPath = getBackupParentPath(path);
        String fileName = path.getFileName().toString();
        return parentPath.toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.matches(Pattern.quote(fileName+"."+maxFull+".")+"[0-9]+");
            }
        });
    }

    /**
     * Compares two versions of a file to see whether there has been any changes
     * @param oldFile The old copy of the file
     * @param newFile The new copy of the file
     * @return True if different (or unable to read oldFile), False if not
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */

    protected static boolean hasChanged(File oldFile, File newFile) throws NoSuchAlgorithmException, IOException
    {
        byte[] oldHash;
        try
        {
            oldHash = generateMD5(Files.readAllBytes(oldFile.toPath()));
        }
        catch (IOException e)
        { //Assume if we can't access the old file, then it doesn't exist (newFile was created since our last backup).
            return true;
        }
        byte[] newHash = generateMD5(Files.readAllBytes(newFile.toPath()));
        return !Arrays.equals(oldHash, newHash);
    }

    /**
     * Generates an MD5 checksum as a String.
     * @param bytes The data being checksummed.
     * @return Hex string of the checksum value.
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    private static byte[] generateMD5(byte[] bytes) throws NoSuchAlgorithmException, IOException
    {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.update(bytes);

        return messageDigest.digest();
    }

}
