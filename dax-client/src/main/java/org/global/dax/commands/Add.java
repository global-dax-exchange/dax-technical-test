package org.global.dax.commands;

import org.global.dax.shared.CacheProtocol;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Add {

    private final SocketChannel channel;
    private final Map<String, Consumer<CacheProtocol.Message>> pendingRequests;

    public Add(SocketChannel channel, Map<String, Consumer<CacheProtocol.Message>> pendingRequests) {
        this.channel = channel;
        this.pendingRequests = pendingRequests;
    }

    public CompletableFuture<Boolean> add(String key, String value) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            CacheProtocol.Message message = CacheProtocol.createAddMessage(key, value);

            // Register callback for this request
            pendingRequests.put(key, response -> {
                if (response.getOperation() == CacheProtocol.OP_RESPONSE &&
                        response.getStatus() == CacheProtocol.STATUS_OK) {
                    future.complete(true);
                } else {
                    future.complete(false);
                }
            });

            CacheProtocol.sendMessage(channel, message);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

}
