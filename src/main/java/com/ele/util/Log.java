package com.ele.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Log {
    private ConcurrentHashMap<Integer, LogEntry> logEntries;    //for local state machine
    private ConcurrentHashMap<Integer, Integer> recentAppended;  //最近添加的日志，防止client未得到响应时重复提交

    private String name;

    private int lastIndexCache = -1;
    private int lastTermCache = -1;
    private boolean indexCacheDirty = true;
    private boolean termCacheDirty = true;


    private AtomicInteger indexStart;
    private AtomicInteger indexEnd;
    public int commitIndex;
    public int appliedIndex;

    private int storedLogIndex;

    public Log(int id) {
        buildLog(id);
        name = "log" + id;
        logEntries = new ConcurrentHashMap<Integer, LogEntry>();
        recentAppended = new ConcurrentHashMap<Integer, Integer>();

        getLastIndex();
        getLastTerm();

        indexStart = new AtomicInteger(lastIndexCache);
        indexEnd = new AtomicInteger(lastIndexCache);

        commitIndex = lastIndexCache;
        appliedIndex = commitIndex;

        storedLogIndex = getLastIndex();
        int temp = lastIndexCache - 10;
        getRecentAppended(temp);
    }

    /**
     * 创建测试用的表和存储日志的表
     *
     * @param id 服务器的id
     */
    private void buildLog(int id) {
        String sql = "CREATE TABLE IF NOT EXISTS simple" +
                " (id int(11) DEFAULT NULL," +
                " v int(11) DEFAULT NULL)";
        DBConnector.update(sql);

        sql = "CREATE TABLE IF NOT EXISTS log" + id +
                " (term int(11) DEFAULT NULL," +
                " logIndex int(11) DEFAULT NULL," +
                " commandId int(11) DEFAULT NULL," +
                " command varchar(256) DEFAULT NULL)";
        DBConnector.update(sql);
    }

    /**
     * 初始化时从数据库获取最近加入的entry的commandID
     *
     * @param temp 获取日志的目录起始点
     */
    private void getRecentAppended(int temp) {
        String queryString = "SELECT logIndex, commandId FROM " + name + " WHERE logIndex > " + temp + " ORDER BY logIndex ASC";
        List<Map<String, Object>> results = DBConnector.get(queryString);
        for (Map<String, Object> row : results) {
            this.recentAppended.put((Integer) row.get("commandId"), (Integer) row.get("logIndex"));
        }
    }

    /**
     * 获取AppliedIndex
     */
    public void getAppliedIndex() {
        String queryString = "SELECT count(*) AS lastIndex FROM " + "simple";
        Object o = DBConnector.get(queryString).get(0).get("lastIndex");
        long Index = (o == null) ? 0 : (Long) o; //不能直接转成Integer
        appliedIndex = (int) Index;
    }

    /**
     * 获取最后一条日志的Index
     *
     * @return
     */
    public int getLastIndex() {
        if (indexCacheDirty) {
            if (logEntries.isEmpty()) {
                String queryString = "SELECT max(logIndex) AS lastLogIndex FROM " + name;
                Object o = DBConnector.get(queryString).get(0).get("lastLogIndex");
                lastIndexCache = (o == null) ? 0 : (Integer) o;
            } else {
                lastIndexCache = indexEnd.get();
            }
            indexCacheDirty = false;
        }
        return lastIndexCache;
    }

    /**
     * 获取最后一条日志的term
     *
     * @return
     */
    public int getLastTerm() {
        if (termCacheDirty) {
            if (logEntries.isEmpty()) {
                String queryString = "SELECT term FROM " + name + " WHERE logIndex = " + getLastIndex();
                List<Map<String, Object>> result = DBConnector.get(queryString);
                lastTermCache = (result.isEmpty() ? 1 : (Integer) result.get(0).get("term"));
            } else {
                lastTermCache = logEntries.get(getLastIndex()).term;
            }
            termCacheDirty = false;
        }
        return lastTermCache;
    }

    /**
     * 通过日志目录获取日志entry
     *
     * @param index
     * @return
     */
    public LogEntry getLogByIndex(int index) {
        if (index == 0) {
            return new LogEntry(0, 0, -1, null);
        }

        LogEntry logEntry = logEntries.get(indexStart.get());
        if (logEntries.isEmpty() || logEntry != null && logEntry.logIndex > index) {
            String queryString = "SELECT * FROM " + name + " WHERE logIndex = " + index;
            List<Map<String, Object>> entryList = DBConnector.get(queryString);
            if (entryList.isEmpty()) {
                System.out.println(index);
                System.exit(1);
                return null;
            } else {
                Map<String, Object> entry = entryList.get(0);
                return new LogEntry((Integer) entry.get("term"),
                        (Integer) entry.get("logIndex"),
                        (Integer) entry.get("commandId"),
                        (String) entry.get("command"));
            }

        } else {
            if (logEntries.get(index) == null) {
                System.out.println(index);
                System.exit(1);
            }
            return logEntries.get(index);
        }
    }

    /**
     * 删除Index >= begin的日志
     *
     * @param begin
     */
    public void deleteLogEntry(int begin) {
        for (int i = begin; i < indexEnd.get(); ++i)
            logEntries.remove(i);
        indexEnd.set(begin - 1);
        indexCacheDirty = true;
        termCacheDirty = true;
    }

    /**
     * 添加新的日志entry
     *
     * @param term
     * @param command
     * @param commandId
     */
    public void addLogEntry(int term, String command, int commandId) {
        int logIndex = indexEnd.incrementAndGet();
        LogEntry logEntry = new LogEntry(term, logIndex, commandId, command);
        logEntries.put(logIndex, logEntry);
        indexCacheDirty = true;
        termCacheDirty = true;

        if (recentAppended.size() >= 1000) {
            Iterator it = recentAppended.entrySet().iterator();
            it.next();
            it.remove();
        }
        recentAppended.put(commandId, logIndex);
    }

    /**
     * 将日志存储到数据库
     */
    public void storeLog() {
        int counter = 0;
        while (counter < 100 && storedLogIndex < getLastIndex()) {
            try {
                LogEntry logEntry = getLogByIndex(++storedLogIndex);
                String sql = "INSERT INTO " + name + " VALUES (" + logEntry.term + "," + logEntry.logIndex + "," + logEntry.commandId + ",\'" + logEntry.command + "\')";
                DBConnector.update(sql);
                ++counter;
            } catch (NullPointerException npe) {
                System.out.println(getLastIndex() + ", " + storedLogIndex);
                System.exit(1);
            }
        }
    }

    /**
     * 将已经应用并且存储到数据库的日志删除
     */
    public void clearLog() {
        int endIndex = Math.min(storedLogIndex, appliedIndex) - 100;
        for (int i = indexStart.get(); i < endIndex; ++i)
            logEntries.remove(i);
        indexStart.set(endIndex);
    }

    /**
     * 查看该条命令是否已经被应用
     *
     * @param commandId
     * @return
     */
    public boolean checkAppliedBefore(int commandId) {
        if (recentAppended.get(commandId) != null && recentAppended.get(commandId) <= appliedIndex)
            return true;
        return false;
    }

    /**
     * 查看该条命令是否已经添加到日志中
     *
     * @param commandId
     * @return
     */
    public boolean checkAppendBefore(int commandId) {
        if (recentAppended.get(commandId) != null)
            return true;
        return false;
    }
}
