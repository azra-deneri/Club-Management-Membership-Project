package com.iscms.dao;

import com.iscms.model.RegistrationRequest;
import com.iscms.model.TierUpgradeRequest;
import java.util.List;

public interface RequestDAO {
    void insertRegistration(RegistrationRequest req); // Create new registration request
    List<RegistrationRequest> findPendingRegistrations(); // List all registration requests
    void updateRegistrationStatus(int requestId, String status); // Update registration status ("PENDING","APPROVED","REJECTED")
    void expireOldRegistrationRequests(); // Expire in 3 days - cannot turned to approved

    void insertTierUpgrade(TierUpgradeRequest req);
    List<TierUpgradeRequest> findPendingTierUpgrades();
    void updateTierUpgradeStatus(int requestId, String status);
    void deleteRegistration(int requestId);
    List<RegistrationRequest> findAllRegistrations();
    List<TierUpgradeRequest> findAllTierUpgrades();
}