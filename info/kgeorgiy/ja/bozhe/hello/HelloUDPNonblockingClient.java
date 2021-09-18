package info.kgeorgiy.ja.bozhe.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class HelloUDPNonblockingClient implements HelloClient {
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        Selector selector = Utils.openSelector();
        SocketAddress address = Utils.getSocketAddress(host, port);
        ClientDataProcessor processor = new ClientDataProcessor(prefix, address);

        IntStream.range(0, threads).forEach(id -> {
            try {
                DatagramChannel datagramChannel = Utils.createChannel(selector, SelectionKey.OP_WRITE, id, requests);
                datagramChannel.connect(address);
            } catch (IOException e) {
                Logger.logError("Can't create datagram channel.");
            }
        });

        while (!Thread.interrupted() && selector != null && !selector.keys().isEmpty()) {
            Utils.select(selector);
            if (!selector.selectedKeys().isEmpty()) {
                for (final Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    final SelectionKey key = it.next();
                    if (key.isWritable()) {
                        processor.processWriteable(key);
                    } else if (key.isReadable()) {
                        processor.processReadable(key);
                    }
                    it.remove();
                }
            } else {
                for (final SelectionKey key : selector.keys()) {
                    processor.send(key);
                }
            }
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
            new HelloUDPNonblockingClient().run(host, port, prefix, threads, requests);
        } catch (NumberFormatException e) {
            Logger.logError("Incorrect arguments. " + e.getMessage());
        }
    }
}
