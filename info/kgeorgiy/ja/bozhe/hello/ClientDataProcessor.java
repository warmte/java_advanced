package info.kgeorgiy.ja.bozhe.hello;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class ClientDataProcessor {
    private final DatagramSocket socket;
    private final DatagramPacket request;
    private final DatagramPacket response;
    private final String requestFormat;
    private final String responseFormat;
    private final SocketAddress address;

    public ClientDataProcessor(String prefix, int thread, DatagramSocket socket, SocketAddress address) throws SocketException {
        this.address = address;
        this.socket = socket;
        this.socket.setSoTimeout(200);
        this.request = new DatagramPacket(new byte[0], 0, address);
        this.response = new DatagramPacket(new byte[socket.getReceiveBufferSize()], socket.getReceiveBufferSize());
        this.requestFormat = (prefix + thread + "_%d");
        this.responseFormat = "\\D*" + thread + "\\D*%d\\D*";
    }

    public ClientDataProcessor(String prefix, SocketAddress address) {
        this.requestFormat = prefix + "%d_%d";
        this.responseFormat = "\\D*%d\\D*%d\\D*";
        this.address = address;
        socket = null;
        request = null;
        response = null;
    }

    public void processNewResponse(int requestNum) {
        String requestString = String.format(requestFormat, requestNum);
        request.setData(requestString.getBytes(StandardCharsets.UTF_8));
        while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
            try {
                socket.send(request);
                socket.receive(response);
                String responseString = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                if (checkResponse(responseString, String.format(responseFormat, requestNum))) {
                    Logger.logSocketJob(requestString, responseString);
                    break;
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void processWriteable(SelectionKey key) {
        key.interestOps(SelectionKey.OP_WRITE);
        send(key);
    }

    ByteBuffer createEmptyBuffer(SelectionKey key, boolean receive) throws SocketException {
        DatagramSocket socket = ((DatagramChannel) key.channel()).socket();
        int bufferSize = receive ? socket.getReceiveBufferSize() : socket.getSendBufferSize();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.clear();
        return buffer;
    }

    public void processReadable(SelectionKey key) {
        try {
            ByteBuffer response = createEmptyBuffer(key, true);
            ((DatagramChannel) key.channel()).receive(response);
            Utils.Attachment attachment = (Utils.Attachment) key.attachment();
            if (checkResponse(new String(response.array(), StandardCharsets.UTF_8), String.format(responseFormat, attachment.getThread(), attachment.getRequest()))) {
                if (attachment.addRequest()) {
                    key.interestOps(SelectionKey.OP_WRITE);
                } else {
                    key.channel().close();
                }
            } else {
                send(key);
            }
        } catch (IOException e) {
            Logger.logError("Can't send response.");
        }
    }

    public void send(SelectionKey key) {
        try {
            Utils.Attachment attachment = (Utils.Attachment) key.attachment();
            String answer = String.format(requestFormat, attachment.getThread(), attachment.getRequest());
            ByteBuffer request = createEmptyBuffer(key, false);
            request.put(answer.getBytes());
            request.flip();
            ((DatagramChannel) key.channel()).send(request, address);
        } catch (IOException e) {
            Logger.logError("Can't send request.");
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private boolean checkResponse(String responseString, String format) {
        return responseString.matches(format);
    }
}
