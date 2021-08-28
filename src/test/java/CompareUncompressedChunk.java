import compression.CompressionScheme;
import engine.SwitchingIncrementalBackup;
import handlers.BinaryHandler;
import handlers.MCAHandler;
import util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CompareUncompressedChunk
{
    public static void main(String[] args) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Path directory = Paths.get("").toAbsolutePath().resolve("test_data/main_dir/");//Paths.get(reader.readLine());
        Path backupDir = directory.resolveSibling("chunks");//Paths.get(reader.readLine());
        System.out.println(backupDir);
        MCAHandler handler = new MCAHandler();
        int size = 32;
        BinaryHandler handler1 = new BinaryHandler(size, 1024);

        Path file1 = Paths.get("").toAbsolutePath().resolve("B:\\Projects\\IncrementalBackup\\test_data\\0.chunk.0");
        Path file2 = Paths.get("").toAbsolutePath().resolve("B:\\Projects\\IncrementalBackup\\test_data\\5.chunk.0");

        Pair<byte[], CompressionScheme> difference = handler1.getDifference(Files.readAllBytes(file1), Files.readAllBytes(file2));
        System.out.println(difference.first().length);
    }
}
