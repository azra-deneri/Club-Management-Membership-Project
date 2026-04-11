package com.iscms.model;

import java.time.LocalTime;

// Model class representing a trainer's general working hours for a given day
// Maps to the trainer_working_day table in the database
// Working days define WHEN a trainer is at the club (general availability)
// Different from TrainerLessonSlot — lesson slots are the specific bookable PT time blocks
public class TrainerWorkingDay {

    // Unique identifier for this working day record (maps to wd_id in DB)
    private int wdId;

    // ID of the trainer this working day belongs to (FK → trainer table)
    private int trainerId;

    // Day of the week the trainer works
    // Values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    private String dayOfWeek;

    // Time the trainer's working shift starts on this day
    private LocalTime startTime;

    // Time the trainer's working shift ends on this day
    private LocalTime endTime;

    // No-arg constructor required for JDBC result mapping
    public TrainerWorkingDay() {}

    // --- Getters and Setters ---

    public int getWdId() { return wdId; }
    public void setWdId(int wdId) { this.wdId = wdId; }

    public int getTrainerId() { return trainerId; }
    public void setTrainerId(int trainerId) { this.trainerId = trainerId; }

    // Day stored as String — DB enum values are MONDAY through SUNDAY
    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
}