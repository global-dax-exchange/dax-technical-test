package org.global.dax.commands;

import org.global.dax.shared.CacheProtocol;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Get {

    private final SocketChannel channel;
    private final Map<String, Consumer<CacheProtocol.Message>> pendingRequests;

    public Get(SocketChannel channel, Map<String, Consumer<CacheProtocol.Message>> pendingRequests) {
        this.channel = channel;
        this.pendingRequests = pendingRequests;
    }

    public CompletableFuture<String> get(String key) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            CacheProtocol.Message message = CacheProtocol.createGetMessage(key);

            // Register callback for this request
            pendingRequests.put(key, response -> {
                if (response.getOperation() == CacheProtocol.OP_RESPONSE) {
                    if (response.getStatus() == CacheProtocol.STATUS_OK) {
                        future.complete(response.getValueAsString());
                    } else if (response.getStatus() == CacheProtocol.STATUS_NOT_FOUND) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(
                                new RuntimeException("Error getting value: " + response.getValueAsString()));
                    }
                } else {
                    future.completeExceptionally(
                            new RuntimeException("Unexpected response type: " + response.getOperation()));
                }
            });

            CacheProtocol.sendMessage(channel, message);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }


}
