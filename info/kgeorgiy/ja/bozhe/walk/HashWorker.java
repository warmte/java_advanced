package info.kgeorgiy.ja.bozhe.walk;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class HashWorker {
    private static final int BUF_SIZE = 1024;
    private static final byte[] buf = new byte[BUF_SIZE];
    final static long HIGH_BYTES = 0xff00_0000_0000_0000L;

    public static long countHash(Path file) {
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
            long hash = 0, high;
            for (int cnt = 0; cnt != -1; cnt = stream.read(buf)) {
                for (int i = 0; i < cnt; ++i) {
                    hash = (hash << 8) + (buf[i] & 0xff);
                    if ((high = hash & HIGH_BYTES) != 0) {
                        hash ^= high >> 48;
                        hash &= ~high;
                    }
                }
            }
            return hash;
        } catch (IOException e) {
            System.err.printf("Cannot read from the file: %s%n", file.toString());
            return 0;
        }
    }

    public static void writeHash(long hash, String file, BufferedWriter writer) throws WalkException {
        try {
            writer.write(String.format("%016x %s", hash, file));
            writer.newLine();
        } catch (IOException e) {
            throw new WalkException("Cannot write to the output file.");
        }
    }
}
