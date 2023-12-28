import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BuildSystemShort {
    private static final Logger logger = new Logger("GradleMain", "main");
    private static GradleArgs args;
    private static final Object[] storage = new Object[1024];
    public static class Logger {
        private static final String RESET = "\u001B[0m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        private boolean silent = false;
        public enum LogEnum {
            INFO,WARN,ERR
        }
        private final String LN;
        private String TH;
        public Logger(String name, String thread) {
            LN = name;
            TH = thread;
        }
        public void setSilent(boolean silent) {
            this.silent = silent;
        }
        public void log(LogEnum type, Object message) {
            if (silent) {
                return;
            }
            switch(type) {
                case INFO:
                    System.out.printf("%s[%s%s%s] [%s%s%s] %s%s - %s\n", RESET, BLUE, TH, RESET, BLUE, LN, RESET, GREEN, "INFO", message.toString());
                    break;
                case WARN:
                    System.out.printf("%s[%s%s%s] [%s%s%s] %s%s - %s\n", RESET, BLUE, TH, RESET, BLUE, LN, RESET, YELLOW, "WARN", message.toString());
                    break;
                case ERR:
                    System.out.printf("%s[%s%s%s] [%s%s%s] %s%s - %s\n", RESET, BLUE, TH, RESET, BLUE, LN, RESET, RED, "ERR", message.toString());
                    break;
            }
        }
        public void info(Object message) {
            log(LogEnum.INFO, message);
        }
        public void warn(Object message) {
            log(LogEnum.WARN, message);
        }
        public void err(Object message) {
            log(LogEnum.ERR, message);
        }
    }
    public static class GradleArg {
        private final String name;
        private final String value;
        public GradleArg(String name, String value) {
            this.name = name;
            this.value = value;
        }
        public String getName() {
            return this.name;
        }
        public String getValue() {
            return this.value;
        }
    }
    public static class GradleArgs extends ArrayList<GradleArg> {
        public GradleArgs(String[] args) {
            for (String arg : args) {
                if (arg.startsWith("--")) {
                    if (!arg.contains("=")) {
                        add(new GradleArg(arg.substring(2), "flag_true"));
                    }
                    //these conditions are not met in this short build system version
                    else {
                        String[] argSplit = arg.split("=");
                        if (argSplit.length == 1) {
                            add(new GradleArg(argSplit[0], ""));
                        } else {
                            add(new GradleArg(argSplit[0], argSplit[1]));
                        }
                    }
                }
            }
            for (String arg : validArgs) {
                boolean found = false;
                for (GradleArg gradleArg : this) {
                    if (gradleArg.getName().equals(arg)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    add(new GradleArg(arg, "flag_false"));
                }
            }
        }
        private final String[] validArgs = {
                "debug",
                "silent",
                "self-impl"
        };
        private boolean isInvalidArg(String arg) {
            for (String validArg : validArgs) {
                if (validArg.equals(arg)) {
                    return false;
                }
            }
            return true;
        }
        public boolean containsFlag(String flag) {
            for (GradleArg gradleArg : this) {
                if (gradleArg.getName().equals(flag) && gradleArg.getValue().equals("flag_true")) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean add(GradleArg gradleArg) {
            if (isInvalidArg(gradleArg.getName())) {
                logger.warn("Invalid Gradle argument: " + gradleArg.getName() +", ignoring...");
                return false;
            }
            return super.add(gradleArg);
        }
        @Override
        public void add(int index, GradleArg element) {
            if (isInvalidArg(element.getName())) {
                logger.warn("Invalid Gradle argument: " + element.getName() +", ignoring...");
                return;
            }
            super.add(index, element);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            for (GradleArg gradleArg : this) {
                str.append("--").append(gradleArg.getName()).append("=").append(gradleArg.getValue()).append("; ");
            }
            return str.substring(0,str.length()-2);
        }
    }
    public enum GradleResult {
        SUCCESS,FAILURE,SKIPPED,FATAL_FAIL
    }
    public static class Utils {
        public static String trimLast(String s) {
            StringBuilder accumulator = new StringBuilder();
            for (int i = s.length() - 1; i >= 0; i--) {
                if (s.charAt(i) != ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\t') {
                    accumulator.append(s.charAt(i));
                }
            }
            return accumulator.reverse().toString();
        }
    }


    public static void putArgs(GradleArgs args) {
        BuildSystemShort.args = args;
    }
    private static void performInitialChecks() {
        if (!System.getProperty("java.version").startsWith("1.8")) {
            logger.err("Java version must be 1.8, found " + System.getProperty("java.version"));
            System.exit(1);
        }
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            logger.err("Operating system must be Windows, found " + System.getProperty("os.name"));
            System.exit(1);
        }
        if (args == null) {
            logger.warn("No arguments provided, using default arguments...");
        }
    }
    private static void initializeSelf() {
        if (args == null) putArgs(new GradleArgs(new String[]{}));
        logger.TH = "daemon";
        logger.info("Switched main thread to daemon and set logger thread to daemon.");
    }
    public static void initializeGradle() {
        logger.info("Attempting to retrieve Gradle version...");
        logger.info("Found Gradle version: v8.5 (latest)");
        logger.info("Initializing Gradle Daemon...");
        long currentMillis = System.currentTimeMillis();
        performInitialChecks();
        initializeSelf();
        logger.info("Gradle Daemon initialized in " + (System.currentTimeMillis() - currentMillis) + "ms.");
        logger.info("Read args: " + args.toString());

        logger.info(":initialize SKIPPED");
        logger.info("Task :initialize done.");
    }
    public static void runTask(BiFunction<GradleArgs, Object[], GradleResult> task, String taskName, Object... args) {
        logger.info(":" + taskName);
        switch (task.apply(BuildSystemShort.args, args)) {
            case SUCCESS:
                logger.info("Task :" + taskName + " SUCCESS");
                break;
            case FAILURE:
                logger.err("Task " + taskName + " FAIL");
                break;
            case FATAL_FAIL:
                logger.err("Task :" + taskName + " FAIL");
                System.exit(1);
            case SKIPPED:
                logger.info("Task :" + taskName + " SKIPPED");
                break;
        }
    }

    private static int[] asPtr(int a) {
        return new int[]{a};
    }
    private static boolean isStorageFull() {
        for (Object o : storage) {
            if (o == null) {
                return false;
            }
        }
        return true;
    }
    private static int getRandomAccessPointerPosition() {
        if (isStorageFull()) {
            return -1;
        }
        int i = -1;
        while (i == -1 || storage[i] != null) {
            i = (int) (Math.random() * storage.length);
        }
        return i;
    }
    public static int[] alloc(Object obj) {
        int ptr = getRandomAccessPointerPosition();
        logger.info("Allocated pointer at " + ptr + " for object " + obj);
        return asPtr(ptr);
    }
    public static int[] malloc(int size) {
        int[] pointers = new int[size];
        for (int i = 0; i < size; i++) {
            pointers[i] = getRandomAccessPointerPosition();
        }
        logger.info("Allocated " + size + " pointers: " + Arrays.toString(pointers));
        return pointers;
    }
    public static Object get(int index) {
        return storage[index];
    }
    public static void set(int index, Object obj) {
        storage[index] = obj;
    }
}
