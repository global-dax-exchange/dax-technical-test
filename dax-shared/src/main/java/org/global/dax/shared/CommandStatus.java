package org.global.dax.shared;

public enum CommandStatus {
    UNKNOWN("UNKNOWN COMMAND"),
    NOT_FOUND("NOT FOUND"),
    OK("OK");

    public final String label;

    CommandStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
