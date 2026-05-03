package com.iscms.dao;

import com.iscms.model.Installment;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// JDBC implementation of InstallmentDAO. Uses try-with-resources for all
// connection/statement/result-set handles to avoid leaks (the same pattern
// used by other DAOImpl classes in this project).
public class InstallmentDAOImpl implements InstallmentDAO {

    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    @Override
    public void insert(Installment inst) {
        String sql = "INSERT INTO installment "
                + "(membership_id, member_id, installment_no, due_date, amount, status, paid_date, payment_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, inst.getMembershipId());
            // member_id may be null after member deletion — preserve nullability
            if (inst.getMemberId() != null) ps.setInt(2, inst.getMemberId());
            else                            ps.setNull(2, Types.INTEGER);
            ps.setInt(3, inst.getInstallmentNo());
            ps.setDate(4, Date.valueOf(inst.getDueDate()));
            ps.setBigDecimal(5, inst.getAmount());
            ps.setString(6, inst.getStatus());
            if (inst.getPaidDate() != null)
                ps.setTimestamp(7, Timestamp.valueOf(inst.getPaidDate()));
            else
                ps.setNull(7, Types.TIMESTAMP);
            if (inst.getPaymentId() != null) ps.setInt(8, inst.getPaymentId());
            else                             ps.setNull(8, Types.INTEGER);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) inst.setInstallmentId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert installment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void insertAll(List<Installment> installments) {
        // Iterates and calls single-insert — small batch (always 12 rows), no need for
        // explicit JDBC batching. Atomicity-wise, callers should wrap this in a
        // transaction if all-or-nothing is required.
        for (Installment inst : installments) insert(inst);
    }

    @Override
    public List<Installment> findByMembershipId(int membershipId) {
        String sql = "SELECT * FROM installment WHERE membership_id = ? ORDER BY installment_no ASC";
        return queryList(sql, ps -> ps.setInt(1, membershipId));
    }

    @Override
    public List<Installment> findByMemberId(int memberId) {
        String sql = "SELECT * FROM installment WHERE member_id = ? ORDER BY due_date ASC";
        return queryList(sql, ps -> ps.setInt(1, memberId));
    }

    @Override
    public Optional<Installment> findById(int installmentId) {
        String sql = "SELECT * FROM installment WHERE installment_id = ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, installmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById installment failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Installment> findOverdueForMember(int memberId) {
        // Returns rows that are explicitly OVERDUE OR are PENDING with a past due_date.
        // The OR-with-PENDING handles the case where markOverdueGlobal hasn't been called yet
        // — we still want to show the member their actual overdue balance immediately.
        String sql = "SELECT * FROM installment "
                + "WHERE member_id = ? "
                + "AND ( status = 'OVERDUE' OR (status = 'PENDING' AND due_date < CURDATE()) ) "
                + "ORDER BY due_date ASC";
        return queryList(sql, ps -> ps.setInt(1, memberId));
    }

    @Override
    public void markPaid(int installmentId, int paymentId) {
        String sql = "UPDATE installment SET status = 'PAID', paid_date = NOW(), payment_id = ? "
                + "WHERE installment_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            ps.setInt(2, installmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("markPaid installment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int markOverdueGlobal() {
        // Bulk transition: all PENDING installments past their due_date become OVERDUE.
        // Called periodically (e.g. on manager dashboard load) to keep the table fresh.
        String sql = "UPDATE installment SET status = 'OVERDUE' "
                + "WHERE status = 'PENDING' AND due_date < CURDATE()";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("markOverdueGlobal failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int countUnpaidByMembership(int membershipId) {
        String sql = "SELECT COUNT(*) FROM installment "
                + "WHERE membership_id = ? AND status IN ('PENDING','OVERDUE')";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("countUnpaidByMembership failed: " + e.getMessage(), e);
        }
        return 0;
    }

    // === Helpers ===

    @FunctionalInterface
    private interface PSBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // Generic SELECT-many helper used by all findBy* methods
    private List<Installment> queryList(String sql, PSBinder binder) {
        List<Installment> list = new ArrayList<>();
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
        return list;
    }

    // Maps a single result row to an Installment object
    private Installment map(ResultSet rs) throws SQLException {
        Installment i = new Installment();
        i.setInstallmentId(rs.getInt("installment_id"));
        i.setMembershipId(rs.getInt("membership_id"));
        int memId = rs.getInt("member_id");
        i.setMemberId(rs.wasNull() ? null : memId);
        i.setInstallmentNo(rs.getInt("installment_no"));
        i.setDueDate(rs.getDate("due_date").toLocalDate());
        i.setAmount(rs.getBigDecimal("amount"));
        i.setStatus(rs.getString("status"));
        Timestamp paid = rs.getTimestamp("paid_date");
        i.setPaidDate(paid != null ? paid.toLocalDateTime() : null);
        int payId = rs.getInt("payment_id");
        i.setPaymentId(rs.wasNull() ? null : payId);
        return i;
    }
}