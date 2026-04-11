package com.iscms.dao;

import com.iscms.model.Trainer;
import com.iscms.model.TrainerWorkingDay;
import com.iscms.model.TrainerLessonSlot;
import java.util.List;
import java.util.Optional;

// DAO interface for trainer database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface TrainerDAO {

    // Insert a new trainer record into the database
    void insert(Trainer trainer);

    // Find a trainer by username — used during login authentication
    Optional<Trainer> findByUsername(String username);

    // Find a trainer by their ID — returns Optional.empty() if not found
    Optional<Trainer> findById(int trainerId);

    // Return all trainer records regardless of active status
    // Used in manager dashboard trainer management panel
    List<Trainer> findAll();

    // Return only active trainers (is_active = true)
    // Used in PT booking — members can only book with active trainers
    List<Trainer> findActive();

    // Update the number of consecutive failed login attempts for a trainer
    // Used for lockout logic
    void updateFailedAttempts(int trainerId, int attempts);

    // Lock or unlock a trainer account
    // true = locked (cannot log in), false = unlocked
    void updateLockStatus(int trainerId, boolean locked);

    // Update the hashed password for a trainer
    // Always stores BCrypt hash — plain text passwords are never stored
    void updatePassword(int trainerId, String hashedPassword);

    // Replace all working day records for a trainer with the given list
    // Deletes existing records first, then inserts new ones (delete + insert pattern)
    void saveWorkingDays(int trainerId, List<TrainerWorkingDay> days);

    // Replace all lesson slot records for a trainer with the given list
    // Deletes existing records first, then inserts new ones (delete + insert pattern)
    void saveLessonSlots(int trainerId, List<TrainerLessonSlot> slots);

    // Update the editable info fields of a trainer (full name, username, specialty)
    void updateInfo(int trainerId, String fullName, String username, String specialty);

    // Activate or deactivate a trainer
    // false = deactivated, cannot receive new PT appointments
    void updateActive(int trainerId, boolean active);

    // Return all working day records for a given trainer
    // Used in trainer schedule view and PT booking availability check
    List<TrainerWorkingDay> findWorkingDays(int trainerId);

    // Return all lesson slot records for a given trainer
    // Used in member PT booking panel (PTPanel) to show available time slots
    List<TrainerLessonSlot> findLessonSlots(int trainerId);
}