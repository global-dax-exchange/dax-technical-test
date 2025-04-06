package org.global.dax.shared;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A custom protocol for cache operations over TCP using NIO SocketChannels.
 *
 * Protocol format:
 * [4 bytes: Magic number] [1 byte: Version] [1 byte: Operation code] [4 bytes: Key length]
 * [4 bytes: Value length] [n bytes: Key bytes] [m bytes: Value bytes]
 */
public class CacheProtocol {
    // Protocol constants
    public static final int MAGIC_NUMBER = 0x43414348; // "CACH" in ASCII
    public static final byte VERSION = 0x01;

    // Operation codes
    public static final byte OP_ADD = 0x01;
    public static final byte OP_GET = 0x02;
    public static final byte OP_REMOVE = 0x03;
    public static final byte OP_HEARTBEAT = 0x04;
    public static final byte OP_RESPONSE = 0x10;
    public static final byte OP_ERROR = 0x11;

    // Status codes
    public static final byte STATUS_OK = 0x00;
    public static final byte STATUS_NOT_FOUND = 0x01;
    public static final byte STATUS_ERROR = 0x02;

    // Header size constants
    public static final int HEADER_SIZE = 14; // 4 (magic) + 1 (version) + 1 (op) + 4 (key len) + 4 (value len)
    public static final int MAX_KEY_SIZE = 1048576; // 1 MB max key size
    public static final int MAX_VALUE_SIZE = 16777216; // 16 MB max value size

    /**
     * Represents a protocol message with its components
     */
    public static class Message {

        private final byte version;
        private final byte operation;
        private final byte[] key;
        private final byte[] value;
        private byte status;

        // Constructor for request messages (Add, Get, Remove)
        public Message(byte operation, byte[] key, byte[] value) {
            this.version = VERSION;
            this.operation = operation;
            this.key = key;
            this.value = value;
        }

        // Constructor for response messages
        public Message(byte operation, byte status, byte[] key, byte[] value) {
            this.version = VERSION;
            this.operation = operation;
            this.status = status;
            this.key = key;
            this.value = value;
        }

        public byte getVersion() {
            return version;
        }

        public byte getOperation() {
            return operation;
        }

        public byte getStatus() {
            return status;
        }

        public byte[] getKey() {
            return key;
        }

        public byte[] getValue() {
            return value;
        }

        public String getKeyAsString() {
            return key != null ? new String(key, StandardCharsets.UTF_8) : null;
        }

        public String getValueAsString() {
            return value != null ? new String(value, StandardCharsets.UTF_8) : null;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "version=" + version +
                    ", operation=" + operationToString(operation) +
                    ", status=" + (operation == OP_RESPONSE || operation == OP_ERROR ? statusToString(status) : "N/A") +
                    ", keySize=" + (key != null ? key.length : 0) +
                    ", key='" + getKeyAsString() + '\'' +
                    ", valueSize=" + (value != null ? value.length : 0) +
                    ", value='" + (value != null && value.length < 100 ? getValueAsString() : "<binary data>") + '\'' +
                    '}';
        }

        private String operationToString(byte op) {
            switch (op) {
                case OP_ADD: return "ADD";
                case OP_GET: return "GET";
                case OP_REMOVE: return "REMOVE";
                case OP_HEARTBEAT: return "HEARTBEAT";
                case OP_RESPONSE: return "RESPONSE";
                case OP_ERROR: return "ERROR";
                default: return "UNKNOWN(" + op + ")";
            }
        }

        private String statusToString(byte status) {
            switch (status) {
                case STATUS_OK: return "OK";
                case STATUS_NOT_FOUND: return "NOT_FOUND";
                case STATUS_ERROR: return "ERROR";
                default: return "UNKNOWN(" + status + ")";
            }
        }
    }

