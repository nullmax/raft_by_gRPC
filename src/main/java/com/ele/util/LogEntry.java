package com.ele.util;

public class LogEntry {
    public final int term;
    public final int logIndex;
    public final int commandId;
    public final String command;

    LogEntry(int term, int logIndex, int commandId, String command) {
        this.term = term;
        this.logIndex = logIndex;
        this.commandId = commandId;
        this.command = command;
    }

    public void showEntry() {
        System.out.println("term:" + term + ", logIndex:" + logIndex + ", commandId:" + commandId + ", command:\'" + command + "\'");
    }
}
