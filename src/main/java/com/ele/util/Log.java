package com.ele.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Log {
    private LinkedList<LogEntry> logEntries;    //for local state machine
    private LinkedList<Integer> recentAppended;  //最近添加的日志，防止client未得到响应时重复提交
    private LinkedList<Integer> recentStored;

    private String name;

    private int lastIndexCache = -1;
    private int lastTermCache = -1;
    private boolean indexCacheDirty = true;
    private boolean termCacheDirty = true;

    public int commitIndex;
    public int appliedIndex;

    public Log(int id) {
        buildLog(id);
        name = "log" + id;
        logEntries = new LinkedList<LogEntry>();
        recentAppended = new LinkedList<Integer>();
        recentStored = new LinkedList<Integer>();

        getLastIndex();
        getLastTerm();

        commitIndex = lastIndexCache;
        appliedIndex = commitIndex;

        int temp = lastIndexCache - 10;
        getRecentAppended(temp);
    }

    public static void main(String[] args) {
        Log log = new Log(1);
        log.getLogByIndex(log.getLastIndex()).showEntry();
    }

    private void buildLog(int id) {
        String sql = "CREATE TABLE IF NOT EXISTS log" + id +
                " (term int(11) DEFAULT NULL," +
                " logIndex int(11) DEFAULT NULL," +
                " commandId int(11) DEFAULT NULL," +
                " command varchar(256) DEFAULT NULL)";
        DBConnector.update(sql);
    }

    private void getRecentAppended(int temp) {
        String queryString = "SELECT logIndex, commandId FROM " + name + " WHERE logIndex > " + temp + " ORDER BY logIndex ASC";
        List<Map<String, Object>> results = DBConnector.get(queryString);
        for (Map<String, Object> row : results) {
            this.recentAppended.add((Integer) row.get("commandId"));
        }
    }

    public synchronized int getLastIndex() {
        if (indexCacheDirty) {
            if (logEntries.isEmpty()) {
                String queryString = "SELECT max(logIndex) AS lastLogIndex FROM " + name;
                Object o = DBConnector.get(queryString).get(0).get("lastLogIndex");
                lastIndexCache = (o == null) ? 0 : (Integer) o;
            } else {
                lastIndexCache = logEntries.peekLast().logIndex;
            }
            indexCacheDirty = false;
        }
        return lastIndexCache;
    }

    public synchronized int getLastTerm() {
        if (termCacheDirty) {
            if (logEntries.isEmpty()) {
                String queryString = "SELECT term FROM " + name + " WHERE logIndex = " + getLastIndex();
                List<Map<String, Object>> result = DBConnector.get(queryString);
                lastTermCache = (result.isEmpty() ? 1 : (Integer) result.get(0).get("term"));
            } else {
                lastTermCache = logEntries.peekLast().term;
            }
            termCacheDirty = false;
        }
        return lastTermCache;
    }

    public synchronized LogEntry getLogByIndex(int index) {
        if (index == 0) {
            return new LogEntry(0, 0, -1, null);
        }
        if (logEntries.isEmpty() || logEntries.peekFirst().logIndex > index) {
            String queryString = "SELECT * FROM " + name + " WHERE logIndex = " + index;
            List<Map<String, Object>> entryList = DBConnector.get(queryString);

            if (entryList.isEmpty()) {
                return null;
            } else {
                Map<String, Object> entry = entryList.get(0);
                return new LogEntry((Integer) entry.get("term"),
                        (Integer) entry.get("logIndex"),
                        (Integer) entry.get("commandId"),
                        (String) entry.get("command"));
            }
        } else {
            for (LogEntry logEntry : logEntries) {
                if (logEntry.logIndex == index) {
                    return logEntry;
                }
            }
        }
        return null;
    }

    // delete log from local state machine which index >= begin
    public synchronized void deleteLogEntry(int begin) {
        Iterator<LogEntry> it = logEntries.iterator();
        while (it.hasNext()) {
            LogEntry log = it.next();
            if (log.logIndex >= begin) {
                it.remove();
            }
        }
        indexCacheDirty = true;
        termCacheDirty = true;
    }

    public synchronized void addLogEntry(int term, String command, int commandId) {
        int logIndex = getLastIndex() + 1;
        LogEntry logEntry = new LogEntry(term, logIndex, commandId, command);
        logEntries.add(logEntry);
        indexCacheDirty = true;
        termCacheDirty = true;

        if (this.recentAppended.size() >= 50) {
            this.recentAppended.removeFirst();
        }
        this.recentAppended.add(commandId);
    }

    public synchronized void storeLog() {
        boolean updateFlag = false;
        for (LogEntry logEntry : logEntries) {
            if (!checkStoredBefore(logEntry.commandId)) {
                String sql = "INSERT INTO " + name + " VALUES (" + logEntry.term + "," + logEntry.logIndex + "," + logEntry.commandId + ",\'" + logEntry.command + "\')";
                DBConnector.update(sql);
                updateFlag = true;
                if (this.recentStored.size() >= 500) {
                    this.recentStored.removeFirst();
                }
                this.recentStored.add(logEntry.commandId);
            }
        }

        if (updateFlag) {
            System.out.println(name + " updated!");
        }
    }

    public synchronized void clearLog() {
        Iterator<LogEntry> it = logEntries.iterator();
        while (it.hasNext()) {
            if (it.next().logIndex < appliedIndex / 2) {
                it.remove();
            }
        }
    }

    public boolean checkAppliedBefore(int commandId) {
//        for (int cmdId : this.recentAppended) {
//            if (cmdId == commandId) {
//                return true;
//            }
//        }
        if (recentAppended.contains(commandId))
            return true;

        if (this.recentAppended.size() >= 250) {
            this.recentAppended.removeFirst();
        }
        this.recentAppended.add(commandId);
        return false;
    }

    public boolean checkStoredBefore(int commandId) {
        for (int cmdId : this.recentStored) {
            if (cmdId == commandId) {
                return true;
            }
        }
//        if (this.recentStored.size() >= 50) {
//            this.recentStored.removeFirst();
//        }
//        this.recentStored.add(commandId);
        return false;
    }
}
