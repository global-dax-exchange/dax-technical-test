package org.global.dax.server;

import org.global.dax.handlers.CacheMessageHandler;
import org.global.dax.shared.CacheProtocol;
import org.global.dax.shared.MessageHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.global.dax.shared.Properties.PORT;

public final class ServerMain {
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    // The cache storage using ConcurrentHashMap
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    // Store buffers for each client connection
    private final Map<SocketChannel, ByteBuffer> clientBuffers = new HashMap<>();

    // Protocol message handler
    private final MessageHandler messageHandler = new CacheMessageHandler(cache);

    public void start() throws IOException {
        // Create selector
        Selector selector = Selector.open();

        // Create server socket channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Cache server started on port " + PORT);

        // Process events
        while (true) {
            selector.select();

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    accept(selector, key);
                } else if (key.isReadable()) {
                    read(key);
                }
            }
        }
    }

    private void accept(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // Create a buffer for this client
        clientBuffers.put(clientChannel, ByteBuffer.allocate(BUFFER_SIZE));

        System.out.println("Accepted connection from " + clientChannel.getRemoteAddress());
    }

    private void read(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = clientBuffers.get(clientChannel);

        try {
            // Try to read a message
            CacheProtocol.Message message = CacheProtocol.readMessage(clientChannel, buffer);

            // If message is complete, process it
            if (message != null) {
                System.out.println("Received from " + clientChannel.getRemoteAddress() + ": " + message);
                messageHandler.handleMessage(message, clientChannel);
            }
        } catch (IOException e) {
            System.err.println("Error reading from client: " + e.getMessage());
            closeConnection(clientChannel);
        }
    }

    private void closeConnection(SocketChannel channel) {
        try {
            System.out.println("Closing connection with " + channel.getRemoteAddress());
            channel.close();
            clientBuffers.remove(channel);
        } catch (IOException e) {
            System.err.println("An exception occurred when closed the connection: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            new ServerMain().start();
        } catch (IOException e) {
            System.err.println("An exception occurred when started the server: " + e.getMessage());
        }
    }
}

