package info.kgeorgiy.ja.bozhe.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class HelloUDPServer implements HelloServer {
    private ServerDataProcessor processor;
    private ExecutorService helloService;

    @Override
    public void start(int port, int threads) {
        try {
            this.processor = new ServerDataProcessor(new DatagramSocket(port));
            this.helloService = Executors.newFixedThreadPool(threads);
            Runnable task = () -> {
                while (!processor.isClosed()) {
                    processor.processNewRequest();
                }
            };
            IntStream.range(0, threads).forEach(x -> helloService.submit(task));
        } catch (SocketException e) {
            Logger.logError(e.getMessage());
        }
    }

    @Override
    public void close() {
        processor.close();
        helloService.shutdown();
        try {
            boolean res = helloService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Logger.logInterrupted("requests handling");
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            Logger.logError("Incorrect number of arguments, should be: 2");
            return;
        }
        for (String arg: args) {
            if (arg == null) {
                Logger.logError("Arguments can't be null.");
            }
            return;
        }

        try {
            final int port = Integer.parseInt(args[0]);
            final int threads = Integer.parseInt(args[1]);
            new HelloUDPServer().start(port, threads);
        } catch (NumberFormatException e) {
            Logger.logError("Incorrect arguments. " + e.getMessage());
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.hello server-evil info.kgeorgiy.ja.bozhe.hello.HelloUDPServer