    /**
     * Creates an ADD message
     */
    public static Message createAddMessage(String key, String value) {
        return new Message(OP_ADD,
                key.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a GET message
     */
    public static Message createGetMessage(String key) {
        return new Message(OP_GET,
                key.getBytes(StandardCharsets.UTF_8),
                new byte[0]);
    }

    /**
     * Creates a REMOVE message
     */
    public static Message createRemoveMessage(String key) {
        return new Message(OP_REMOVE,
                key.getBytes(StandardCharsets.UTF_8),
                new byte[0]);
    }

    /**
     * Creates a HEARTBEAT message
     */
    public static Message createHeartbeatMessage() {
        return new Message(OP_HEARTBEAT, "heartbeat".getBytes(StandardCharsets.UTF_8),new byte[0]);
    }

    /**
     * Creates a response message
     */
    public static Message createResponseMessage(byte status, byte[] key, byte[] value) {
        return new Message(OP_RESPONSE, status, key, value);
    }

    /**
     * Creates an error message
     */
    public static Message createErrorMessage(String errorMessage) {
        return new Message(OP_ERROR, STATUS_ERROR, new byte[0],
                errorMessage.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends a message through the specified SocketChannel
     */
    public static void sendMessage(SocketChannel channel, Message message) throws IOException {
        byte[] key = message.getKey() != null ? message.getKey() : new byte[0];
        byte[] value = message.getValue() != null ? message.getValue() : new byte[0];

        if (key.length > MAX_KEY_SIZE) {
            throw new IllegalArgumentException("Key exceeds maximum size of " + MAX_KEY_SIZE + " bytes");
        }
        if (value.length > MAX_VALUE_SIZE) {
            throw new IllegalArgumentException("Value exceeds maximum size of " + MAX_VALUE_SIZE + " bytes");
        }

        // Create buffer for the complete message (header + key + value)
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + key.length + value.length);

        // Write header
        buffer.putInt(MAGIC_NUMBER);
        buffer.put(VERSION);
        buffer.put(message.getOperation());
        buffer.putInt(key.length);
        buffer.putInt(value.length);

        // Write key and value if present
        if (key.length > 0) {
            buffer.put(key);
        }
        if (value.length > 0) {
            buffer.put(value);
        }

        // Prepare buffer for reading by the channel
        buffer.flip();

        // Write the entire buffer to the channel
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Reads a message from the specified SocketChannel
     * Returns null if the message is incomplete and more data is needed
     */
    public static Message readMessage(SocketChannel channel, ByteBuffer buffer) throws IOException {
        // First, try to read enough for the header
        if (buffer.position() < HEADER_SIZE) {
            channel.read(buffer);

            // If we still don't have enough for the header, return null
            if (buffer.position() < HEADER_SIZE) {
                return null;
            }
        }

        // Prepare buffer for reading
        buffer.flip();

        // Read and validate header fields
        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid magic number: " + Integer.toHexString(magic));
        }

        byte version = buffer.get();
        if (version != VERSION) {
            throw new IOException("Unsupported protocol version: " + version);
        }

        byte operation = buffer.get();
        int keyLength = buffer.getInt();
        int valueLength = buffer.getInt();

        if (keyLength < 0 || keyLength > MAX_KEY_SIZE) {
            throw new IOException("Invalid key length: " + keyLength);
        }
        if (valueLength < 0 || valueLength > MAX_VALUE_SIZE) {
            throw new IOException("Invalid value length: " + valueLength);
        }

        // Calculate total message size
        int totalMessageSize = HEADER_SIZE + keyLength + valueLength;

        // Check if we have the complete message
        if (buffer.limit() < totalMessageSize) {
            // Restore position to continue reading
            buffer.position(buffer.limit());
            buffer.limit(buffer.capacity());

            // Read more data
            channel.read(buffer);

            // Check if we now have enough data
            if (buffer.position() < totalMessageSize) {
                // Still not enough data
                buffer.flip();
                buffer.position(buffer.limit());
                buffer.limit(buffer.capacity());
                return null;
            }

            // Reset for reading
            buffer.flip();

            // Skip header we've already processed
            buffer.position(HEADER_SIZE);
        }

        // Read key
        byte[] key = null;
        if (keyLength > 0) {
            key = new byte[keyLength];
            buffer.get(key);
        }

        // Read value
        byte[] value = null;
        if (valueLength > 0) {
            value = new byte[valueLength];
            buffer.get(value);
        }

        // Create message based on operation
        Message message;
        if (operation == OP_RESPONSE || operation == OP_HEARTBEAT || operation == OP_ERROR) {
            // For responses, first byte of value is status code
            byte status = (value != null && value.length > 0) ? value[0] : STATUS_OK;
            byte[] actualValue = (value != null && value.length > 1) ?
                    Arrays.copyOfRange(value, 1, value.length) : new byte[0];
            message = new Message(operation, status, key, actualValue);
        } else {
            message = new Message(operation, key, value);
        }

        // If there's more data, compact the buffer
        if (buffer.hasRemaining()) {
            buffer.compact();
        } else {
            buffer.clear();
        }

        return message;
    }
}