package com.iscms.web.dto;

import java.time.LocalDate;

/**
 * Member projection for view templates.
 *
 * Critical: the password hash, failedAttempts, and isLocked flag from the
 * Member entity are deliberately omitted. This DTO carries only fields that
 * the Thymeleaf templates need — preventing accidental exposure of sensitive
 * data through ${member.password} in any view, even if a developer forgets.
 */
public record MemberDTO(
        int memberId,
        String fullName,
        String phone,
        String email,
        String gender,
        LocalDate dateOfBirth,
        Double weight,
        Double height,
        String emergencyContactName,
        String emergencyContactPhone,
        String status
) {
}
