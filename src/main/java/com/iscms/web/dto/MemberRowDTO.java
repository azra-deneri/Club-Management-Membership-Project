package com.iscms.web.dto;

/**
 * Compact member projection for the manager's member list table.
 *
 * Replaces the inline MemberRow record previously defined as a nested
 * class inside ManagerController. Pulling it into the dto package
 * lets us reuse the same shape across pages, exports, and reports,
 * and keeps the controller focused on routing rather than view-model
 * declarations (Week 14 — value objects belong in their own layer).
 */
public record MemberRowDTO(
        int id,
        String name,
        String phone,
        String email,
        String status,
        String tier,
        String createdDate,
        boolean locked
) {
}