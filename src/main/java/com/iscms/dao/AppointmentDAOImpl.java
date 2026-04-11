package com.iscms.dao;

import com.iscms.model.PersonalTrainingAppointment;
import com.iscms.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// All SQL operations for personal_training_appointment table live here
// Service layer never touches SQL — only calls this via the AppointmentDAO interface
public class AppointmentDAOImpl implements AppointmentDAO {

    // Helper method to get the shared database connection from the Singleton DBConnection
    private Connection getConn() throws SQLException {
        return DBConnection.getInstance().getConnection();
    }

    // Inserts a new PT appointment record into the database
    // no_show_penalty_until and created_at are omitted — DB sets defaults automatically
    @Override
    public void insert(PersonalTrainingAppointment apt) {
        String sql = "INSERT INTO personal_training_appointment " +
                "(member_id, trainer_id, appointment_date, start_time, end_time, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, apt.getMemberId());
            ps.setInt(2, apt.getTrainerId());
            // Convert Java LocalDate → SQL Date
            ps.setDate(3, Date.valueOf(apt.getAppointmentDate()));
            // Convert Java LocalTime → SQL Time
            ps.setTime(4, Time.valueOf(apt.getStartTime()));
            ps.setTime(5, Time.valueOf(apt.getEndTime()));
            ps.setString(6, apt.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insert appointment failed: " + e.getMessage(), e);
        }
    }

    // Updates the status of an existing appointment
    // Called when trainer marks: COMPLETED or NO_SHOW, or member/manager cancels
    @Override
    public void updateStatus(int appointmentId, String status) {
        String sql = "UPDATE personal_training_appointment SET status = ? WHERE appointment_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed: " + e.getMessage(), e);
        }
    }

    // Sets the no-show penalty expiry date on an appointment
    // Called after trainer marks a member as NO_SHOW
    @Override
    public void updateNoShowPenalty(int appointmentId, LocalDate penaltyUntil) {
        String sql = "UPDATE personal_training_appointment SET no_show_penalty_until = ? " +
                "WHERE appointment_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(penaltyUntil));
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateNoShowPenalty failed: " + e.getMessage(), e);
        }
    }

    // Finds a single appointment by its ID
    // Returns Optional.empty() if no record found — avoids null returns
    @Override
    public Optional<PersonalTrainingAppointment> findById(int appointmentId) {
        String sql = "SELECT * FROM personal_training_appointment WHERE appointment_id = ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Returns all appointments for a given member, ordered newest first
    @Override
    public List<PersonalTrainingAppointment> findByMemberId(int memberId) {
        List<PersonalTrainingAppointment> list = new ArrayList<>();
        String sql = "SELECT * FROM personal_training_appointment " +
                "WHERE member_id = ? ORDER BY appointment_date DESC";
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

    // Returns all appointments assigned to a given trainer, ordered newest first
    @Override
    public List<PersonalTrainingAppointment> findByTrainerId(int trainerId) {
        List<PersonalTrainingAppointment> list = new ArrayList<>();
        String sql = "SELECT * FROM personal_training_appointment " +
                "WHERE trainer_id = ? ORDER BY appointment_date DESC";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByTrainerId failed: " + e.getMessage(), e);
        }
        return list;
    }

    // Checks whether a trainer already has a SCHEDULED appointment overlapping the given time slot
    // Uses standard interval overlap formula: existing.start < newEnd AND existing.end > newStart
    // This catches all overlap cases: partial overlap, full containment, exact match
    @Override
    public boolean isSlotTaken(int trainerId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        String sql = "SELECT COUNT(*) FROM personal_training_appointment " +
                "WHERE trainer_id = ? AND appointment_date = ? AND status = 'SCHEDULED' " +
                "AND start_time < ? AND end_time > ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trainerId);
            ps.setDate(2, Date.valueOf(date));
            // existing.start < newEnd
            ps.setTime(3, Time.valueOf(endTime));
            // existing.end > newStart
            ps.setTime(4, Time.valueOf(startTime));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("isSlotTaken failed: " + e.getMessage(), e);
        }
        return false;
    }

    // Checks whether a member already has a SCHEDULED appointment overlapping the given time slot
    // Same overlap formula as isSlotTaken — prevents double-booking for the member
    @Override
    public boolean hasMemberConflict(int memberId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        String sql = "SELECT COUNT(*) FROM personal_training_appointment " +
                "WHERE member_id = ? AND appointment_date = ? AND status = 'SCHEDULED' " +
                "AND start_time < ? AND end_time > ?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setDate(2, Date.valueOf(date));
            ps.setTime(3, Time.valueOf(endTime));
            ps.setTime(4, Time.valueOf(startTime));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("hasMemberConflict failed: " + e.getMessage(), e);
        }
        return false;
    }

    // Counts how many PT appointments a member has in a given month
    // Counts SCHEDULED and COMPLETED only — CANCELLED and NO_SHOW do not count toward the limit
    @Override
    public int countMonthlyAppointments(int memberId, int year, int month) {
        String sql = "SELECT COUNT(*) FROM personal_training_appointment " +
                "WHERE member_id = ? AND YEAR(appointment_date) = ? AND MONTH(appointment_date) = ? " +
                "AND status IN ('SCHEDULED','COMPLETED')";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, year);
            ps.setInt(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("countMonthlyAppointments failed: " + e.getMessage(), e);
        }
        return 0;
    }

    // Finds the latest active no-show penalty expiry date for a member
    // Uses MAX() in case multiple penalties exist — returns the furthest future date
    // Returns Optional.empty() if the member has no active penalty
    // Called at booking time to enforce the 7-day no-show ban
    @Override
    public Optional<LocalDate> findActiveNoShowPenalty(int memberId) {
        String sql = "SELECT MAX(no_show_penalty_until) FROM personal_training_appointment " +
                "WHERE member_id = ? AND no_show_penalty_until >= CURDATE()";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate(1);
                    // d can be null if no active penalty exists
                    if (d != null) return Optional.of(d.toLocalDate());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findActiveNoShowPenalty failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // Maps a single ResultSet row to a PersonalTrainingAppointment object
    // Handles nullable fields: no_show_penalty_until may be null
    private PersonalTrainingAppointment mapRow(ResultSet rs) throws SQLException {
        PersonalTrainingAppointment apt = new PersonalTrainingAppointment();
        apt.setAppointmentId(rs.getInt("appointment_id"));
        apt.setMemberId(rs.getInt("member_id"));
        apt.setTrainerId(rs.getInt("trainer_id"));
        // Convert SQL Date → Java LocalDate
        apt.setAppointmentDate(rs.getDate("appointment_date").toLocalDate());
        // Convert SQL Time → Java LocalTime
        apt.setStartTime(rs.getTime("start_time").toLocalTime());
        apt.setEndTime(rs.getTime("end_time").toLocalTime());
        apt.setStatus(rs.getString("status"));
        // Null check required — penalty is only set after a NO_SHOW
        Date penalty = rs.getDate("no_show_penalty_until");
        if (penalty != null) apt.setNoShowPenaltyUntil(penalty.toLocalDate());
        apt.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return apt;
    }
}