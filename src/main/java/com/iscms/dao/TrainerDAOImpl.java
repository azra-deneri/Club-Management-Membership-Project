package com.iscms.dao;

import com.iscms.model.Trainer;
import com.iscms.model.TrainerLessonSlot;
import com.iscms.model.TrainerWorkingDay;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// All SQL operations for trainer, trainer_working_day, trainer_lesson_slot tables live here
// Service layer never touches SQL — only calls this via the TrainerDAO interface
public class TrainerDAOImpl implements TrainerDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new trainer record into the database
    // failed_attempts, is_locked, created_at are omitted — DB sets defaults automatically
    @Override
    public void insert(Trainer t) {
        String sql = "INSERT INTO trainer (full_name, username, email, password, specialty, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getFullName());
            ps.setString(2, t.getUsername());
            ps.setString(3, t.getEmail());
            // Password must already be BCrypt-hashed before calling this method
            ps.setString(4, t.getPassword());
            ps.setString(5, t.getSpecialty());
            ps.setBoolean(6, t.isActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert trainer failed: " + e.getMessage(), e);
        }
    }

    // Finds a trainer by username — used during login authentication
    @Override
    public Optional<Trainer> findByUsername(String username) {
        String sql = "SELECT * FROM trainer WHERE username = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Finds a trainer by their ID — returns Optional.empty() if not found
    @Override
    public Optional<Trainer> findById(int trainerId) {
        String sql = "SELECT * FROM trainer WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Returns all trainer records ordered alphabetically by full name
    // Used in manager dashboard trainer management panel
    @Override
    public List<Trainer> findAll() {
        List<Trainer> list = new ArrayList<>();
        String sql = "SELECT * FROM trainer ORDER BY full_name";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll trainers failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Returns only active trainers (is_active = 1), ordered alphabetically
    // Used in PT booking — members can only book appointments with active trainers
    @Override
    public List<Trainer> findActive() {
        List<Trainer> list = new ArrayList<>();
        String sql = "SELECT * FROM trainer WHERE is_active = 1 ORDER BY full_name";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findActive failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates the failed login attempt count for a trainer
    // Called after each failed login — lockout triggered at threshold
    @Override
    public void updateFailedAttempts(int trainerId, int attempts) {
        String sql = "UPDATE trainer SET failed_attempts = ? WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            ps.setInt(2, trainerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateFailedAttempts failed: " + e.getMessage(), e);
        }
    }

    // Locks or unlocks a trainer account
    // true = locked (cannot log in), false = unlocked
    @Override
    public void updateLockStatus(int trainerId, boolean locked) {
        String sql = "UPDATE trainer SET is_locked = ? WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, locked);
            ps.setInt(2, trainerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateLockStatus failed: " + e.getMessage(), e);
        }
    }

    // Updates the hashed password for a trainer
    // Always stores BCrypt hash — plain text passwords are never stored
    @Override
    public void updatePassword(int trainerId, String hashedPassword) {
        String sql = "UPDATE trainer SET password = ? WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashedPassword);
            ps.setInt(2, trainerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePassword failed: " + e.getMessage(), e);
        }
    }

    // Returns all working day records for a given trainer
    // Used in trainer schedule view and PT booking availability check
    @Override
    public List<TrainerWorkingDay> findWorkingDays(int trainerId) {
        List<TrainerWorkingDay> list = new ArrayList<>();
        String sql = "SELECT * FROM trainer_working_day WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TrainerWorkingDay wd = new TrainerWorkingDay();
                    wd.setWdId(rs.getInt("wd_id"));
                    wd.setTrainerId(rs.getInt("trainer_id"));
                    wd.setDayOfWeek(rs.getString("day_of_week"));
                    // Convert SQL Time → Java LocalTime
                    wd.setStartTime(rs.getTime("start_time").toLocalTime());
                    wd.setEndTime(rs.getTime("end_time").toLocalTime());
                    list.add(wd);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findWorkingDays failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Returns all lesson slot records for a given trainer
    // Used in member PT booking panel (PTPanel) to show available time slots
    @Override
    public List<TrainerLessonSlot> findLessonSlots(int trainerId) {
        List<TrainerLessonSlot> list = new ArrayList<>();
        String sql = "SELECT * FROM trainer_lesson_slot WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TrainerLessonSlot slot = new TrainerLessonSlot();
                    slot.setSlotId(rs.getInt("slot_id"));
                    slot.setTrainerId(rs.getInt("trainer_id"));
                    slot.setDayOfWeek(rs.getString("day_of_week"));
                    // Convert SQL Time → Java LocalTime
                    slot.setStartTime(rs.getTime("start_time").toLocalTime());
                    slot.setEndTime(rs.getTime("end_time").toLocalTime());
                    list.add(slot);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findLessonSlots failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Replaces all working day records for a trainer with the given list
    // Delete + insert pattern on the same connection ensures consistency
    @Override
    public void saveWorkingDays(int trainerId, List<TrainerWorkingDay> days) {
        String deleteSql = "DELETE FROM trainer_working_day WHERE trainer_id = ?";
        String insertSql = "INSERT INTO trainer_working_day (trainer_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConn()) {
            // Step 1: Delete all existing working days for this trainer
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, trainerId);
                ps.executeUpdate();
            }
            // Step 2: Insert the new working days one by one
            for (TrainerWorkingDay wd : days) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, trainerId);
                    ps.setString(2, wd.getDayOfWeek());
                    // Convert Java LocalTime → SQL Time
                    ps.setTime(3, Time.valueOf(wd.getStartTime()));
                    ps.setTime(4, Time.valueOf(wd.getEndTime()));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("saveWorkingDays failed: " + e.getMessage(), e);
        }
    }

    // Replaces all lesson slot records for a trainer with the given list
    // Delete + insert pattern on the same connection ensures consistency
    @Override
    public void saveLessonSlots(int trainerId, List<TrainerLessonSlot> slots) {
        String deleteSql = "DELETE FROM trainer_lesson_slot WHERE trainer_id = ?";
        String insertSql = "INSERT INTO trainer_lesson_slot (trainer_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConn()) {
            // Step 1: Delete all existing lesson slots for this trainer
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, trainerId);
                ps.executeUpdate();
            }
            // Step 2: Insert the new lesson slots one by one
            for (TrainerLessonSlot slot : slots) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, trainerId);
                    ps.setString(2, slot.getDayOfWeek());
                    // Convert Java LocalTime → SQL Time
                    ps.setTime(3, Time.valueOf(slot.getStartTime()));
                    ps.setTime(4, Time.valueOf(slot.getEndTime()));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("saveLessonSlots failed: " + e.getMessage(), e);
        }
    }

    // Updates the editable info fields of a trainer
    @Override
    public void updateInfo(int trainerId, String fullName, String username, String specialty) {
        String sql = "UPDATE trainer SET full_name = ?, username = ?, specialty = ? WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, username);
            ps.setString(3, specialty);
            ps.setInt(4, trainerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateInfo failed: " + e.getMessage(), e);
        }
    }

    // Activates or deactivates a trainer
    // false = deactivated, cannot receive new PT appointments
    @Override
    public void updateActive(int trainerId, boolean active) {
        String sql = "UPDATE trainer SET is_active = ? WHERE trainer_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, trainerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateActive failed: " + e.getMessage(), e);
        }
    }

    // Maps a single ResultSet row to a Trainer object
    private Trainer mapRow(ResultSet rs) throws SQLException {
        Trainer t = new Trainer();
        t.setTrainerId(rs.getInt("trainer_id"));
        t.setFullName(rs.getString("full_name"));
        t.setUsername(rs.getString("username"));
        t.setEmail(rs.getString("email"));
        t.setPassword(rs.getString("password"));
        t.setSpecialty(rs.getString("specialty"));
        t.setActive(rs.getBoolean("is_active"));
        t.setFailedAttempts(rs.getInt("failed_attempts"));
        t.setLocked(rs.getBoolean("is_locked"));
        // Convert SQL Timestamp → Java LocalDateTime
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return t;
    }
}