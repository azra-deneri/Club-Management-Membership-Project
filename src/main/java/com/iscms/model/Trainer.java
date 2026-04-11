package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a personal trainer in the system
// Maps to the trainer table in the database
public class Trainer {

    // Unique identifier for the trainer (maps to trainer_id in DB)
    private int trainerId;

    // Full display name of the trainer
    private String fullName;

    // Unique login username (UNIQUE constraint in DB)
    private String username;

    // Email address of the trainer (optional — nullable in DB)
    private String email;

    // BCrypt-hashed password (never stored as plain text)
    private String password;

    // Trainer's area of expertise (e.g., Fitness, Yoga, Swimming, Pilates, HIIT)
    private String specialty;

    // Whether this trainer is currently active and available for bookings
    // false = deactivated by manager, cannot receive new appointments
    private boolean isActive;

    // Number of consecutive failed login attempts (used for lockout logic)
    private int failedAttempts;

    // Whether this trainer account is locked (true = cannot log in)
    private boolean isLocked;

    // Timestamp of when this trainer account was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public Trainer() {}

    // --- Getters and Setters ---

    public int getTrainerId() { return trainerId; }
    public void setTrainerId(int trainerId) { this.trainerId = trainerId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    // Boolean getter uses 'is' prefix — standard Java naming convention
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    // Boolean getter uses 'is' prefix — standard Java naming convention
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}