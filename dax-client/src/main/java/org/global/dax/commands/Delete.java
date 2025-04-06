package org.global.dax.commands;

import org.global.dax.shared.CacheProtocol;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Delete {

    private final SocketChannel channel;
    private final Map<String, Consumer<CacheProtocol.Message>> pendingRequests;

    public Delete(SocketChannel channel, Map<String, Consumer<CacheProtocol.Message>> pendingRequests) {
        this.channel = channel;
        this.pendingRequests = pendingRequests;
    }

    public CompletableFuture<Boolean> delete(String key) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            CacheProtocol.Message message = CacheProtocol.createRemoveMessage(key);

            // Register callback for this request
            pendingRequests.put(key, response -> {
                if (response.getOperation() == CacheProtocol.OP_RESPONSE) {
                    future.complete(response.getStatus() == CacheProtocol.STATUS_OK);
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
