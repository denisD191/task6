import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) {
        if(args.length == 0)
            return;

        File dir = new File(args[0]);

        List<File> filesList = getList(dir);
        List<String> classDefinitions = new ArrayList<>();

        int max_cap = 100;
        BlockingQueue<File> q_source = new LinkedBlockingQueue<>(max_cap);
        BlockingQueue<List<String>> q_result = new LinkedBlockingQueue<>(max_cap);

        (new Thread(new Processor(q_source, q_result))).start();
        (new Thread(new Processor(q_source, q_result))).start();
        (new Thread(new Proc_result(q_result, classDefinitions))).start();

        for (File file: filesList) {
            try {
                // put files to source queue
                q_source.put(file);
                System.out.println("В очередь добавлен файл " + file.getName());
            } catch (InterruptedException ex) {}
        }

        while ((q_result.remainingCapacity() < max_cap) && (q_source.remainingCapacity() < max_cap));
            //wait while q_result and q_source not empty

        Map<String, String> map = getMap(classDefinitions);
        for(Map.Entry<String, String> entry : map.entrySet()) {
            System.out.println(
                    entry.getKey() + ":" + (entry.getValue() != null ? entry.getValue() : "Object"));
        }
    }

    // list all source files
    private static List<File> getList(File directory) {
        List<File> files = null;
        if(directory.isDirectory()) {
            File[] list = directory.listFiles();
            if(list != null) {
                // get files from current directory
                files = Arrays.stream(list)
                        .filter(File::isFile)
                        .collect(Collectors.toList());
                // get files from subdirectories
                List<File> dirs = Arrays.stream(list)
                        .filter(File::isDirectory)
                        .collect(Collectors.toList());
                for (File dir: dirs) {
                    files.addAll(getList(dir));
                }
            }
        }
        return files;
    }

    // key - classname, value - parent and interfaces
    private static Map<String, String> getMap(List<String> classDefinitions) {
        Map<String, String> map = new HashMap<>();
        for(String def: classDefinitions) {
            String[] words = def.split(" ");
            String key = "", value = null;
            int i = words[0].equals("public") ? 1 : 0;

            if(words[i].equals("class")) {
                key = "class " + words[i + 1];
            }
            else if(words[i].equals("interface")) {
                key = "interface " + words[i + 1];
            }
            if(words.length > 4) {
                if (words[i + 2].equals("extends"))
                    value = words[i + 3];
            }
            if(words.length > 5) {
                if (words[i + 4].equals("implements"))
                    for(int j = i + 5; j < words.length; j++) {
                        value += ", " + words[j];
                    }
            }
            map.put(key, value);
        }
        return map;
    }
}

class Processor implements Runnable {
    private final BlockingQueue<File> src;
    private File F;
    private List<String> clDef;
    private final BlockingQueue<List<String>> res;
    Processor(BlockingQueue<File> src, BlockingQueue<List<String>> res) { this.src = src; this.res = res; }
    public void run() {
        try {
            while (true) {
                // take file from src
                F = src.take();
                System.out.println("Начата обработка файла " + F.getName());
                clDef = new ArrayList<>();
                try {
                    clDef.addAll(Files.lines(Paths.get(F.getAbsolutePath()))
                            .filter(line -> line.contains("class")
                                    || line.contains("interface")
                                    || line.contains("extends")
                                    || line.contains("implements"))
                            .collect(Collectors.toList()));
                }
                catch(IOException e) {}
                System.out.println("Обработан файл " + F.getName());
                // put result into res
                res.put(clDef);
            }
        }
        catch (InterruptedException ex) {}
    }
}

class Proc_result implements Runnable {
    private final BlockingQueue<List<String>> res;
    private List<String> clDef, cld;
    Proc_result(BlockingQueue<List<String>> res, List<String> cld) { this.res = res; this.cld = cld; }
    public void run() {
        try {
            while (true) {
                // take class definitions from res
                clDef = res.take();
                // put result into cld
                cld.addAll(clDef);
            }
        }
        catch (InterruptedException ex) {}
    }
}