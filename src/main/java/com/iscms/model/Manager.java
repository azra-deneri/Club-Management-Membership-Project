package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a manager or admin user in the system
// Maps to the manager table in the database
public class Manager {

    // Unique identifier for the manager (maps to manager_id in DB)
    private int managerId;

    // Full display name of the manager
    private String fullName;

    // Unique login username (UNIQUE constraint in DB)
    private String username;

    // Unique email address (UNIQUE constraint in DB)
    private String email;

    // Role of this user — either ADMIN or MANAGER
    // ADMIN: can manage other managers | MANAGER: handles members, events, trainers
    private String role;

    // BCrypt-hashed password (never stored as plain text)
    private String password;

    // Number of consecutive failed login attempts (used for lockout logic)
    private int failedAttempts;

    // Whether this account is locked (true = cannot log in)
    private boolean isLocked;

    // Timestamp of when this manager account was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public Manager() {}

    // --- Getters and Setters ---

    public int getManagerId() { return managerId; }
    public void setManagerId(int managerId) { this.managerId = managerId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    // Boolean getter uses 'is' prefix — standard Java naming convention
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}