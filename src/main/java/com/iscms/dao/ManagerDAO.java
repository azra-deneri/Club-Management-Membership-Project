package com.iscms.dao;

import com.iscms.model.Manager;
import java.util.List;
import java.util.Optional;

public interface ManagerDAO {
    void insert(Manager manager); // Create New Manager
    void delete(int managerId); // Delete Manager
    Optional<Manager> findByEmail(String email); // Find email
    List<Manager> findAll(); // Get all managers
    void updateFailedAttempts(int managerId, int attempts); // Count failed login attempts
    void updateLockStatus(int managerId, boolean locked); // Is Manager locked?
    void updatePassword(int managerId, String hashedPassword); // Change password
    void nullifyManagerEvents(int managerId);
}