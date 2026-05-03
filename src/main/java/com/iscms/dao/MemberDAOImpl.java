package com.iscms.dao;

import com.iscms.model.Member;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// All SQL operations for the member table live here.
// Batch 4 adds three new operations supporting the cancellation lifecycle:
//   setCancellationRequested, clearCancellationRequested, setPassiveSince
// plus findPassiveOlderThan for the 1-year auto-delete check.
public class MemberDAOImpl implements MemberDAO {

    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    @Override
    public void insert(Member m) {
        String sql = "INSERT INTO member (full_name, date_of_birth, gender, phone, email, "
                + "password, weight, height, bmi_value, bmi_category, "
                + "emergency_contact_name, emergency_contact_phone, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getFullName());
            ps.setDate(2, Date.valueOf(m.getDateOfBirth()));
            ps.setString(3, m.getGender());
            ps.setString(4, m.getPhone());
            ps.setString(5, m.getEmail());
            ps.setString(6, m.getPassword());
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

    @Override
    public void updateBmi(int memberId, double bmiValue, String bmiCategory) {
        String sql = "UPDATE member SET bmi_value = ?, bmi_category = ?, "
                + "bmi_updated_at = CURRENT_TIMESTAMP WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, bmiValue);
            ps.setString(2, bmiCategory);
            ps.setInt(3, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateBmi failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateProfile(int memberId, Double weight, Double height,
                              String ecName, String ecPhone) {
        String sql = "UPDATE member SET weight = ?, height = ?, "
                + "emergency_contact_name = ?, emergency_contact_phone = ? "
                + "WHERE member_id = ?";
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

    @Override
    public void resetPassword(int memberId, String hashedPassword) {
        String sql = "UPDATE member SET password = ?, failed_attempts = 0, "
                + "is_locked = 0 WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashedPassword);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("resetPassword failed: " + e.getMessage(), e);
        }
    }

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

    @Override
    public void deleteById(int memberId) {
        String sql = "DELETE FROM member WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteById failed: " + e.getMessage(), e);
        }
    }

    // === Batch 4 — cancellation lifecycle methods ===

    @Override
    public void setCancellationRequested(int memberId, LocalDateTime when) {
        String sql = "UPDATE member SET cancellation_requested_at = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(when));
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setCancellationRequested failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearCancellationRequested(int memberId) {
        String sql = "UPDATE member SET cancellation_requested_at = NULL WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("clearCancellationRequested failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setPassiveSince(int memberId, LocalDateTime when) {
        // Pass null when=null to clear (member returns to ACTIVE on renewal)
        String sql = "UPDATE member SET passive_since = ? WHERE member_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (when != null) ps.setTimestamp(1, Timestamp.valueOf(when));
            else              ps.setNull(1, Types.TIMESTAMP);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setPassiveSince failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Member> findPassiveOlderThan(LocalDateTime cutoff) {
        // Returns PASSIVE members whose passive_since is non-null and older than cutoff.
        // The caller (login-trigger expirer) iterates and calls deleteById on each.
        List<Member> list = new ArrayList<>();
        String sql = "SELECT * FROM member "
                + "WHERE status = 'PASSIVE' "
                + "AND passive_since IS NOT NULL "
                + "AND passive_since < ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findPassiveOlderThan failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Maps a single ResultSet row to a Member object — now also reads the
    // bmi_updated_at, cancellation_requested_at, and passive_since columns.
    private Member mapRow(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setMemberId(rs.getInt("member_id"));
        m.setFullName(rs.getString("full_name"));
        m.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        m.setGender(rs.getString("gender"));
        m.setPhone(rs.getString("phone"));
        m.setEmail(rs.getString("email"));
        m.setPassword(rs.getString("password"));
        m.setStatus(rs.getString("status"));
        m.setFailedAttempts(rs.getInt("failed_attempts"));
        m.setLocked(rs.getBoolean("is_locked"));

        double weight = rs.getDouble("weight");
        if (!rs.wasNull()) m.setWeight(weight);
        double height = rs.getDouble("height");
        if (!rs.wasNull()) m.setHeight(height);
        double bmi = rs.getDouble("bmi_value");
        if (!rs.wasNull()) m.setBmiValue(bmi);

        m.setBmiCategory(rs.getString("bmi_category"));
        m.setEmergencyContactName(rs.getString("emergency_contact_name"));
        m.setEmergencyContactPhone(rs.getString("emergency_contact_phone"));

        Timestamp bmiTs = rs.getTimestamp("bmi_updated_at");
        if (bmiTs != null) m.setBmiUpdatedAt(bmiTs.toLocalDateTime());

        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

        // === Batch 4 fields ===
        Timestamp cancelTs = rs.getTimestamp("cancellation_requested_at");
        if (cancelTs != null) m.setCancellationRequestedAt(cancelTs.toLocalDateTime());

        Timestamp passiveTs = rs.getTimestamp("passive_since");
        if (passiveTs != null) m.setPassiveSince(passiveTs.toLocalDateTime());

        return m;
    }
}