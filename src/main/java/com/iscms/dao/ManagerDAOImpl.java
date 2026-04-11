package com.iscms.dao;

import com.iscms.model.Manager;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ManagerDAOImpl implements ManagerDAO {

    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    @Override
    public void insert(Manager m) {
        String sql = "INSERT INTO manager (full_name, username, email, role, password) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getFullName());
            ps.setString(2, m.getUsername());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getRole());
            ps.setString(5, m.getPassword());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert manager failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int managerId) {
        String sql = "DELETE FROM manager WHERE manager_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete manager failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Manager> findByEmail(String email) {
        String sql = "SELECT * FROM manager WHERE email = ?";
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
    public List<Manager> findAll() {
        List<Manager> list = new ArrayList<>();
        String sql = "SELECT * FROM manager ORDER BY full_name";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll managers failed: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateFailedAttempts(int managerId, int attempts) {
        String sql = "UPDATE manager SET failed_attempts = ? WHERE manager_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            ps.setInt(2, managerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateFailedAttempts failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateLockStatus(int managerId, boolean locked) {
        String sql = "UPDATE manager SET is_locked = ? WHERE manager_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, locked);
            ps.setInt(2, managerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateLockStatus failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void updatePassword(int managerId, String hashedPassword) {
        String sql = "UPDATE manager SET password = ? WHERE manager_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashedPassword);
            ps.setInt(2, managerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePassword failed: " + e.getMessage(), e);
        }
    }

    private Manager mapRow(ResultSet rs) throws SQLException { // Turn ResultSet into manager object
        Manager m = new Manager();
        m.setManagerId(rs.getInt("manager_id"));
        m.setFullName(rs.getString("full_name"));
        m.setUsername(rs.getString("username"));
        m.setEmail(rs.getString("email"));
        m.setRole(rs.getString("role"));
        m.setPassword(rs.getString("password"));
        m.setFailedAttempts(rs.getInt("failed_attempts"));
        m.setLocked(rs.getBoolean("is_locked"));
        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return m;
    }

    public void nullifyManagerEvents(int managerId) {
        String sql = "UPDATE event SET created_by = NULL WHERE created_by = ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("nullifyManagerEvents failed: " + e.getMessage(), e);
        }
    }
}