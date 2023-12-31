import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class Main {
    public static void main(String[] args) {
        BuildSystemShort.putArgs(new BuildSystemShort.GradleArgs(args));
        BuildSystemShort.initializeGradle();
        BuildSystemShort.putTasks(
                new BuildSystemShort.GradleTask("awt", Main::awt),
                new BuildSystemShort.GradleTask("obfuscate", Main::runObfuscation),
                new BuildSystemShort.GradleTask("genMappings", Main::genMappings)
        );
        BuildSystemShort.runTask("awt");
    }

    public static BuildSystemShort.GradleResult awt(BuildSystemShort.GradleArgs gArgs, Object[] args) {
        BuildSystemShort.Logger logger = new BuildSystemShort.Logger("AWT", "AWT-Event-Loop");
        if (gArgs.containsFlag("silent")) {
            logger.setSilent(true);
        }

        int[] mappingLocation = BuildSystemShort.alloc(null);
        final AtomicIntegerArray[] fileLocation = {new AtomicIntegerArray(new int[0])};

        JFrame frame = new JFrame("Obfuscator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 300);

        JLabel mappings = new JLabel("Mappings");
        mappings.setBounds(10, 10, 100, 100);
        frame.add(mappings);

        JButton genMappings = new JButton("Generate Mappings");
        genMappings.setBounds(10, 0, 200, 30);
        genMappings.addActionListener((ae) -> {
            BuildSystemShort.runTask("genMappings");
            File mappingsFile = new File("default.mapping");
            JOptionPane.showMessageDialog(frame, "Generated mappings file at " + mappingsFile.getAbsolutePath());
        });
        frame.add(genMappings);

        JButton mappingsButton = new JButton("(None)");
        mappingsButton.setBounds(10, 70, 250, 30);
        mappingsButton.addActionListener((ae) -> {
            JFileChooser mappingsChooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Mappings (.mapping)", "mapping");
            mappingsChooser.setCurrentDirectory(new File("build.gradle"));
            mappingsChooser.setFileFilter(filter);

            if (mappingsChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                BuildSystemShort.set(mappingLocation[0], mappingsChooser.getSelectedFile());
                mappingsButton.setText(mappingsChooser.getSelectedFile().getName());
            }
        });
        frame.add(mappingsButton);

        JLabel file = new JLabel("File(s)");
        file.setBounds(10, 110, 100, 100);
        frame.add(file);

        JButton fileButton = new JButton("(None)");
        fileButton.setBounds(10, 170, 250, 30);
        fileButton.addActionListener((ae) -> {
            JFileChooser fileChooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Java File (.java)", "java");
            fileChooser.setCurrentDirectory(new File("build.gradle"));
            fileChooser.setFileFilter(filter);
            fileChooser.setMultiSelectionEnabled(true);

            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File[] files = fileChooser.getSelectedFiles();

                fileLocation[0] = new AtomicIntegerArray(BuildSystemShort.malloc(files.length));
                for (int i = 0; i < files.length - 1; i++) {
                    BuildSystemShort.set(fileLocation[0].get(i), new LinkedFile(files[i], fileLocation[0].get(i + 1)));
                }
                BuildSystemShort.set(fileLocation[0].get(files.length - 1), new LinkedFile(files[files.length - 1], -1));
                fileButton.setText(files.length + " files selected.");
            }
        });
        frame.add(fileButton);

        JButton obfuscate = new JButton("Obfuscate");
        obfuscate.setBounds(10, 210, 250, 30);
        obfuscate.addActionListener((ae) -> {
            if (BuildSystemShort.get(mappingLocation[0]) == null) {
                logger.err("Mappings file not selected.");
                JOptionPane.showMessageDialog(frame, "Mappings file not selected.");
                return;
            }

            File mappingsFile = (File) BuildSystemShort.get(mappingLocation[0]);
            if (!mappingsFile.getPath().endsWith(".mapping")) {
                logger.err("Mappings file must be a .mapping file.");
                JOptionPane.showMessageDialog(frame, "Mappings file must be a .mapping file.");
                return;
            }

            BuildSystemShort.runTask("obfuscate", BuildSystemShort.get(mappingLocation[0]), BuildSystemShort.get(fileLocation[0].get(0)));
            JOptionPane.showMessageDialog(frame, "Obfuscated files.");
        });
        frame.add(obfuscate);

        frame.setLayout(null);
        frame.setVisible(true);
        return BuildSystemShort.GradleResult.SUCCESS;
    }
    public static BuildSystemShort.GradleResult runObfuscation(BuildSystemShort.GradleArgs gArgs, Object[] args) {
        File mappings = (File) args[0];
        LinkedFile file = (LinkedFile) args[1];

        BuildSystemShort.Logger logger = new BuildSystemShort.Logger("Obfuscator", "daemon");
        if (!gArgs.containsFlag("self-impl")) {
            logger.warn("Self-implementation is not enabled. This may cause issues with the obfuscator.");
        }
        if (gArgs.containsFlag("silent")) {
            logger.setSilent(true);
        }
        String mappingsPath = mappings.getPath();
        logger.info("Obfuscating with mappings " + mappingsPath);
        logger.info("File of origin: " + file.getFile().getPath());

        List<String> mappingsContents;
        ArrayList<List<String>> fileContents = new ArrayList<>();

        try {
            mappingsContents = Files.readAllLines(mappings.toPath());

            fileContents.add(Files.readAllLines(file.getFile().toPath()));
            for (int i = file.next; i != -1; i = ((LinkedFile) BuildSystemShort.get(i)).next) {
                fileContents.add(Files.readAllLines(((LinkedFile) BuildSystemShort.get(i)).getFile().toPath()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mappingsContents.isEmpty()) {
            logger.err("Mappings file is empty.");
            return BuildSystemShort.GradleResult.FAILURE;
        }

        ArrayList<String>
                classNames = new ArrayList<>(),
                methodNames = new ArrayList<>(),
                fieldNames = new ArrayList<>(),
                deadCode = new ArrayList<>();

        String currentType = "";
        StringBuilder deadCodeBuilder = new StringBuilder();
        for (String line : mappingsContents) {
            if (line.startsWith("CLASS")) {
                currentType = "CLASS";
            } else if (line.startsWith("METHOD")) {
                currentType = "METHOD";
            } else if (line.startsWith("FIELD")) {
                currentType = "FIELD";
            } else if (line.startsWith("DEADCODE")) {
                currentType = "DEADCODE";
            } else {
                switch (currentType) {
                    case "CLASS":
                        classNames.add(line);
                        break;
                    case "METHOD":
                        methodNames.add(line);
                        break;
                    case "FIELD":
                        fieldNames.add(line);
                        break;
                    case "DEADCODE":
                        if (line.equals("3pu4xF2Uqof55GplgL06Bw8Ip8tNwX")) {
                            deadCode.add(deadCodeBuilder.toString());
                            deadCodeBuilder = new StringBuilder();
                            break;
                        }
                        deadCodeBuilder.append(line).append("\n");
                        break;
                }
            }
        }

        int currentClassIndex = 0, currentMethodIndex = 0, currentFieldIndex = 0;
        HashMap<String, String> mapping = new HashMap<>();
        ArrayList<ArrayList<String>> newFileContents = new ArrayList<>();
        logger.info("Creating mappings for " + fileContents.size() + " files.");
        //First create mappings
        for (List<String> f : fileContents) {
            boolean override = false;
            boolean inMethod = false;
            int braceCounter = 0;
            for (String s : f) {
                String trimmed = s.trim();
                if (trimmed.contains("@Override")) {
                    override = true;
                    continue;
                }
                if (override) {
                    override = false;
                    continue;
                }
                if (inMethod && trimmed.contains("{")) {
                    braceCounter++;
                }
                if (inMethod && trimmed.contains("}")) {
                    braceCounter--;
                    if (braceCounter == 0) {
                        inMethod = false;
                    }
                }
                if (trimmed.contains("return")) {
                    continue;
                }
                if (trimmed.startsWith("public static void main")) {
                    continue;
                }
                if (trimmed.startsWith("public class ")) {
                    logger.info("Found class " + trimmed.substring("public class ".length()));
                    String without = trimmed.substring("public class ".length());
                    String className = without.substring(0, without.indexOf(" "));
                    if (currentClassIndex + 1 > classNames.size() - 1) {
                        logger.err("Mappings file does not contain enough class mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    mapping.put(className, classNames.get(currentClassIndex++));
                    continue;
                }
                if (trimmed.matches("^(public|private|protected|)( static|static|)( void|void| \\w+) \\w+(\\(.*\\)).*$")) {
                    String without = trimmed.substring(0, trimmed.indexOf("("));
                    String methodName = without.substring(without.lastIndexOf(" ") + 1);
                    if (currentMethodIndex + 1 > methodNames.size() - 1) {
                        logger.err("Mappings file does not contain enough method mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    logger.info("Found method \"" + methodName + "\" from line " + trimmed);
                    mapping.put(methodName, methodNames.get(currentMethodIndex++));
                    inMethod = true;
                    continue;
                }
                if (trimmed.matches("^\\w+ \\w+( = | =|= |=)\\w+.+$") && inMethod) {
                    String without = trimmed.substring(0, trimmed.indexOf("=")).trim();
                    String fieldName = without.substring(without.lastIndexOf(" ") + 1);
                    if (currentFieldIndex + 1 > fieldNames.size() - 1) {
                        logger.err("Mappings file does not contain enough field mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    logger.info("Found variable \"" + fieldName + "\" from line: " + without);
                    mapping.put(fieldName, fieldNames.get(currentFieldIndex++));
                }
                if (trimmed.matches("^\t*(public|private|protected|)( static|static|)( final|final|)( \\w+|\\w+) \\w+;$")) {
                    if (trimmed.startsWith("public static void main(")) continue;
                    String without = trimmed.substring(0, trimmed.indexOf(";"));
                    String fieldName = without.substring(without.lastIndexOf(" ") + 1);
                    if (currentFieldIndex + 1 > fieldNames.size() - 1) {
                        logger.err("Mappings file does not contain enough field mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    logger.info("Found field \"" + fieldName + " from line \"" + trimmed);
                    mapping.put(fieldName, fieldNames.get(currentFieldIndex++));
                }
            }
        }

        logger.info("Created " + mapping.size() + " mappings:");
        for (String key : mapping.keySet()) {
            logger.info(key + " -> " + mapping.get(key));
        }
        int deadCodePointer = 0;
        for (List<String> f : fileContents) {
            ArrayList<String> newFile = new ArrayList<>();
            boolean inClass = false;
            int braceCounter = 0;
            for (String s : f) {
                String t = s;
                String trimmed = s.trim();
                if (inClass && trimmed.contains("{")) {
                    braceCounter++;
                }
                if (inClass && trimmed.contains("}")) {
                    braceCounter--;
                    if (braceCounter == -1) {
                        inClass = false;
                    }
                }
                if (trimmed.contains("class")) {
                    inClass = true;
                }
                for (String key : mapping.keySet()) {
                    t = t.replaceAll(key, mapping.get(key));
                }
                if (inClass && braceCounter == 0 && s.contains("}") && Math.random() > 0.75d) {
                    logger.info("Wrote dead code snippet.");
                    newFile.add(t);
                    String deadCodeSnippet = deadCode.get(deadCodePointer++);
                    for (String line : deadCodeSnippet.split("\n")) {
                        newFile.add("\t" + line);
                    }
                    continue;
                }
                newFile.add(t);
            }
            newFileContents.add(newFile);
        }
        logger.info("Replaced all mappings.");

        //create a new directory
        File newDir = new File(file.getFile().getParentFile(), "out");
        if (newDir.exists()) {
            logger.warn("Output directory already exists. Deleting...");
            if (!newDir.delete()) {
                logger.warn("Failed to delete output directory.");
            }
        }
        if (!newDir.mkdir()) {
            logger.err("Failed to create output directory.");
            return BuildSystemShort.GradleResult.FAILURE;
        }

        for (ArrayList<String> f: newFileContents) {
            try {
                String className = null;
                for (String s : f) {
                    if (s.startsWith("public class ")) {
                        String without = s.substring("public class ".length());
                        className = without.substring(0, without.indexOf(" "));
                        break;
                    }
                }
                if (className == null) {
                    logger.err("Failed to find class name.");
                    return BuildSystemShort.GradleResult.FAILURE;
                }
                Files.write(new File(newDir, className + ".java").toPath(), f);
                logger.info("Wrote " + className + ".java");
            } catch (IOException e) {
                logger.err("Failed to write file " + file.getFile().getPath());
                return BuildSystemShort.GradleResult.FAILURE;
            }
        }

        return BuildSystemShort.GradleResult.SUCCESS;
    }
    public static BuildSystemShort.GradleResult genMappings(BuildSystemShort.GradleArgs gArgs, Object[] args) {
        ArrayList<String> classes, methods = new ArrayList<>(), fields = new ArrayList<>(), deadCode;
        BuildSystemShort.Logger logger = new BuildSystemShort.Logger("mapping-gen", "daemon");
        if (gArgs.containsFlag("silent")) {
            logger.setSilent(true);
        }
        String classList = fetchClasses();
        String deadCodes = fetchMethods();
        classes = new ArrayList<>(Arrays.asList(classList.split("\n")));
        for (int i = 'A'; i <= 'Z'; i++) {
            for (int j = 1; j <= 25; j++) {
                StringBuilder s = new StringBuilder();
                for (int k = 0; k < j; k++) {
                    s.append((char) i);
                }
                methods.add(s.toString());
            }
        }
        logger.info("Generated class mappings.");
        //format: m0x + 6 random hex digits
        for (int i = 0; i < 10000; i++) {
            StringBuilder s = new StringBuilder("m0x");
            for (int j = 0; j < 6; j++) {
                s.append((char) (Math.random() * ('f' - 'a') + 'a'));
            }
            while (methods.contains(s.toString())) {
                s = new StringBuilder("m0x");
                for (int j = 0; j < 6; j++) {
                    s.append((char) (Math.random() * ('f' - 'a') + 'a'));
                }
            }
            methods.add(s.toString());
            if (i % 1000 == 0) logger.info("Generated " + (i) + " method mappings.");
        }
        logger.info("Generated method mappings.");
        char c = (char) (Math.random() * ('z' - 'a') + 'a');
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    for (int l = 0; l < 16; l++) {
                        fields.add(String.format("%c%c%c%c%c",
                                c,
                                (i > 9 ? (char) ('a' + i - 10) : (char) ('0' + i)),
                                (j > 9 ? (char) ('a' + j - 10) : (char) ('0' + j)),
                                (k > 9 ? (char) ('a' + k - 10) : (char) ('0' + k)),
                                (l > 9 ? (char) ('a' + l - 10) : (char) ('0' + l))
                        ));
                    }
                }
            }
        }
        logger.info("Generated field snippets.");

        String delimiterLine = deadCodes.split("\n")[0];
        String d = delimiterLine.substring(2);
        String[] excludingFirstLine = deadCodes.split("\n");
        excludingFirstLine[0] = "";
        deadCodes = String.join("\n", excludingFirstLine);
        deadCode = new ArrayList<>(Arrays.asList(deadCodes.split(d)));

        logger.info("Generated " + classes.size() + " class mappings.");
        logger.info("Generated " + methods.size() + " method mappings.");
        logger.info("Generated " + fields.size() + " field mappings.");
        logger.info("Generated " + deadCode.size() + " dead code snippets.");

        Collections.shuffle(classes);
        Collections.shuffle(methods);
        Collections.shuffle(fields);

        File mappingsFile = new File("default.mapping");
        if (mappingsFile.exists()) {
            logger.warn("Mappings file already exists. Deleting...");
            if (!mappingsFile.delete()) {
                logger.warn("Failed to delete mappings file.");
            }
        }
        try (PrintWriter writer = new PrintWriter(mappingsFile)) {
            writer.println("CLASS");
            for (String s : classes) {
                writer.println(s);
            }
            writer.println("METHOD");
            for (String s : methods) {
                writer.println(s);
            }
            writer.println("FIELD");
            for (String field : fields) {
                writer.println(field);
            }
            writer.println("DEADCODE\n3pu4xF2Uqof55GplgL06Bw8Ip8tNwX");
            for (String s : deadCode) {
                writer.println(s);
                writer.println("3pu4xF2Uqof55GplgL06Bw8Ip8tNwX");
            }
            logger.info("Wrote mappings file at " + mappingsFile.getAbsolutePath());
        } catch (IOException e) {
            logger.err("Failed to write mappings file.");
            return BuildSystemShort.GradleResult.FAILURE;
        }

        return BuildSystemShort.GradleResult.SUCCESS;
    }
    private static String fetchClasses() {
        BuildSystemShort.Logger logger = new BuildSystemShort.Logger("net-coro", "coroutines");
        logger.info(":runClient :coroutines :fetchClasses");
        logger.info("Fetching classes from CDN...");
        try {
            URL cdn = new URL("https://d3n9s55goxa3h4.cloudfront.net/cdn/RmCB7dXLfCehQRqGpvZUIjTh6gomakOQ/classes.txt");
            logger.info("CDN -> https://d3n9s55goxa3h4.cloudfront.net/cdn/RmCB7dXLfCehQRqGpvZUIjTh6gomakO");
            StringBuilder s = new StringBuilder();
            Scanner scanner = new Scanner(cdn.openStream());
            while (scanner.hasNextLine()) {
                s.append(scanner.nextLine()).append("\n");
            }
            logger.info("Fetched " + s.toString().split("\n").length + " lines.");
            logger.info(":runClient :coroutines :fetchClasses DONE");
            return s.toString();
        } catch (IOException e) {
            logger.err("Failed to fetch classes.");
            return "";
        }
    }
    private static String fetchMethods() {
        BuildSystemShort.Logger logger = new BuildSystemShort.Logger("net-coro", "coroutines");
        logger.info(":runClient :coroutines :fetchMethods");
        logger.info("Fetching methods from CDN...");
        try {
            URL cdn = new URL("https://d3n9s55goxa3h4.cloudfront.net/cdn/RmCB7dXLfCehQRqGpvZUIjTh6gomakOQ/methods.txt");
            logger.info("CDN -> https://d3n9s55goxa3h4.cloudfront.net/cdn/RmCB7dXLfCehQRqGpvZUIjTh6gomakOQ");
            StringBuilder s = new StringBuilder();
            Scanner scanner = new Scanner(cdn.openStream());
            while (scanner.hasNextLine()) {
                s.append(scanner.nextLine()).append("\n");
            }
            logger.info("Fetched " + s.toString().split("\n").length + " lines.");
            logger.info(":runClient :coroutines :fetchMethods DONE");
            return s.toString();
        } catch (IOException e) {
            logger.err("Failed to fetch classes.");
            return "";
        }
    }
    public static class LinkedFile {
        private final File file;
        private final int next;

        public LinkedFile(File file, int next) {
            this.file = file;
            this.next = next;
        }
        public File getFile() {
            return file;
        }
    }
}