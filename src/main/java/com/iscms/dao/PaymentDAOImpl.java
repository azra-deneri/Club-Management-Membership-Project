package com.iscms.dao;

import com.iscms.model.Payment;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


// All SQL operations for the payment table live here
// Service layer never touches SQL — only calls this via the PaymentDAO interface
public class PaymentDAOImpl implements PaymentDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new payment record into the database
    // payment_date is omitted — DB sets it automatically via DEFAULT CURRENT_TIMESTAMP
    // Called when a manager approves a registration or tier upgrade request
    @Override
    public void insert(Payment p) {
        String sql = "INSERT INTO payment (member_id, amount, payment_type, description, status, recorded_by) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getMemberId());
            ps.setDouble(2, p.getAmount());
            // MEMBERSHIP = new registration or renewal | UPGRADE = tier upgrade
            ps.setString(3, p.getPaymentType());
            ps.setString(4, p.getDescription());
            // Status is always PAID at insert time in current implementation
            ps.setString(5, p.getStatus());
            // ID of the manager who recorded this payment (FK → manager table, nullable)
            ps.setInt(6, p.getRecordedBy());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert payment failed: " + e.getMessage(), e);
        }
    }

    // Returns all payment records for a given member, ordered newest first
    // Used in member dashboard payment history tab
    @Override
    public List<Payment> findByMemberId(int memberId) {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payment WHERE member_id = ? ORDER BY payment_date DESC";
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

    // Returns all payment records across all members, ordered newest first
    // Used in admin dashboard and monthly revenue report
    @Override
    public List<Payment> findAll() {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payment ORDER BY payment_date DESC";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll payments failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Maps a single ResultSet row to a Payment object
    // recorded_by is nullable in DB — getInt() returns 0 if NULL (acceptable for primitive int)
    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setMemberId(rs.getInt("member_id"));
        p.setAmount(rs.getDouble("amount"));
        // Convert SQL Timestamp → Java LocalDateTime
        p.setPaymentDate(rs.getTimestamp("payment_date").toLocalDateTime());
        p.setPaymentType(rs.getString("payment_type"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        // recorded_by is nullable — returns 0 if NULL (no manager assigned)
        p.setRecordedBy(rs.getInt("recorded_by"));
        return p;
    }
}