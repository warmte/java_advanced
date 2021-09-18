package info.kgeorgiy.ja.bozhe.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class HelloUDPClient implements HelloClient {
    void task(int requests, InetSocketAddress address, String prefix, int thread) {
        try (DatagramSocket socket = new DatagramSocket()) {
            ClientDataProcessor processor = new ClientDataProcessor(prefix, thread, socket, address);
            IntStream.range(0, requests).forEach(processor::processNewResponse);
        } catch (SocketException e) {
            Logger.logError(e.getMessage());
        }
    }

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
            ExecutorService threadPool = Executors.newFixedThreadPool(threads);
            IntStream.range(0, threads).forEach(thread -> threadPool.submit(() -> task(requests, address, prefix, thread)));
            threadPool.shutdown();
            try {
                boolean res = threadPool.awaitTermination((long) requests * threads * 100, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Logger.logInterrupted("requests submitting");
            }
        } catch (UnknownHostException ignored) {
            Logger.logError("The " + host + " host is unknown.");
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 5) {
            Logger.logError("Incorrect number of arguments, should be: 5");
            return;
        }
        for (String arg: args) {
            if (arg == null) {
                Logger.logError("Arguments can't be null.");
            }
            return;
        }

        try {
            final String host = args[0];
            final int port = Integer.parseInt(args[1]);
            final String prefix = args[2];
            final int threads = Integer.parseInt(args[3]);
            final int requests = Integer.parseInt(args[4]);
            new HelloUDPClient().run(host, port, prefix, threads, requests);
        } catch (NumberFormatException e) {
            Logger.logError("Incorrect arguments. " + e.getMessage());
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.hello client-evil info.kgeorgiy.ja.bozhe.hello.HelloUDPClient