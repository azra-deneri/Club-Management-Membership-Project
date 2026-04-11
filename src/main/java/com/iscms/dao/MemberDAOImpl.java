package com.iscms.dao;

import com.iscms.model.Member;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// All SQL operations for the member table live here
// Service layer never touches SQL — only calls this via the MemberDAO interface
public class MemberDAOImpl implements MemberDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new member record into the database
    // failed_attempts, is_locked, created_at are omitted — DB sets defaults automatically
    // Uses setObject() for nullable Double fields (weight, height, bmi) — maps null correctly
    @Override
    public void insert(Member m) {
        String sql = "INSERT INTO member (full_name, date_of_birth, gender, phone, email, " +
                "password, weight, height, bmi_value, bmi_category, " +
                "emergency_contact_name, emergency_contact_phone, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getFullName());
            // Convert Java LocalDate → SQL Date
            ps.setDate(2, Date.valueOf(m.getDateOfBirth()));
            ps.setString(3, m.getGender());
            ps.setString(4, m.getPhone());
            ps.setString(5, m.getEmail());
            // Password must already be BCrypt-hashed before calling this method
            ps.setString(6, m.getPassword());
            // setObject() handles null correctly for nullable Double fields
            ps.setObject(7, m.getWeight());
            ps.setObject(8, m.getHeight());
            ps.setObject(9, m.getBmiValue());
            ps.setString(10, m.getBmiCategory());
            ps.setString(11, m.getEmergencyContactName());
            ps.setString(12, m.getEmergencyContactPhone());
            ps.setString(13, m.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert member failed: " + e.getMessage(), e);
        }
    }

    // Updates a member's phone number
    // Called only after uniqueness check in service layer
    @Override
    public void updatePhone(int memberId, String phone) {
        String sql = "UPDATE member SET phone = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePhone failed: " + e.getMessage(), e);
        }
    }

    // Finds a member by phone number — used during login and duplicate phone validation
    @Override
    public Optional<Member> findByPhone(String phone) {
        String sql = "SELECT * FROM member WHERE phone = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByPhone failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Finds a member by email address — used during login and duplicate email validation
    @Override
    public Optional<Member> findByEmail(String email) {
        String sql = "SELECT * FROM member WHERE email = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByEmail failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Finds a member by their ID — returns Optional.empty() if not found
    @Override
    public Optional<Member> findById(int memberId) {
        String sql = "SELECT * FROM member WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Returns all member records ordered alphabetically by full name
    // Used in manager dashboard member list
    @Override
    public List<Member> findAll() {
        List<Member> list = new ArrayList<>();
        String sql = "SELECT * FROM member ORDER BY full_name";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Returns all members with a given status (e.g., ACTIVE, FROZEN, SUSPENDED, PENDING)
    // Used for filtered views in manager dashboard and report panels
    @Override
    public List<Member> findByStatus(String status) {
        List<Member> list = new ArrayList<>();
        String sql = "SELECT * FROM member WHERE status = ? ORDER BY full_name";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByStatus failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates the account status of a member
    // Valid values: ACTIVE, PASSIVE, SUSPENDED, PENDING, REGISTRATION_FAILED, FROZEN
    @Override
    public void updateStatus(int memberId, String status) {
        String sql = "UPDATE member SET status = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed: " + e.getMessage(), e);
        }
    }

    // Updates the failed login attempt count for a member
    // Called after each failed login — lockout triggered at threshold
    @Override
    public void updateFailedAttempts(int memberId, int attempts) {
        String sql = "UPDATE member SET failed_attempts = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateFailedAttempts failed: " + e.getMessage(), e);
        }
    }

    // Locks or unlocks a member account
    // true = locked (cannot log in), false = unlocked
    @Override
    public void updateLockStatus(int memberId, boolean locked) {
        String sql = "UPDATE member SET is_locked = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, locked);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateLockStatus failed: " + e.getMessage(), e);
        }
    }

    // Updates the BMI value and category for a member
    // bmi_updated_at is set to current timestamp by DB automatically
    // Called after member updates weight or height
    @Override
    public void updateBmi(int memberId, double bmiValue, String bmiCategory) {
        String sql = "UPDATE member SET bmi_value = ?, bmi_category = ?, " +
                "bmi_updated_at = CURRENT_TIMESTAMP WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, bmiValue);
            ps.setString(2, bmiCategory);
            ps.setInt(3, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateBmi failed: " + e.getMessage(), e);
        }
    }

    // Updates editable profile fields for a member
    // Uses setObject() for nullable Double fields (weight, height)
    // Phone and email are updated via dedicated methods
    @Override
    public void updateProfile(int memberId, Double weight, Double height,
                              String ecName, String ecPhone) {
        String sql = "UPDATE member SET weight = ?, height = ?, " +
                "emergency_contact_name = ?, emergency_contact_phone = ? " +
                "WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, weight);
            ps.setObject(2, height);
            ps.setString(3, ecName);
            ps.setString(4, ecPhone);
            ps.setInt(5, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateProfile failed: " + e.getMessage(), e);
        }
    }

    // Resets a member's password and clears lockout state in a single operation
    // Also resets failed_attempts to 0 and unlocks the account (is_locked = 0)
    // Ensures a member can log in immediately after resetting their password
    @Override
    public void resetPassword(int memberId, String hashedPassword) {
        String sql = "UPDATE member SET password = ?, failed_attempts = 0, " +
                "is_locked = 0 WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashedPassword);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("resetPassword failed: " + e.getMessage(), e);
        }
    }

    // Checks whether a phone number is already registered — used for duplicate validation
    @Override
    public boolean existsByPhone(String phone) {
        String sql = "SELECT COUNT(*) FROM member WHERE phone = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsByPhone failed: " + e.getMessage(), e);
        }
        return false;
    }

    // Checks whether an email is already registered — used for duplicate validation
    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM member WHERE email = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsByEmail failed: " + e.getMessage(), e);
        }
        return false;
    }

    // Maps a single ResultSet row to a Member object
    // Handles nullable fields: weight, height, bmi_value, bmi_updated_at
    private Member mapRow(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setMemberId(rs.getInt("member_id"));
        m.setFullName(rs.getString("full_name"));
        // Convert SQL Date → Java LocalDate
        m.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        m.setGender(rs.getString("gender"));
        m.setPhone(rs.getString("phone"));
        m.setEmail(rs.getString("email"));
        m.setPassword(rs.getString("password"));
        m.setStatus(rs.getString("status"));
        m.setFailedAttempts(rs.getInt("failed_attempts"));
        m.setLocked(rs.getBoolean("is_locked"));

        // Nullable double fields: must use wasNull() check after getDouble()
        // getDouble() returns 0.0 for NULL — wasNull() distinguishes 0.0 from actual NULL
        double weight = rs.getDouble("weight");
        if (!rs.wasNull()) m.setWeight(weight);
        double height = rs.getDouble("height");
        if (!rs.wasNull()) m.setHeight(height);
        double bmi = rs.getDouble("bmi_value");
        if (!rs.wasNull()) m.setBmiValue(bmi);

        m.setBmiCategory(rs.getString("bmi_category"));
        m.setEmergencyContactName(rs.getString("emergency_contact_name"));
        m.setEmergencyContactPhone(rs.getString("emergency_contact_phone"));

        // bmi_updated_at is nullable — only set if member has calculated BMI
        Timestamp bmiTs = rs.getTimestamp("bmi_updated_at");
        if (bmiTs != null) m.setBmiUpdatedAt(bmiTs.toLocalDateTime());

        // Convert SQL Timestamp → Java LocalDateTime
        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return m;
    }

    // Updates a member's email address
    // Called only after uniqueness check in service layer
    @Override
    public void updateEmail(int memberId, String email) {
        String sql = "UPDATE member SET email = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateEmail failed: " + e.getMessage(), e);
        }
    }

    // Deletes a member record by ID
    // Use with caution — cascading FK constraints may affect related records
    @Override
    public void deleteById(int memberId) {
        String sql = "DELETE FROM member WHERE member_id = ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteById failed: " + e.getMessage(), e);
        }
    }
}