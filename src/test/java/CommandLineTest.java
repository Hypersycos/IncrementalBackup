import engine.IncrementalBackup;
import engine.SwitchingIncrementalBackup;
import handlers.MCAHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class CommandLineTest
{
    public static void main(String[] args) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Path directory = Paths.get("").toAbsolutePath().resolve("test_data/main_dir/");//Paths.get(reader.readLine());
        Path backupDir = directory.resolveSibling("backup_dir");//Paths.get(reader.readLine());
        System.out.println(backupDir);
        SwitchingIncrementalBackup backup = new SwitchingIncrementalBackup(directory, backupDir);
        backup.register(new MCAHandler(), "mca");
        String input = "";
        while (!Objects.equals(input, "exit"))
        {
            input = reader.readLine();
            if (Objects.equals(input, "full")) backup.performFullBackup();
            else if (Objects.equals(input, "partial")) backup.performIncrementalBackup();
            else if (Objects.equals(input, "restore"))
            {
                System.out.println("dirname");
                Path restoreDir = directory.resolveSibling(reader.readLine());
                System.out.println("inc");
                int minorVer = Integer.parseInt(reader.readLine());
                backup.restore(1, minorVer, restoreDir);
            }
            System.out.print("Command: ");
        }
    }
}
