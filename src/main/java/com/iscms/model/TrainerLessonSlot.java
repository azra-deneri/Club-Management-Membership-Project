package com.iscms.model;

import java.time.LocalTime;

// Model class representing a specific bookable lesson slot for a trainer (POJO)
// Maps to the trainer_lesson_slot table in the database
// A lesson slot defines WHEN a trainer is available for PT appointments
// Members see these slots in the weekly booking view (PTPanel)
public class TrainerLessonSlot {

    // Unique identifier for this lesson slot (maps to slot_id in DB)
    private int slotId;

    // ID of the trainer who owns this slot (FK → trainer table)
    private int trainerId;

    // Day of the week this slot is available
    // Values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    private String dayOfWeek;

    // Time the lesson slot starts
    private LocalTime startTime;

    // Time the lesson slot ends
    private LocalTime endTime;

    // No-arg constructor required for JDBC result mapping
    public TrainerLessonSlot() {}

    // --- Getters and Setters ---

    public int getSlotId() { return slotId; }
    public void setSlotId(int slotId) { this.slotId = slotId; }

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