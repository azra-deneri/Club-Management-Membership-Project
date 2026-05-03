package com.iscms.dao;

import com.iscms.model.Payment;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// All SQL operations for the payment table live here.
// Now also handles payment_method (CASH/ONLINE) and installment_id (nullable FK)
// columns added in Batch 4. Existing callers that don't set payment_method default
// to "CASH" — matches the DB-side DEFAULT 'CASH' on the column.
public class PaymentDAOImpl implements PaymentDAO {

    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new payment record. payment_date defaults to CURRENT_TIMESTAMP via DB.
    // Sets payment_method (defaults to "CASH" if caller didn't set it on the model)
    // and installment_id (nullable — set only when the payment clears an installment).
    @Override
    public void insert(Payment p) {
        String sql = "INSERT INTO payment "
                + "(member_id, amount, payment_type, description, status, recorded_by, "
                + " payment_method, installment_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // member_id is nullable since FK uses ON DELETE SET NULL; treat 0 as null
            if (p.getMemberId() <= 0) ps.setNull(1, Types.INTEGER);
            else                       ps.setInt(1, p.getMemberId());

            ps.setDouble(2, p.getAmount());
            ps.setString(3, p.getPaymentType());
            ps.setString(4, p.getDescription());
            ps.setString(5, p.getStatus());

            // recorded_by: 0 in the model means "online self-payment, no manager" → store NULL
            if (p.getRecordedBy() <= 0) ps.setNull(6, Types.INTEGER);
            else                         ps.setInt(6, p.getRecordedBy());

            // payment_method: default to CASH if model didn't specify
            String method = p.getPaymentMethod() != null ? p.getPaymentMethod() : "CASH";
            ps.setString(7, method);

            // installment_id: nullable
            if (p.getInstallmentId() != null) ps.setInt(8, p.getInstallmentId());
            else                              ps.setNull(8, Types.INTEGER);

            ps.executeUpdate();

            // Capture the generated payment_id back onto the model so callers
            // (e.g. installment payment flow) can link the installment to this payment row.
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) p.setPaymentId(keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert payment failed: " + e.getMessage(), e);
        }
    }

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

    // Maps a single ResultSet row to a Payment object, handling the new
    // payment_method and installment_id columns alongside the existing ones.
    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setMemberId(rs.getInt("member_id"));   // 0 if NULL (deleted member)
        p.setAmount(rs.getDouble("amount"));
        p.setPaymentDate(rs.getTimestamp("payment_date").toLocalDateTime());
        p.setPaymentType(rs.getString("payment_type"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        p.setRecordedBy(rs.getInt("recorded_by"));  // 0 if NULL (online self-payment)
        p.setPaymentMethod(rs.getString("payment_method"));

        int instId = rs.getInt("installment_id");
        p.setInstallmentId(rs.wasNull() ? null : instId);
        return p;
    }
}