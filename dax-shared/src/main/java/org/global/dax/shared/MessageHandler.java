package org.global.dax.shared;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Protocol handler interface for processing messages
 */
public interface MessageHandler {
    void handleMessage(CacheProtocol.Message message, SocketChannel channel) throws IOException;
}
