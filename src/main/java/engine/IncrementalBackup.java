package engine;

import util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class IncrementalBackup
{
    private static final String removedString = "removed";
    private static final String protectedFile = "journal";
    private final Path backupPath;
    private int fullBackupSequence = 0;
    private int incrementalBackupSequence = 0;
    private final Path directory;
    private Set<Path> trackedFiles = new HashSet<>();

    public IncrementalBackup(Path directory, Path backupPath)
    {
        this.backupPath = backupPath;
        this.directory = directory;
        File journal = backupPath.resolve(protectedFile).toFile();
        try (Scanner journalScanner = new Scanner(journal))
        {
            fullBackupSequence = Integer.parseInt(journalScanner.nextLine());
            incrementalBackupSequence = Integer.parseInt(journalScanner.nextLine());
            while (journalScanner.hasNextLine())
            {
                trackedFiles.add(Paths.get(journalScanner.nextLine()));
            }
        }
        catch (FileNotFoundException | NumberFormatException | NoSuchElementException e)
        {
            //TODO: search for highest numbers
            //TODO: get tracked files
        }

    }

    /**
     * Writes backup journal
     * @throws IOException Thrown if unable to create temporary file, or unable to overwrite journal
     */
    private void writeJournal() throws IOException
    {
        File tempJournal = File.createTempFile("tempJournal",".tmp");
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(tempJournal)))
        {
            bw.write(String.valueOf(fullBackupSequence));
            bw.newLine();
            bw.write(String.valueOf(incrementalBackupSequence));
            bw.newLine();
            for (Path path : trackedFiles)
            {
                bw.write(path.toString());
                bw.newLine();
            }
        }
        Files.copy(tempJournal.toPath(), backupPath.resolve(protectedFile), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore() throws IOException
    {
        restore(fullBackupSequence, incrementalBackupSequence, directory);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore(Path restorePath) throws IOException
    {
        restore(fullBackupSequence, incrementalBackupSequence, restorePath);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore(int fullSequence) throws IOException
    {
        restore(fullSequence, Integer.MAX_VALUE, directory);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore(Path restorePath, int fullSequence) throws IOException
    {
        restore(fullSequence, Integer.MAX_VALUE, restorePath);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore(int fullSequence, int incrementalSequence) throws IOException
    {
        restore(fullSequence, incrementalSequence, directory);
    }

    /**
     * Restores version fullSequence.incrementalSequence to restorePath
     * @throws IOException Thrown if unable to copy a file
     */
    public synchronized void restore(int fullSequence, int incrementalSequence, Path restorePath) throws IOException
    {
        restoreDirectory(backupPath.resolve(String.valueOf(fullSequence)), fullSequence, incrementalSequence, restorePath);
    }

    protected void restoreFile(Path restorePath, List<Path> files) throws IOException
    {
        Files.copy(files.get(files.size()-1), restorePath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Recursively restores up a directory
     * @param directory Directory being restored from
     * @param restorePath Directory to copy to
     * @throws NullPointerException Thrown if directory doesn't exist
     * @throws IOException Thrown if unable to copy a file
     */
    private void restoreDirectory(Path directory, int fullSequence, int incrementalSequence, Path restorePath) throws NullPointerException, IOException
    {
        Set<Path> directories = new HashSet<>();
        Map<String, List<Pair<BackupPath,Path>>> files = new HashMap<>();
        Path realPath = getRealPath(directory, fullSequence, restorePath);
        if (!realPath.toFile().exists() && !realPath.toFile().mkdirs()){
            throw new IOException("Unable to make "+realPath);
        }
        for (File file : Objects.requireNonNull(directory.toFile().listFiles()))
        {
            Path path = file.toPath();
            if (file.isDirectory())
            {
                directories.add(path);
            }
            else
            {
                if (file.getName().equals(protectedFile)) continue;
                BackupPath details;
                try
                {
                    details = new BackupPath(file.getName());
                }
                catch (NumberFormatException | IndexOutOfBoundsException e)
                {
                    continue;
                }
                if (details.getMinorVersion() > incrementalSequence) continue;

                if (!files.containsKey(details.getName()))
                {
                    files.put(details.getName(), new ArrayList<>());
                }
                files.get(details.getName()).add(new Pair<>(details, file.toPath()));
            }
        }
        for (String name : files.keySet())
        {
            files.get(name).sort(Comparator.comparing((pair) -> pair.first().getMinorVersion() - (pair.first().isRemoved() ? 0.5 : 0)));
            List<Path> toPass = new ArrayList<>();
            for (Pair<BackupPath, Path> entry : files.get(name))
            {
                if (entry.first().isRemoved())
                {
                    toPass.clear();
                }
                else
                {
                    toPass.add(entry.second());
                }
            }
            if (toPass.size() > 0) restoreFile(realPath.resolve(name), toPass);
        }
        for (Path subDirectory : directories)
        {
            restoreDirectory(subDirectory, fullSequence, incrementalSequence, restorePath);
        }
    }

    /**
     * Performs a full backup, copying all files, incrementing fullBackupSequence and resetting incrementalBackupSequence
     * @throws IOException If unable to write a new journal
     */
    public synchronized void performFullBackup() throws IOException
    {
        fullBackupSequence++;
        incrementalBackupSequence = 0;
        Set<Path> newTrackedFiles = Collections.synchronizedSet(new HashSet<>());
        Set<Exception> exceptions = Collections.synchronizedSet(new HashSet<>());
        backupDirectory(directory, newTrackedFiles, exceptions, true, null);
        trackedFiles = newTrackedFiles;
        writeJournal();
    }

    /**
     * Performs an incremental backup, copying only files which have changed, incrementing incrementalBackupSequence
     * @throws IOException Thrown if unable to write journal
     */
    public synchronized void performIncrementalBackup() throws IOException
    {
        incrementalBackupSequence++;
        Set<Path> newTrackedFiles = Collections.synchronizedSet(new HashSet<>());
        Set<Exception> exceptions = Collections.synchronizedSet(new HashSet<>());
        backupDirectory(directory, newTrackedFiles, exceptions, false, generateBackupLinks());
        for (Path path : trackedFiles)
        {
            if (!newTrackedFiles.contains(path))
            {
                //instead of creating an empty file, create a unique marker for a removed file.
                //Is the distinction ever useful? I don't know, but maybe.
                BackupPath backupPath = new BackupPath(incrementalBackupSequence, path.getFileName().toString(), true);
                String name = backupPath.toName();
                try
                {
                    if (!getBackupParentPath(path).resolve(name).toFile().createNewFile())
                        exceptions.add(new IOException("Unable to create "+name));
                }
                catch (IOException e)
                {
                    exceptions.add(new IOException("Unable to create "+name));
                }
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
        writeJournal();
    }

    /**
     * Recursively backs up a directory, keeping track of all existing files and any exceptions
     * @param directory Directory to back up
     * @param trackedFiles Synchronised list of all files backed up
     * @param failures Synchronised list of all expected exceptions
     * @param isFullBackup Is this a full or incremental backup?
     * @throws NullPointerException Thrown if directory doesn't exist
     */
    private void backupDirectory(Path directory, Set<Path> trackedFiles, Set<Exception> failures, boolean isFullBackup, Map<Path, List<Path>> links) throws NullPointerException
    {
        Path backupDir = getBackupPath(directory);
        if (!Files.exists(backupDir) && !backupDir.toFile().mkdirs())
        {
            failures.add(new IOException("Unable to create "+backupDir));
            return;
        }
        for (File file : Objects.requireNonNull(directory.toFile().listFiles()))
        {
            Path path = file.toPath();
            if (file.isDirectory())
            {
                backupDirectory(path, trackedFiles, failures, isFullBackup, links);
            }
            else
            {
                try
                {
                    BackupPath backupName = new BackupPath(incrementalBackupSequence, path.getFileName().toString(), false);
                    if (isFullBackup)
                    {
                        backupFile(backupDir, backupName, path, null);
                    }
                    else
                    {
                        backupFile(backupDir, backupName, path, links.get(path));
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
     * @throws IOException Thrown if unable to make backup file
     */
    protected void backupFile(Path backupLocation, BackupPath name, Path file, List<Path> links) throws IOException
    {
        if (hasChanged(links, file.toFile()))
        { //we need to save
            copyFile(file, backupLocation, name);
        }
    }

    /**
     * Save a copy of a file
     * @param file File to backup
     * @throws IOException Thrown if unable to make backup file
     */
    private void copyFile(Path file, Path newLocation, BackupPath name) throws IOException
    {
        Files.copy(file, newLocation.resolve(name.toName()), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Returns the latest backup file for a given file
     * @param path path to file
     * @return File of the latest backup
     */
    private File getLatestBackup(Path path)
    {
        return getLatestBackup(path, fullBackupSequence, Integer.MAX_VALUE);
    }

    /**
     * Returns the latest backup file for a given file after maxFull (but before the next full backup)
     * @param path path to file
     * @param maxFull The full backup number to use
     * @return File of the latest backup
     */
    private File getLatestBackup(Path path, int maxFull)
    {
        return getLatestBackup(path, maxFull, Integer.MAX_VALUE);
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
    { // backupPath/fullBackupSequence/path
        return getBackupParentPath(path, fullBackupSequence);
    }

    /**
     * Obtains the parent directory of the file in the backup directory, for the fullBackupSequence-th full backup
     * @param path path to file
     * @return parent directory of path in backupPath
     */
    private Path getBackupParentPath(Path path, int fullBackupSequence)
    { // backupPath/fullBackupSequence/path
        return getBackupPath(path.getParent(), fullBackupSequence);
    }

    private Path getBackupPath(Path path)
    {
        return getBackupPath(path, fullBackupSequence);
    }

    private Path getBackupPath(Path path, int fullBackupSequence)
    {
        return backupPath.resolve(String.valueOf(fullBackupSequence)).resolve(directory.relativize(path));
    }

    private Map<Path, List<Path>> generateBackupLinks()
    {
        Map<Path, List<Pair<BackupPath, Path>>> unordered = generateBackupLinks(fullBackupSequence);
        Map<Path, List<Path>> sorted = new HashMap<>();
        for (Path path : unordered.keySet())
        {
            List<Pair<BackupPath, Path>> files = unordered.get(path);
            files.sort(Comparator.comparing((pair) -> pair.first().getMinorVersion() - (pair.first().isRemoved() ? 0.5 : 0)));
            List<Path> toPass = new ArrayList<>();
            for (Pair<BackupPath, Path> entry : files)
            {
                if (entry.first().isRemoved())
                {
                    toPass.clear();
                }
                else
                {
                    toPass.add(entry.second());
                }
            }
            sorted.put(path, toPass);
        }
        return sorted;
    }

    private Map<Path, List<Pair<BackupPath, Path>>> generateBackupLinks(int fullBackupSequence)
    {
        Map<Path, List<Pair<BackupPath, Path>>> toReturn = new HashMap<>();
        addBackupLinks(backupPath.resolve(String.valueOf(fullBackupSequence)), toReturn);
        return toReturn;
    }

    private Path getRealPath(Path dir, int fullBackupSequence)
    {
        return getRealPath(dir, fullBackupSequence, directory);
    }

    private Path getRealPath(Path dir, int fullBackupSequence, Path mainDir)
    {
        Path relDir = backupPath.resolve(String.valueOf(fullBackupSequence)).relativize(dir);
        return mainDir.resolve(relDir);
    }

    private void addBackupLinks(Path dir, Map<Path, List<Pair<BackupPath, Path>>> links)
    {
        Path realDir = getRealPath(dir, fullBackupSequence);
        for (File file : Objects.requireNonNull(dir.toFile().listFiles()))
        {
            if (file.isDirectory())
            {
                addBackupLinks(file.toPath(), links);
            }
            else
            {
                BackupPath details = new BackupPath(file.getName());
                if (details.getMinorVersion() >= incrementalBackupSequence) continue;
                Path filePath = realDir.resolve(details.getName());
                if (!links.containsKey(filePath))
                {
                    links.put(filePath, new ArrayList<>());
                }
                links.get(filePath).add(new Pair<>(details, file.toPath()));
            }
        }
    }

    /**
     * Returns all backup copies since the last full backup for a given file
     * @param path path to file
     * @return Array containing all relevant backup files
     */
    private File[] getBackupsFromPath(Path path)
    {
        return getBackupsFromPath(path, fullBackupSequence);
    }

    /**
     * Returns all backup copies since the maxFull-th full backup for a given file
     * @param path path to file
     * @param maxFull full backup to use
     * @return Array containing all relevant backup files
     */
    private File[] getBackupsFromPath(Path path, int maxFull)
    {
        Path parentPath = getBackupParentPath(path, maxFull);
        String fileName = path.getFileName().toString();
        return parentPath.toFile().listFiles((dir, name) -> name.matches("[0-9]+\\.r?\\."+Pattern.quote(fileName)));
    }

    /**
     * Compares two versions of a file to see whether there has been any changes
     * @param oldFiles The collection of backups for the file. May be null or have length 0.
     * @param newFile The new copy of the file
     * @return True if different (or unable to read oldFile), False if not
     * @throws IOException Thrown if unable to read newfile
     */
    protected boolean hasChanged(List<Path> oldFiles, File newFile) throws IOException
    {
        if (oldFiles == null || oldFiles.size() == 0) return true;
        File oldFile = oldFiles.get(oldFiles.size()-1).toFile();
        byte[] oldHash;
        try
        {
            oldHash = generateMD5(Files.readAllBytes(oldFile.toPath()));
        }
        catch (IOException | NullPointerException e)
        { //Assume if we can't access the old file, then it doesn't exist (newFile was created since our last backup).
            return true;
        }
        byte[] newHash = generateMD5(Files.readAllBytes(newFile.toPath()));
        return !Arrays.equals(oldHash, newHash);
    }

    protected boolean isDifferent(byte[] oldData, byte[] newData)
    {
        if (oldData == null && newData == null) return false;
        if (oldData == null || newData == null) return true;
        byte[] oldHash = generateMD5(oldData);
        byte[] newHash = generateMD5(newData);
        return !Arrays.equals(oldHash, newHash);
    }

    /**
     * Generates an MD5 checksum as a String.
     * @param bytes The data being checksummed.
     * @return Byte array of the checksum value.
     */
    private static byte[] generateMD5(byte[] bytes)
    {
        MessageDigest messageDigest;
        try
        {
            messageDigest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new AssertionError("MD5 unavailable");
        }
        messageDigest.update(bytes);

        return messageDigest.digest();
    }

}
