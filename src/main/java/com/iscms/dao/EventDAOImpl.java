package com.iscms.dao;

import com.iscms.model.Event;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// All SQL operations for the event table live here
// Service layer never touches SQL — only calls this via the EventDAO interface
public class EventDAOImpl implements EventDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new event record into the database
    // created_at is omitted — DB sets it automatically via DEFAULT CURRENT_TIMESTAMP
    // Status defaults to ACTIVE if not explicitly set on the event object
    @Override
    public void insert(Event e) {
        String sql = "INSERT INTO event (event_name, category, event_date, start_time, end_time, " +
                "location, capacity, description, status, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getEventName());
            ps.setString(2, e.getCategory());
            // Convert Java LocalDate → SQL Date
            ps.setDate(3, Date.valueOf(e.getEventDate()));
            // Convert Java LocalTime → SQL Time
            ps.setTime(4, Time.valueOf(e.getStartTime()));
            ps.setTime(5, Time.valueOf(e.getEndTime()));
            ps.setString(6, e.getLocation());
            ps.setInt(7, e.getCapacity());
            ps.setString(8, e.getDescription());
            // Null-safe status — defaults to ACTIVE if not set
            ps.setString(9, e.getStatus() != null ? e.getStatus() : "ACTIVE");
            ps.setInt(10, e.getCreatedBy());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("insert event failed: " + ex.getMessage(), ex);
        }
    }

    // Updates only the capacity of an event
    // Kept separate from update() because capacity changes have their own business rule:
    // manager can only increase capacity up to 5 hours before the event starts (BR-39)
    @Override
    public void updateCapacity(int eventId, int newCapacity) {
        String sql = "UPDATE event SET capacity = ? WHERE event_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newCapacity);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateCapacity failed: " + e.getMessage(), e);
        }
    }

    // Updates only the status of an event (ACTIVE, CANCELLED, EXPIRED)
    // Kept separate from update() because status changes are independent operations
    @Override
    public void updateStatus(int eventId, String status) {
        String sql = "UPDATE event SET status = ? WHERE event_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed: " + e.getMessage(), e);
        }
    }

    // Finds a single event by its ID — returns Optional.empty() if not found
    @Override
    public Optional<Event> findById(int eventId) {
        String sql = "SELECT * FROM event WHERE event_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Returns all events regardless of status, ordered newest first
    // Used in manager-facing event management panel
    @Override
    public List<Event> findAll() {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM event ORDER BY event_date DESC";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll events failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Returns only ACTIVE events ordered by date ascending (soonest first)
    // Used in member-facing event listing — members only see events they can register for
    @Override
    public List<Event> findActiveEvents() {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM event WHERE status = 'ACTIVE' ORDER BY event_date";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findActiveEvents failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Updates editable event details — does NOT update capacity or status
    // Capacity and status have dedicated methods with their own business rules
    @Override
    public void update(Event event) {
        String sql = "UPDATE event SET event_name=?, category=?, event_date=?, " +
                "start_time=?, end_time=?, location=?, description=? " +
                "WHERE event_id=?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventName());
            ps.setString(2, event.getCategory());
            ps.setDate(3, Date.valueOf(event.getEventDate()));
            ps.setTime(4, Time.valueOf(event.getStartTime()));
            ps.setTime(5, Time.valueOf(event.getEndTime()));
            ps.setString(6, event.getLocation());
            ps.setString(7, event.getDescription());
            ps.setInt(8, event.getEventId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("update event failed: " + e.getMessage(), e);
        }
    }

    // Maps a single ResultSet row to an Event object
    // All columns are non-null in DB except location and description
    private Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setEventId(rs.getInt("event_id"));
        e.setEventName(rs.getString("event_name"));
        e.setCategory(rs.getString("category"));
        // Convert SQL Date → Java LocalDate
        e.setEventDate(rs.getDate("event_date").toLocalDate());
        // Convert SQL Time → Java LocalTime
        e.setStartTime(rs.getTime("start_time").toLocalTime());
        e.setEndTime(rs.getTime("end_time").toLocalTime());
        e.setLocation(rs.getString("location"));
        e.setCapacity(rs.getInt("capacity"));
        e.setDescription(rs.getString("description"));
        e.setStatus(rs.getString("status"));
        e.setCreatedBy(rs.getInt("created_by"));
        // Convert SQL Timestamp → Java LocalDateTime
        e.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return e;
    }
}