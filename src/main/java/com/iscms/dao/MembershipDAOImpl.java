package com.iscms.dao;

import com.iscms.model.FreezeLog;
import com.iscms.model.Membership;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// All SQL operations for the membership table live here
// Service layer never touches SQL — only calls this via the MembershipDAO interface
public class MembershipDAOImpl implements MembershipDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new membership record into the database
    // Note: DB column is named 'package' (not 'package_type') — mapped correctly here
    // created_at is omitted — DB sets it automatically via DEFAULT CURRENT_TIMESTAMP
    @Override
    public void insert(Membership ms) {
        String sql = "INSERT INTO membership (member_id, tier, package, start_date, end_date, status, freeze_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ms.getMemberId());
            ps.setString(2, ms.getTier());
            // Java field is 'packageType' but DB column is 'package'
            ps.setString(3, ms.getPackageType());
            // Convert Java LocalDate → SQL Date
            ps.setDate(4, Date.valueOf(ms.getStartDate()));
            ps.setDate(5, Date.valueOf(ms.getEndDate()));
            ps.setString(6, ms.getStatus());
            ps.setInt(7, ms.getFreezeCount());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert membership failed: " + e.getMessage(), e);
        }
    }

    // Finds the most recently created ACTIVE membership for a given member
    // LIMIT 1 + ORDER BY created_at DESC ensures only one record is returned
    @Override
    public Optional<Membership> findActiveByMemberId(int memberId) {
        String sql = "SELECT * FROM membership WHERE member_id = ? AND status = 'ACTIVE' " +
                "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findActiveByMemberId failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Finds the most recently created FROZEN membership for a given member
    // Called when unfreezing a membership to get the correct record to update
    @Override
    public Optional<Membership> findFrozenByMemberId(int memberId) {
        String sql = "SELECT * FROM membership WHERE member_id = ? AND status = 'FROZEN' " +
                "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findFrozenByMemberId failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Returns all membership records for a given member across all statuses
    // Ordered newest first — used to show full membership history
    @Override
    public List<Membership> findAllByMemberId(int memberId) {
        List<Membership> list = new ArrayList<>();
        String sql = "SELECT * FROM membership WHERE member_id = ? ORDER BY created_at DESC";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllByMemberId failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates the status of a membership
    // Valid values: ACTIVE, PASSIVE, SUSPENDED, FROZEN
    @Override
    public void updateStatus(int membershipId, String status) {
        String sql = "UPDATE membership SET status = ? WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, membershipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed: " + e.getMessage(), e);
        }
    }

    // Updates the tier of a membership
    // Called when a tier upgrade request is approved
    @Override
    public void updateTier(int membershipId, String newTier) {
        String sql = "UPDATE membership SET tier = ? WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newTier);
            ps.setInt(2, membershipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateTier failed: " + e.getMessage(), e);
        }
    }

    // Updates the end date of a membership
    // Called when a renewal is approved or freeze period adjustment is needed
    @Override
    public void updateEndDate(int membershipId, LocalDate newEndDate) {
        String sql = "UPDATE membership SET end_date = ? WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            // Convert Java LocalDate → SQL Date
            ps.setDate(1, Date.valueOf(newEndDate));
            ps.setInt(2, membershipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateEndDate failed: " + e.getMessage(), e);
        }
    }


    // Used to track freeze usage per membership
    @Override
    public void incrementFreezeCount(int membershipId) {
        String sql = "UPDATE membership SET freeze_count = freeze_count + 1 WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("incrementFreezeCount failed: " + e.getMessage(), e);
        }
    }

    // Finds a single membership record by its ID
    // Returns Optional.empty() if not found
    @Override
    public Optional<Membership> findById(int membershipId) {
        String sql = "SELECT * FROM membership WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Updates the package type of a membership
    // Note: DB column is named 'package' — mapped correctly here
    @Override
    public void updatePackageType(int membershipId, String packageType) {
        String sql = "UPDATE membership SET package = ? WHERE membership_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packageType);
            ps.setInt(2, membershipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePackageType failed: " + e.getMessage(), e);
        }
    }

    // Maps a single ResultSet row to a Membership object
    // Note: DB column 'package' maps to Java field 'packageType'
    private Membership mapRow(ResultSet rs) throws SQLException {
        Membership ms = new Membership();
        ms.setMembershipId(rs.getInt("membership_id"));
        ms.setMemberId(rs.getInt("member_id"));
        ms.setTier(rs.getString("tier"));
        // DB column is 'package' — Java field is 'packageType'
        ms.setPackageType(rs.getString("package"));
        // Convert SQL Date → Java LocalDate
        ms.setStartDate(rs.getDate("start_date").toLocalDate());
        ms.setEndDate(rs.getDate("end_date").toLocalDate());
        ms.setStatus(rs.getString("status"));
        ms.setFreezeCount(rs.getInt("freeze_count"));
        // Convert SQL Timestamp → Java LocalDateTime
        ms.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return ms;
    }

    @Override
    public void insertFreezeLog(int membershipId, java.time.LocalDate start, java.time.LocalDate end) {
        String sql = "INSERT INTO membership_freeze_log (membership_id, freeze_start, freeze_end) VALUES (?, ?, ?)";
        try (java.sql.Connection conn = getConn();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            ps.setDate(2, java.sql.Date.valueOf(start));
            ps.setDate(3, java.sql.Date.valueOf(end));
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("insertFreezeLog failed: " + e.getMessage(), e);
        }
    }

    @Override
    public java.util.Optional<FreezeLog> findLatestFreezeLog(int membershipId) {
        // Most recent freeze row first, so unfreeze always operates on the
        // currently-active freeze period rather than an older historical one.
        String sql = "SELECT freeze_start, freeze_end FROM membership_freeze_log " +
                     "WHERE membership_id = ? ORDER BY freeze_start DESC, freeze_end DESC LIMIT 1";
        try (java.sql.Connection conn = getConn();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(new FreezeLog(
                        membershipId,
                        rs.getDate("freeze_start").toLocalDate(),
                        rs.getDate("freeze_end").toLocalDate()
                    ));
                }
                return java.util.Optional.empty();
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("findLatestFreezeLog failed: " + e.getMessage(), e);
        }
    }
}