package info.kgeorgiy.ja.bozhe.hello;

public class Logger {
    public static void logSocketJob(String requestString, String responseString) {
        System.out.println("---[PROCESSED]--- " + System.lineSeparator()
                + "   [REQUEST]      " + requestString + System.lineSeparator()
                + "   [RESPONSE]     " + responseString);
    }

    public static void logError(String message) {
        System.out.println("   [ERROR]        " +  message);
    }

    public static void logError(String message, Exception e) {
        logError(message);
        System.out.println(e.getMessage());
    }

    public static void logInterrupted(String process) {
        logError("The " + process + " process was interrupted.");
    }
}
