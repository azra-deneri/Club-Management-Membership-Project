package com.iscms.web.dto;

/**
 * Trainer projection for view templates.
 */
public record TrainerDTO(
        int trainerId,
        String fullName,
        String username,
        String email,
        String specialty,
        boolean isActive
) {
}
