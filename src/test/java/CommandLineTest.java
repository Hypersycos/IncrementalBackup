import net.hypersycos.incrementalbackup.engine.IncrementalBackup;
import net.hypersycos.incrementalbackup.engine.SwitchingIncrementalBackup;
import net.hypersycos.incrementalbackup.handlers.MCAHandler;

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
        Path backupDir = directory.resolve("backups").resolve("Incremental");//Paths.get(reader.readLine());
        System.out.println(backupDir);
        SwitchingIncrementalBackup backup = new SwitchingIncrementalBackup(directory, backupDir);
        backup.register(new MCAHandler(), "mca");
        String input = "";
        while (!Objects.equals(input, "exit"))
        {
            input = reader.readLine();
            long startTime = System.currentTimeMillis();
            if (Objects.equals(input, "full")) backup.performFullBackup();
            else if (Objects.equals(input, "partial")) backup.performIncrementalBackup();
            else if (Objects.equals(input, "restore"))
            {
                System.out.println("dirname");
                Path restoreDir = directory.resolveSibling(reader.readLine());
                System.out.println("full");
                int majorVer = Integer.parseInt(reader.readLine());
                System.out.println("inc");
                int minorVer = Integer.parseInt(reader.readLine());
                backup.restore(majorVer, minorVer, restoreDir);
            }
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println("Elapsed: " + Long.toString(duration));
            System.out.print("Command: ");
        }
    }
}
