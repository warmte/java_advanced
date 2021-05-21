package info.kgeorgiy.ja.bozhe.walk;

import java.io.*;
import java.nio.file.*;

public class BaseWalk {
    private final Path inputPath;
    private final Path outputPath;
    private final boolean recursive;


    BaseWalk(Path inputPath, Path outputPath, boolean recursive) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.recursive = recursive;
    }

    public static void run(String[] args, boolean recursive) {
        if (args == null || args.length != 2) {
            System.err.println("Invalid arguments number. Usage: RecursiveWalk <input file name> <output file name>");
        } else if (args[0] == null) {
            System.err.println("Input file name must be written.");
        } else if (args[1] == null) {
            System.err.println("Output file name must be written.");
        } else {
            try {
                new BaseWalk(findPath(args[0], "input"), findPath(args[1], "output"), recursive).countHashes();
            } catch (WalkException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private static Path findPath(String fileName, String fileType) throws WalkException {
        try {
            return Paths.get(fileName);
        } catch (InvalidPathException e) {
            throw new WalkException(String.format("Path for the %s file is not correct: %s", fileType, fileName), e);
        }
    }

    private void countHashes() throws WalkException {
        Path outputParentPath = outputPath.getParent();
        if (outputParentPath != null) {
            try {
                Files.createDirectories(outputParentPath);
            } catch (FileAlreadyExistsException e) {
                System.err.println("The directory name exists but is not a directory.");
            } catch (IOException e) {
                // Nothing happens right now.
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                try {
                    HashFileVisitor visitor = new HashFileVisitor(writer);

                    String file;
                    while ((file = reader.readLine()) != null) {
                        try {
                            Path filepath = Paths.get(file);
                            if (this.recursive) {
                                Files.walkFileTree(filepath, visitor);
                                if (visitor.visitFailed()) {
                                    throw new WalkException("Cannot write to the output file.");
                                }
                            } else {
                                HashWorker.writeHash(HashWorker.countHash(filepath), file, writer);
                            }
                        } catch (InvalidPathException e) {
                            System.err.printf("Invalid path of the file: %s%n", file);
                            HashWorker.writeHash(0, file, writer);
                        } catch (IOException e) {
                            throw new WalkException("The file cannot be processed:" + file, e);
                        }
                    }
                } catch (IOException e) {
                    throw new WalkException("Error with reading from the input file.", e);
                }
            } catch (IOException e) {
                throw new WalkException("Unable to go through the output file.", e);
            }
        } catch (IOException e) {
            throw new WalkException("Unable to go through the input file.", e);
        }
    }
}
