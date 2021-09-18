package info.kgeorgiy.ja.bozhe.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class HelloUDPNonblockingServer implements HelloServer {
    private ServerDataProcessor processor;
    private ExecutorService helloService;
    private DatagramChannel channel;
    private Selector selector;

    @Override
    public void start(int port, int threads) {
        selector = Utils.openSelector();

        try {
            channel = Utils.createChannel(selector, SelectionKey.OP_READ, 0, 0);
            channel.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            Logger.logError("Channel can't register the port: " + port, e);
            return;
        }

        try {
            processor = new ServerDataProcessor(threads, channel.socket().getReceiveBufferSize());
        } catch (SocketException e) {
            Logger.logError("Can't get socket buffer size.");
        }
        helloService = Executors.newFixedThreadPool(threads + 1);

        helloService.submit(() -> {
            while (!Thread.interrupted() && !processor.isClosed() && !channel.socket().isClosed()) {
                Utils.select(selector);
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext() && !processor.isClosed(); ) {
                    final SelectionKey key = it.next();
                    if (key.isReadable()) {
                        helloService.submit(processor.processNewReadable(key, selector));
                    } else if (key.isWritable()) {
                        processor.processNewWritable(key, selector);
                    }
                    it.remove();
                }
            }
        });
    }

    @Override
    public void close() {
        processor.close();
        try {
            selector.close();
        } catch (IOException e) {
            Logger.logError("Selector can't be closed.", e);
        }
        try {
            channel.close();
        } catch (IOException e) {
            Logger.logError("Channel can't be closed.", e);
        }
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
            new HelloUDPNonblockingServer().start(port, threads);
        } catch (NumberFormatException e) {
            Logger.logError("Incorrect arguments. " + e.getMessage());
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.hello server info.kgeorgiy.ja.bozhe.hello.HelloUDPNonblockingServer
