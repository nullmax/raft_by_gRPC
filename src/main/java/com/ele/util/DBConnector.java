package com.ele.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBConnector {
    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";


    private static final String DB_URL = "jdbc:mysql://localhost/test?useSSL=false";
    private static final String USER = "root";
    private static final String PASSWORD = "toor";

    private static Connection conn;

    /**
     * 获取数据库链接
     *
     * @return
     */
    private static Connection getConn() {
        if (conn != null)
            return conn;
        try {
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException(e);
        }
        return conn;
    }

    /**
     * 将查询结果的每一列存储到map，并将所有列存入到一个List
     *
     * @param r JDBC的查询结果
     * @return
     */
    private static List<Map<String, Object>> resultSet2Obj(ResultSet r) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try {
            ResultSetMetaData rsmd = r.getMetaData();
            int numberOfColumns = rsmd.getColumnCount();
            while (r.next()) {
                Map<String, Object> row = new HashMap<String, Object>();
                for (int i = 1; i <= numberOfColumns; ++i) {
                    String name = rsmd.getColumnName(i);
                    Object value = r.getObject(name);
                    row.put(name, value);
                }
                result.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 执行数据库查询指令
     *
     * @param sql
     * @return
     */
    public static List<Map<String, Object>> get(String sql) {
        List<Map<String, Object>> results = null;
        ResultSet rs = null;
        Connection conn = getConn();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            results = resultSet2Obj(rs);

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    /**
     * 执行数据库update语句
     *
     * @param sql
     * @return
     */
    public static boolean update(String sql) {
        Connection conn = getConn();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.executeUpdate(sql);

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * 释放链接
     */
    public static void releaseConn() {
        if (conn != null) {
            synchronized (conn) {
                try {
                    conn.close();
                    conn = null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
