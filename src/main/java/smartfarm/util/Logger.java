package smartfarm.util;

public class Logger {
    public static void logger(Runnable func){
        func.run();
    }
}
