package info.kgeorgiy.ja.bozhe.hello;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.IntStream;

public class Utils {
    public static Selector openSelector() {
        try {
            return Selector.open();
        } catch (IOException e) {
            Logger.logError("Selector can't be opened.");
        }
        return null;
    }

    public static void select(Selector selector) {
        try {
            selector.select(200);
        } catch (IOException e) {
            Logger.logError("Selector is unavailable.");
        }
    }

    public static SocketAddress getSocketAddress(String host, int port)  {
        try {
            return new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            Logger.logError("Host is unavailable: " + host);
        }
        return null;
    }

    public static DatagramChannel createChannel(Selector selector, int status, int id, int requests) throws IOException {
        DatagramChannel datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        datagramChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        datagramChannel.register(selector, status, new Attachment(id, requests));
        return datagramChannel;
    }

    public static ExecutorService createThreadPool(int threads, Runnable task) {
        ExecutorService service = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEach(x -> service.submit(task));
        return service;
    }

    public static class Attachment {
        private int request;
        private final int thread;
        private final int limit;

        Attachment(int thread, int limit) {
            this.request = 0;
            this.thread = thread;
            this.limit = limit;
        }

        public int getRequest() {
            return request;
        }

        public int getThread() {
            return thread;
        }

        public boolean addRequest() {
            request += 1;
            return request < limit;
        }
    }
}
