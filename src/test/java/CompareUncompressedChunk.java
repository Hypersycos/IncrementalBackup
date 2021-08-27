import engine.SwitchingIncrementalBackup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CompareUncompressedChunk
{
    public static void main(String[] args)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Path directory = Paths.get("").toAbsolutePath().resolve("test_data/main_dir/");//Paths.get(reader.readLine());
        Path backupDir = directory.resolveSibling("backup_dir");//Paths.get(reader.readLine());
        System.out.println(backupDir);
        SwitchingIncrementalBackup backup = new SwitchingIncrementalBackup(directory, backupDir);
    }
}
