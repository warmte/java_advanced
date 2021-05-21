package info.kgeorgiy.ja.bozhe.walk;

public class WalkException extends Exception {
    WalkException(String message) {
        super(message);
    }

    WalkException(String message, Exception e) {
        super(message + System.lineSeparator() + e.getMessage(), e);
    }
}
