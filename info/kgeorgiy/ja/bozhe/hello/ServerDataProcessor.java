package info.kgeorgiy.ja.bozhe.hello;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public class ServerDataProcessor {
    private final DatagramSocket socket;
    private final Queue<NonblockingPacket> readyToWrite = new ConcurrentLinkedQueue<>();
    private final Queue<NonblockingPacket> readyToRead = new ConcurrentLinkedQueue<>();
    boolean closed = false;
    private static final Runnable EMPTY_RUNNABLE = () -> {};
    private static final String RESPONSE_PREFIX = "Hello, ";

    public ServerDataProcessor(int threads, int bufferSize) {
        IntStream.range(0, threads).forEach(i -> readyToRead.add(new NonblockingPacket(ByteBuffer.allocate(bufferSize), null)));
        socket = null;
    }

    public ServerDataProcessor(DatagramSocket socket) {
        this.socket = socket;
    }

    public void close() {
        if (socket != null) {
            this.socket.close();
        }
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public void processNewRequest() {
        try {
            DatagramPacket packet = new DatagramPacket(new byte[socket.getSendBufferSize()], socket.getSendBufferSize());
            try {
                socket.receive(packet);
                packet.setData(getResponse(packet));
                socket.send(packet);
            } catch (IOException ignored) {
            }
        } catch (SocketException e) {
            Logger.logError(e.getMessage());
        }
    }

    private static class NonblockingPacket {
        private ByteBuffer buffer;
        private SocketAddress address;

        public NonblockingPacket(ByteBuffer buffer, SocketAddress address) {
            this.buffer = buffer;
            this.address = address;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public SocketAddress getAddress() {
            return address;
        }

        public void setBuffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public void setAddress(SocketAddress address) {
            this.address = address;
        }

    }

    private NonblockingPacket getPacket(SelectionKey key, int status, Selector selector, Queue<NonblockingPacket> container) {
        NonblockingPacket packet = container.remove();
        if (container.isEmpty()) {
            key.interestOpsAnd(~status);
            selector.wakeup();
        }
        return packet;
    }

    private void processPacket(NonblockingPacket packet, int status, SelectionKey key, Selector selector, Queue<NonblockingPacket> container) {
        container.add(packet);
        key.interestOpsOr(status);
        selector.wakeup();
    }

    public Runnable processNewReadable(SelectionKey key, Selector selector) {
        NonblockingPacket packet = getPacket(key, SelectionKey.OP_READ, selector, readyToRead);
        ByteBuffer byteBuffer = packet.getBuffer().clear();
        try {
            SocketAddress socketAddress = ((DatagramChannel) key.channel()).receive(byteBuffer);
            return (() -> {
                packet.setAddress(socketAddress);
                packet.setBuffer(getResponse(byteBuffer));
                processPacket(packet, SelectionKey.OP_WRITE, key, selector, readyToWrite);
            });
        } catch (IOException e) {
            Logger.logError("Socket address is unavailable: " + packet.getAddress());
        }
        return EMPTY_RUNNABLE;
    }

    public void processNewWritable(SelectionKey key, Selector selector) {
        NonblockingPacket packet = getPacket(key, SelectionKey.OP_WRITE, selector, readyToWrite);
        try {
            ((DatagramChannel) key.channel()).send(packet.getBuffer(), packet.getAddress());
        } catch (IOException e) {
            Logger.logError("Socket address is unavailable: " + packet.getAddress());
        }
        processPacket(packet, SelectionKey.OP_READ, key, selector, readyToRead);
    }

    private static ByteBuffer getResponse(ByteBuffer buffer) {
        buffer.flip();
        String request = RESPONSE_PREFIX + StandardCharsets.UTF_8.decode(buffer);
        return ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getResponse(DatagramPacket packet) {
        String response =  RESPONSE_PREFIX + new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        return response.getBytes(StandardCharsets.UTF_8);
    }
}
