package com.iscms.web.dto;

/**
 * Manager/Admin projection for view templates.
 *
 * Same security principle as MemberDTO: password hash and lockout state are
 * excluded from the view. Role is included because admin/manager dashboards
 * render different tiles for ADMIN vs MANAGER, and the template needs to
 * read this field. The `locked` flag is included so the admin's manager list
 * can show the lock indicator and toggle button — it is a UI status, not a
 * security secret like the failed-attempts counter or the password hash.
 */
public record ManagerDTO(
        int managerId,
        String fullName,
        String username,
        String email,
        String role,
        boolean locked
) {
}