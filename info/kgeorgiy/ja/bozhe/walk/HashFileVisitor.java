package info.kgeorgiy.ja.bozhe.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class HashFileVisitor extends SimpleFileVisitor<Path> {
    private final BufferedWriter writer;
    private boolean success;

    public boolean visitFailed() {
        return !success;
    }

    HashFileVisitor(BufferedWriter writer) {
        this.writer = writer;
        success = true;
    }

    private FileVisitResult process(Path file, long hash) {
        try {
            HashWorker.writeHash(hash, file.toString(), writer);
            return FileVisitResult.CONTINUE;
        } catch (WalkException ex) {
            success = false;
            return FileVisitResult.TERMINATE;
        }
    }


    private FileVisitResult handle(Path file) {
        return process(file, HashWorker.countHash(file));
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
        return handle(file);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {
        return process(file, 0);
    }
}

