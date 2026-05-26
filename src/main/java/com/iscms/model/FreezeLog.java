package com.iscms.model;

import java.time.LocalDate;

// Audit row written when a membership is frozen. Used by Phase 2's partial
// unfreeze: if a member unfreezes early, the unused tail of the freeze is
// refunded by shortening the membership end date back down.
public record FreezeLog(int membershipId, LocalDate freezeStart, LocalDate freezeEnd) {}
