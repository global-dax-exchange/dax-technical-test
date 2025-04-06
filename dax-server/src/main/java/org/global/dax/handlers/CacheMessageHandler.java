package org.global.dax.handlers;

import org.global.dax.shared.CacheProtocol;
import org.global.dax.shared.MessageHandler;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

// Message handler implementation for cache operations
public class CacheMessageHandler implements MessageHandler {

    private final Map<String, byte[]> cache;

    public CacheMessageHandler(Map<String, byte[]> cache) {
        this.cache = cache;
    }

    @Override
    public void handleMessage(CacheProtocol.Message message, SocketChannel channel) throws IOException {
        try {
            switch (message.getOperation()) {
                case CacheProtocol.OP_ADD:
                    handleAddOperation(message, channel);
                    break;

                case CacheProtocol.OP_GET:
                    handleGetOperation(message, channel);
                    break;

                case CacheProtocol.OP_REMOVE:
                    handleRemoveOperation(message, channel);
                    break;

                case CacheProtocol.OP_HEARTBEAT:
                    handleHeartbeatOperation(channel);
                    break;

                default:
                    sendErrorResponse(channel, "Unsupported operation: " + message.getOperation());
            }
        } catch (Exception e) {
            sendErrorResponse(channel, "Server error: " + e.getMessage());
        }
    }

    private void handleAddOperation(CacheProtocol.Message message, SocketChannel channel) throws IOException {
        String key = message.getKeyAsString();
        byte[] value = message.getValue();

        // Store in cache
        cache.put(key, value);

        // Send success response
        CacheProtocol.Message response = CacheProtocol.createResponseMessage(
                CacheProtocol.STATUS_OK,
                message.getKey(),
                new byte[]{CacheProtocol.STATUS_OK});

        CacheProtocol.sendMessage(channel, response);
        System.out.println("Added key: " + key + ", value size: " + value.length + " bytes");
    }

    private void handleGetOperation(CacheProtocol.Message message, SocketChannel channel) throws IOException {
        String key = message.getKeyAsString();
        CacheProtocol.Message response;

        if ("ALL".equalsIgnoreCase(key)) {
            byte[] value;
            if (cache.isEmpty()) {
                value = "NO KEY IN CACHE".getBytes();
            } else {
                value = convertMaptoString(cache).getBytes();
            }
            // Create response with value
            byte[] responseValue = new byte[value.length + 1];
            responseValue[0] = CacheProtocol.STATUS_OK;
            System.arraycopy(value, 0, responseValue, 1, value.length);

            response = CacheProtocol.createResponseMessage(
                    CacheProtocol.STATUS_OK,
                    message.getKey(),
                    responseValue);

            System.out.println("Retrieved all cache keys");
            CacheProtocol.sendMessage(channel, response);
            return;
        }
        byte[] value = cache.get(key);


        if (value != null) {
            // Create response with value
            byte[] responseValue = new byte[value.length + 1];
            responseValue[0] = CacheProtocol.STATUS_OK;
            System.arraycopy(value, 0, responseValue, 1, value.length);

            response = CacheProtocol.createResponseMessage(
                    CacheProtocol.STATUS_OK,
                    message.getKey(),
                    responseValue);

            System.out.println("Retrieved key: " + key + ", value size: " + value.length + " bytes");
        } else {
            // Key not found
            response = CacheProtocol.createResponseMessage(
                    CacheProtocol.STATUS_NOT_FOUND,
                    message.getKey(),
                    new byte[]{CacheProtocol.STATUS_NOT_FOUND});

            System.out.println("Key not found: " + key);
        }

        CacheProtocol.sendMessage(channel, response);
    }

    public String convertMaptoString(Map<String, byte[]> map) {
        return map.keySet().stream().sorted()
                .map(key -> key + ": " + new String(map.get(key)))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private void handleRemoveOperation(CacheProtocol.Message message, SocketChannel channel) throws IOException {
        String key = message.getKeyAsString();
        byte[] removedValue = cache.remove(key);

        CacheProtocol.Message response;
        if (removedValue != null) {
            // Successfully removed
            response = CacheProtocol.createResponseMessage(
                    CacheProtocol.STATUS_OK,
                    message.getKey(),
                    new byte[]{CacheProtocol.STATUS_OK});

            System.out.println("Removed key: " + key);
        } else {
            // Key not found
            response = CacheProtocol.createResponseMessage(
                    CacheProtocol.STATUS_NOT_FOUND,
                    message.getKey(),
                    new byte[]{CacheProtocol.STATUS_NOT_FOUND});

            System.out.println("Remove failed, key not found: " + key);
        }

        CacheProtocol.sendMessage(channel, response);
    }

    private void handleHeartbeatOperation(SocketChannel channel) throws IOException {
        // Heartbeat
        CacheProtocol.Message response  = CacheProtocol.createResponseMessage(
                CacheProtocol.STATUS_OK,
                "heartbeat".getBytes(StandardCharsets.UTF_8),
                new byte[]{CacheProtocol.STATUS_OK});

        System.out.println("Heartbeat operation executed on server");
        CacheProtocol.sendMessage(channel, response);
    }

    private void sendErrorResponse(SocketChannel channel, String errorMessage) throws IOException {
        CacheProtocol.Message response = CacheProtocol.createErrorMessage(errorMessage);
        CacheProtocol.sendMessage(channel, response);
    }
}