package com.iscms.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Singleton class managing the shared database connection
// Implements the Singleton pattern using eager initialization
// Connection credentials are loaded from db.properties at startup
public class DBConnection {

    // Eager initialization — instance created once when class is loaded by JVM
    // Thread-safe without synchronization because class loading is inherently thread-safe
    private static final DBConnection INSTANCE = new DBConnection();

    // Database connection parameters loaded from db.properties
    private String url;
    private String user;
    private String pass;

    // Shared connection object — reused across all DAO calls
    private Connection connection;

    // Private constructor — prevents external instantiation (Singleton pattern)
    // Loads db.properties from classpath and initializes connection parameters
    private DBConnection() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("db.properties");
            if (is == null)
                throw new RuntimeException("db.properties not found in classpath.");
            props.load(is);
            this.url  = props.getProperty("db.url");
            this.user = props.getProperty("db.username");
            this.pass = props.getProperty("db.password");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load DB properties: " + e.getMessage(), e);
        }
    }

    // Returns the single shared instance of DBConnection
    // Called by all DAO implementations via DBConnection.getInstance().getConnection()
    public static DBConnection getInstance() {
        return INSTANCE;
    }

    // Returns a live database connection
    // Auto-reconnects if the connection is null or has been closed
    // Ensures DAOs always receive a usable connection
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, pass);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB connection failed: " + e.getMessage(), e);
        }
        return connection;
    }
}