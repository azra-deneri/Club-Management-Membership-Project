package com.iscms.dao;

import com.iscms.model.FreezeLog;
import com.iscms.model.Membership;
import java.util.List;
import java.util.Optional;

// DAO interface for membership database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface MembershipDAO {

    // Insert a new membership record into the database
    void insert(Membership membership);

    // Find the currently ACTIVE membership for a given member
    // Returns Optional.empty() if the member has no active membership
    Optional<Membership> findActiveByMemberId(int memberId);

    // Find the currently FROZEN membership for a given member
    // Returns Optional.empty() if the member has no frozen membership
    Optional<Membership> findFrozenByMemberId(int memberId);

    // Return all membership records for a given member (all statuses, all history)
    List<Membership> findAllByMemberId(int memberId);

    // Update the status of a membership
    // Valid values: ACTIVE, PASSIVE, SUSPENDED, FROZEN
    void updateStatus(int membershipId, String status);

    // Update the tier of an existing membership
    // Called when a tier upgrade request is approved (e.g., CLASSIC → GOLD)
    void updateTier(int membershipId, String newTier);

    // Update the end date of a membership
    // Called when a renewal is approved or freeze period ends
    void updateEndDate(int membershipId, java.time.LocalDate newEndDate);

    // Increment the freeze counter by 1 for a membership
    // Used to track how many times a member has frozen (BR-06: freeze limit per tier)
    void incrementFreezeCount(int membershipId);

    // Find a single membership record by its ID
    // Returns Optional.empty() if not found
    Optional<Membership> findById(int membershipId);

    // Update the package type of a membership
    // Called when package changes during renewal or upgrade approval
    void updatePackageType(int membershipId, String packageType);

    // Phase 2 — freeze audit log support (see membership_freeze_log table).
    // Project 1 ships the schema but never reads/writes it; Phase 2 starts
    // using it so members can self-unfreeze early without a full-period refund.
    void insertFreezeLog(int membershipId, java.time.LocalDate start, java.time.LocalDate end);
    java.util.Optional<FreezeLog> findLatestFreezeLog(int membershipId);
}