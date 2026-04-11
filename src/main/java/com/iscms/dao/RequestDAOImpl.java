package com.iscms.dao;

import com.iscms.model.RegistrationRequest;
import com.iscms.model.TierUpgradeRequest;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


// All SQL operations for registration_request and tier_upgrade_request tables live here
// Service layer never touches SQL — only calls this via the RequestDAO interface
public class RequestDAOImpl implements RequestDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new registration request into the database
    // status defaults to PENDING via DB default — not included in INSERT
    // type defaults to INITIAL if not explicitly set
    @Override
    public void insertRegistration(RegistrationRequest req) {
        String sql = "INSERT INTO registration_request (member_id, type, tier, package_type, amount, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, req.getMemberId());
            ps.setString(2, req.getType() != null ? req.getType() : "INITIAL");
            ps.setString(3, req.getTier());
            ps.setString(4, req.getPackageType());
            ps.setDouble(5, req.getAmount());
            // Convert Java LocalDateTime → SQL Timestamp
            ps.setTimestamp(6, Timestamp.valueOf(req.getExpiresAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertRegistration failed: " + e.getMessage(), e);
        }
    }

    // Returns all registration requests with status = PENDING, ordered oldest first
    // Used in manager dashboard for approval/rejection
    @Override
    public List<RegistrationRequest> findPendingRegistrations() {
        List<RegistrationRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM registration_request WHERE status = 'PENDING' ORDER BY created_at";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRegRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findPendingRegistrations failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates the status of a registration request
    // Valid values: PENDING, APPROVED, REJECTED, EXPIRED
    @Override
    public void updateRegistrationStatus(int requestId, String status) {
        String sql = "UPDATE registration_request SET status = ? WHERE request_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateRegistrationStatus failed: " + e.getMessage(), e);
        }
    }

    // Sets status = EXPIRED for all PENDING registration requests past their expires_at timestamp
    // Enforces the 3-day approval window
    @Override
    public void expireOldRegistrationRequests() {
        String sql = "UPDATE registration_request SET status = 'EXPIRED' " +
                "WHERE status = 'PENDING' AND expires_at < NOW()";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("expireOldRegistrationRequests failed: " + e.getMessage(), e);
        }
    }

    // Inserts a new tier upgrade request into the database
    // status defaults to PENDING via DB default — not included in INSERT
    @Override
    public void insertTierUpgrade(TierUpgradeRequest req) {
        String sql = "INSERT INTO tier_upgrade_request " +
                "(member_id, membership_id, old_tier, new_tier, package_type, upgrade_fee, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, req.getMemberId());
            ps.setInt(2, req.getMembershipId());
            ps.setString(3, req.getOldTier());
            ps.setString(4, req.getNewTier());
            ps.setString(5, req.getPackageType());
            ps.setDouble(6, req.getUpgradeFee());
            // Convert Java LocalDateTime → SQL Timestamp
            ps.setTimestamp(7, Timestamp.valueOf(req.getExpiresAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertTierUpgrade failed: " + e.getMessage(), e);
        }
    }

    // Returns all tier upgrade requests with status = PENDING, ordered oldest first
    // Used in manager dashboard for approval/rejection
    @Override
    public List<TierUpgradeRequest> findPendingTierUpgrades() {
        List<TierUpgradeRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM tier_upgrade_request WHERE status = 'PENDING' ORDER BY created_at";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapUpgradeRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findPendingTierUpgrades failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates the status of a tier upgrade request
    // Valid values: PENDING, APPROVED, REJECTED, EXPIRED
    @Override
    public void updateTierUpgradeStatus(int requestId, String status) {
        String sql = "UPDATE tier_upgrade_request SET status = ? WHERE request_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateTierUpgradeStatus failed: " + e.getMessage(), e);
        }
    }

    // Deletes a registration request record by ID
    @Override
    public void deleteRegistration(int requestId) {
        String sql = "DELETE FROM registration_request WHERE request_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteRegistration failed: " + e.getMessage(), e);
        }
    }

    // Returns all registration requests regardless of status, ordered newest first
    // Used for full request history view in manager dashboard
    @Override
    public List<RegistrationRequest> findAllRegistrations() {
        List<RegistrationRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM registration_request ORDER BY created_at DESC";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRegRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAllRegistrations failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Returns all tier upgrade requests regardless of status, ordered newest first
    // Used for full upgrade history view in manager dashboard
    @Override
    public List<TierUpgradeRequest> findAllTierUpgrades() {
        List<TierUpgradeRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM tier_upgrade_request ORDER BY created_at DESC";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapUpgradeRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAllTierUpgrades failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Maps a single ResultSet row to a RegistrationRequest object
    private RegistrationRequest mapRegRow(ResultSet rs) throws SQLException {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setMemberId(rs.getInt("member_id"));
        req.setType(rs.getString("type"));
        req.setTier(rs.getString("tier"));
        req.setPackageType(rs.getString("package_type"));
        req.setAmount(rs.getDouble("amount"));
        req.setStatus(rs.getString("status"));
        // Convert SQL Timestamp → Java LocalDateTime
        req.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        req.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return req;
    }

    // Maps a single ResultSet row to a TierUpgradeRequest object
    private TierUpgradeRequest mapUpgradeRow(ResultSet rs) throws SQLException {
        TierUpgradeRequest req = new TierUpgradeRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setMemberId(rs.getInt("member_id"));
        req.setMembershipId(rs.getInt("membership_id"));
        req.setOldTier(rs.getString("old_tier"));
        req.setNewTier(rs.getString("new_tier"));
        req.setPackageType(rs.getString("package_type"));
        req.setUpgradeFee(rs.getDouble("upgrade_fee"));
        req.setStatus(rs.getString("status"));
        // Convert SQL Timestamp → Java LocalDateTime
        req.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        req.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return req;
    }
}