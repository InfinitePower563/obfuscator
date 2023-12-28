import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class Main {
    public static void main(String[] args) {
        BuildSystemShort.putArgs(new BuildSystemShort.GradleArgs(args));
        BuildSystemShort.initializeGradle();
        BuildSystemShort.runTask(Main::awt, "awt");
    }

    private static BuildSystemShort.GradleResult awt(BuildSystemShort.GradleArgs gArgs, Object[] args) {
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

        JButton mappingsButton = new JButton("(None)");
        mappingsButton.setBounds(10, 70, 250, 30);
        mappingsButton.addActionListener((ae) -> {
            JFileChooser mappingsChooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Mappings (.mapping)", "mapping");
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

            BuildSystemShort.runTask(Main::runObfuscation, "obfuscate", BuildSystemShort.get(mappingLocation[0]), BuildSystemShort.get(fileLocation[0].get(0)));
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
            if (file.next != -1) {
                LinkedFile currentFile = (LinkedFile) BuildSystemShort.get(file.next);
                while (currentFile.next != -1) {
                    fileContents.add(Files.readAllLines(currentFile.getFile().toPath()));
                    currentFile = (LinkedFile) BuildSystemShort.get(currentFile.next);
                }
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
                fieldNames = new ArrayList<>();

        String currentType = "";
        for (String line : mappingsContents) {
            if (line.startsWith("CLASS")) {
                currentType = "CLASS";
            } else if (line.startsWith("METHOD")) {
                currentType = "METHOD";
            } else if (line.startsWith("FIELD")) {
                currentType = "FIELD";
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
                }
            }
        }

        int currentClassIndex = 0, currentMethodIndex = 0, currentFieldIndex = 0;
        HashMap<String, String> mapping = new HashMap<>();
        ArrayList<ArrayList<String>> newFileContents = new ArrayList<>();
        //First create mappings
        for (List<String> f : fileContents) {
            for (String s : f) {
                String trimmed = s.trim();
                if (trimmed.startsWith("public class ")) {
                    logger.info("Found class " + trimmed.substring("public class ".length()));
                    String without = trimmed.substring("public class ".length());
                    String className = without.substring(0, without.indexOf(" "));
                    if (currentClassIndex + 1 > classNames.size() - 1) {
                        logger.err("Mappings file does not contain enough class mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    mapping.put(className, classNames.get(currentClassIndex++));
                }
                if (trimmed.matches("^\t*(public|private|protected|)( static|static|)( void|void| \\w+) \\w+(\\([\\w|\\s]*\\)).*$")) {
                    String without = trimmed.substring(0, trimmed.indexOf("("));
                    String methodName = without.substring(without.lastIndexOf(" ") + 1);
                    if (currentMethodIndex + 1 > methodNames.size() - 1) {
                        logger.err("Mappings file does not contain enough method mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    mapping.put(methodName, methodNames.get(currentMethodIndex++));
                }
                if (trimmed.matches("^\t*(public|private|protected|)( static|static|)( final|final|)( \\w+|\\w+) \\w+;$")) {
                    if (trimmed.startsWith("public static void main(")) continue;
                    String without = trimmed.substring(0, trimmed.indexOf(";"));
                    String fieldName = without.substring(without.lastIndexOf(" ") + 1);
                    if (currentFieldIndex + 1 > fieldNames.size() - 1) {
                        logger.err("Mappings file does not contain enough field mappings.");
                        return BuildSystemShort.GradleResult.FAILURE;
                    }
                    mapping.put(fieldName, fieldNames.get(currentFieldIndex++));
                }
            }
        }
        logger.info("Created " + mapping.size() + " mappings.");
        //now replace
        for (List<String> f : fileContents) {
            ArrayList<String> newFile = new ArrayList<>();
            for (String s : f) {
                String t = s;
                for (String key : mapping.keySet()) {
                    t = t.replaceAll(key, mapping.get(key));
                }
                newFile.add(t);
            }
            newFileContents.add(newFile);
        }
        logger.info("Replaced all mappings.");
        //now write
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
                Files.write(new File(file.getFile().getParentFile(), className + ".java").toPath(), f);
                logger.info("Wrote " + className + ".java");
            } catch (IOException e) {
                logger.err("Failed to write file " + file.getFile().getPath());
                return BuildSystemShort.GradleResult.FAILURE;
            }
        }

        return BuildSystemShort.GradleResult.SUCCESS;
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