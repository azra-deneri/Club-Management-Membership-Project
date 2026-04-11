package com.iscms.dao;

import com.iscms.model.EventRegistration;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventRegistrationDAOImpl implements EventRegistrationDAO {

    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new registration or reactivates a previously cancelled one
// Uses ON DUPLICATE KEY UPDATE to handle the UNIQUE(member_id, event_id) constraint
// If no record exists → INSERT with status REGISTERED
// If a CANCELLED record exists → UPDATE status back to REGISTERED

    @Override
    public void insert(EventRegistration reg) {
        String sql = "INSERT INTO event_registration (member_id, event_id, status) " +
                "VALUES (?, ?, 'REGISTERED') " +
                "ON DUPLICATE KEY UPDATE status = 'REGISTERED', " +
                "registration_date = CURRENT_TIMESTAMP";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reg.getMemberId());
            ps.setInt(2, reg.getEventId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert registration failed: " + e.getMessage(), e);
        }
    }


    @Override
    public void cancelByMemberAndEvent(int memberId, int eventId) {
        String sql = "UPDATE event_registration SET status = 'CANCELLED' " +
                "WHERE member_id = ? AND event_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("cancelByMemberAndEvent failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelAllByEventId(int eventId) {
        String sql = "UPDATE event_registration SET status = 'CANCELLED' WHERE event_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("cancelAllByEventId failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByMemberAndEvent(int memberId, int eventId) {
        String sql = "SELECT COUNT(*) FROM event_registration " +
                "WHERE member_id = ? AND event_id = ? AND status = 'REGISTERED'";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsByMemberAndEvent failed: " + e.getMessage(), e);
        }
        return false;
    }

    @Override
    public int countRegistered(int eventId) {
        String sql = "SELECT COUNT(*) FROM event_registration " +
                "WHERE event_id = ? AND status = 'REGISTERED'";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("countRegistered failed: " + e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public List<EventRegistration> findByEventId(int eventId) {
        List<EventRegistration> list = new ArrayList<>();
        String sql = "SELECT * FROM event_registration WHERE event_id = ? ORDER BY registration_date";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByEventId failed: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<EventRegistration> findByMemberId(int memberId) {
        List<EventRegistration> list = new ArrayList<>();
        String sql = "SELECT * FROM event_registration WHERE member_id = ? ORDER BY registration_date DESC";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByMemberId failed: " + e.getMessage(), e);
        }
        return list;
    }

    private EventRegistration mapRow(ResultSet rs) throws SQLException {
        EventRegistration reg = new EventRegistration();
        reg.setRegistrationId(rs.getInt("registration_id"));
        reg.setMemberId(rs.getInt("member_id"));
        reg.setEventId(rs.getInt("event_id"));
        reg.setRegistrationDate(rs.getTimestamp("registration_date").toLocalDateTime());
        reg.setStatus(rs.getString("status"));
        return reg;
    }
}