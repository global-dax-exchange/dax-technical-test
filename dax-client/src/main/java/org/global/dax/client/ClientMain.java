package org.global.dax.client;

import org.global.dax.commands.Add;
import org.global.dax.commands.Delete;
import org.global.dax.commands.Get;
import org.global.dax.commands.Hearbeat;
import org.global.dax.shared.CacheProtocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.global.dax.shared.Properties.HOST;
import static org.global.dax.shared.Properties.PORT;
import static org.global.dax.shared.StringUtil.limitKey;
import static org.global.dax.shared.StringUtil.limitValue;

public final class ClientMain {

    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private SocketChannel channel;
    private Selector selector;
    private boolean running = true;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Map to store pending requests and their callbacks
    private final Map<String, Consumer<CacheProtocol.Message>> pendingRequests = new ConcurrentHashMap<>();

    public void start() throws IOException {
        // Open channel and connect to server
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(HOST, PORT));

        // Create selector and register for connect, read operations
        selector = Selector.open();
        channel.register(selector, SelectionKey.OP_CONNECT);

        // Create a thread for command processing
        executor.submit(this::handleUserInput);

        try {
            // Event loop
            while (running) {
                selector.select();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        finishConnection(key);
                    } else if (key.isReadable()) {
                        read(key);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
            channel.close();
            selector.close();
        }
    }

    public void finishConnection(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            channel.finishConnect();
        } catch (IOException e) {
            key.cancel();
            throw e;
        }

        // Register for read operations
        channel.register(selector, SelectionKey.OP_READ);
        System.out.println("Connected to cache server");
    }

    private void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            // Try to read a message
            CacheProtocol.Message message = CacheProtocol.readMessage(channel, buffer);

            // If message is complete, process it
            if (message != null) {
                handleResponse(message);
            }
        } catch (IOException e) {
            System.err.println("Error reading from server: " + e.getMessage());
            running = false;
        }
    }

    private void handleResponse(CacheProtocol.Message message) {
        String key = message.getKeyAsString();

        // Find and execute the callback for this key
        if (key != null) {
            Consumer<CacheProtocol.Message> callback = pendingRequests.remove(key);
            if (callback != null) {
                callback.accept(message);
            } else {
                System.out.println("Received response: " + message);
            }
        } else {
            System.out.println("Received response: " + message);
        }
    }

    public void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Cache Client");
        System.out.println("Commands: ADD <key> <value>, GET <key>, DELETE <key>, HEARTBEAT, EXIT");

        try {
            while (running) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(input)) {
                    running = false;
                    selector.wakeup();
                    break;
                }

                if ("heartbeat".equalsIgnoreCase(input)) {
                    new Hearbeat(channel, pendingRequests).heartbeat().thenAccept(result -> {
                        System.out.println(result ? "OK" : "FAILED");
                    }).exceptionally(e -> {
                        System.err.println("Error during heartbeat: " + e.getMessage());
                        return null;
                    });
                    break;
                }

                String[] parts = input.split("\\s+", 3);
                String command = parts[0].toLowerCase();
                if (parts.length < 2) {
                    System.out.println("Unknown command");
                    selector.wakeup();
                    break;
                }

                String key = parts[1];
                if (!"ALL".equalsIgnoreCase(key)) {
                    key = limitKey(key);
                }

                switch (command) {
                    case "add":
                        if (parts.length < 3) {
                            System.out.println("Add command requires a value");
                            continue;
                        }
                        String value = parts[2];
                        value = limitValue(value);
                        new Add(channel, pendingRequests).add(key, value).thenAccept(result -> {
                            System.out.println("Add operation " + (result ? "succeeded" : "failed"));
                        }).exceptionally(e -> {
                            System.err.println("Error during add: " + e.getMessage());
                            return null;
                        });
                        break;

                    case "get":
                        new Get(channel, pendingRequests).get(key).thenAccept(result -> {
                            if (result != null) {
                                System.out.println(result);
                            } else {
                                System.out.println("Key not found");
                            }
                        }).exceptionally(e -> {
                            System.err.println("Error during get: " + e.getMessage());
                            return null;
                        });
                        break;

                    case "delete":
                        new Delete(channel, pendingRequests).delete(key).thenAccept(result -> {
                            System.out.println("Delete operation " + (result ? "succeeded" : "failed"));
                        }).exceptionally(e -> {
                            System.err.println("Error during delete: " + e.getMessage());
                            return null;
                        });
                        break;

                    default:
                        System.out.println("Unknown command: " + command);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing command: " + e.getMessage());
            running = false;
            selector.wakeup();
        }
    }

    public static void main(String[] args) {
        try {
            new ClientMain().start();
        } catch (IOException e) {
            System.err.println("Error starting client: " + e.getMessage());
        }
    }
}
