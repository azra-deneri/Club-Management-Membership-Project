package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;

// Service class responsible for all member-related business operations
// Handles registration, approval, tier upgrades, freezing, profile updates, and BMI
public class MemberService {

    private final MemberDAO memberDAO;
    private final MembershipDAO membershipDAO;
    private final RequestDAO requestDAO;
    private final PaymentDAO paymentDAO;

    // Default constructor — creates concrete DAO implementations
    public MemberService() {
        this.memberDAO     = new MemberDAOImpl();
        this.membershipDAO = new MembershipDAOImpl();
        this.requestDAO    = new RequestDAOImpl();
        this.paymentDAO    = new PaymentDAOImpl();
    }

    // Constructor for unit testing — allows injecting mock DAO objects
    public MemberService(MemberDAO memberDAO, MembershipDAO membershipDAO,
                         RequestDAO requestDAO, PaymentDAO paymentDAO) {
        this.memberDAO     = memberDAO;
        this.membershipDAO = membershipDAO;
        this.requestDAO    = requestDAO;
        this.paymentDAO    = paymentDAO;
    }

    // Creates a new member record with PENDING status and a registration request
    // Used when a member self-registers through the registration form (UC-M02)
    // Password is hashed before saving if not already hashed
    public void createRegistrationRequest(Member member, String tier, String packageType) {
        validateMember(member);
        if (!member.getPassword().startsWith("$2a$"))
            member.setPassword(AuthService.hashPassword(member.getPassword()));
        member.setStatus("PENDING");
        memberDAO.insert(member);

        // Re-fetch to get the auto-generated member_id from DB
        Member saved = memberDAO.findByPhone(member.getPhone())
                .orElseThrow(() -> new IllegalStateException("Member not found after insert: " + member.getPhone()));

        RegistrationRequest req = new RegistrationRequest();
        req.setMemberId(saved.getMemberId());
        req.setTier(tier);
        req.setPackageType(packageType);
        req.setAmount(calculateAmount(tier, packageType));
        // Request expires in 3 days if not approved
        req.setExpiresAt(LocalDateTime.now().plusDays(3));
        requestDAO.insertRegistration(req);
    }

    // Creates a renewal request for an existing member
    // Used when a PASSIVE member wants to renew their membership (UC-M08)
    public void createRenewalRequest(int memberId, String tier, String packageType) {
        RegistrationRequest req = new RegistrationRequest();
        req.setMemberId(memberId);
        req.setType("RENEWAL");
        req.setTier(tier);
        req.setPackageType(packageType);
        req.setAmount(calculateAmount(tier, packageType));
        req.setExpiresAt(LocalDateTime.now().plusDays(3));
        requestDAO.insertRegistration(req);
    }

    // Registers a member directly without approval flow — used by manager (UC-A02)
    // Creates member, membership, and payment records in one operation
    public void registerMember(Member member, String tier, String packageType, int managerId) {
        validateMember(member);
        if (!member.getPassword().startsWith("$2a$"))
            member.setPassword(AuthService.hashPassword(member.getPassword()));
        member.setStatus("ACTIVE");
        memberDAO.insert(member);

        // Re-fetch to get the auto-generated member_id from DB
        Member saved = memberDAO.findByPhone(member.getPhone())
                .orElseThrow(() -> new IllegalStateException("Member not found after insert: " + member.getPhone()));

        Membership ms = new Membership();
        ms.setMemberId(saved.getMemberId());
        ms.setTier(tier);
        ms.setPackageType(packageType);
        ms.setStartDate(LocalDate.now());
        ms.setEndDate(calcEndDate(packageType));
        ms.setStatus("ACTIVE");
        ms.setFreezeCount(0);
        membershipDAO.insert(ms);

        Payment payment = new Payment();
        payment.setMemberId(saved.getMemberId());
        payment.setAmount(calculateAmount(tier, packageType));
        payment.setPaymentType("MEMBERSHIP");
        payment.setDescription(tier + " - " + packageType);
        payment.setStatus("PAID");
        payment.setRecordedBy(managerId);
        paymentDAO.insert(payment);
    }

    // Approves a pending registration or renewal request
    // For RENEWAL: old ACTIVE membership is set to PASSIVE BEFORE new one is inserted
    // This prevents the new membership from being accidentally set to PASSIVE
    public void approveRegistration(int requestId, int recordedBy) {
        List<RegistrationRequest> reqs = requestDAO.findPendingRegistrations();
        RegistrationRequest req = reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Registration request not found: " + requestId));

        requestDAO.updateRegistrationStatus(requestId, "APPROVED");
        memberDAO.updateStatus(req.getMemberId(), "ACTIVE");

        // FIX: For RENEWAL — set old ACTIVE memberships to PASSIVE BEFORE inserting new one
        // If done after insert, the new membership would also be caught by the filter and set to PASSIVE
        if ("RENEWAL".equals(req.getType())) {
            membershipDAO.findAllByMemberId(req.getMemberId()).stream()
                    .filter(m -> "ACTIVE".equals(m.getStatus()))
                    .forEach(m -> membershipDAO.updateStatus(m.getMembershipId(), "PASSIVE"));
        }

        // Insert new membership AFTER old ones are passivated
        Membership ms = new Membership();
        ms.setMemberId(req.getMemberId());
        ms.setTier(req.getTier());
        ms.setPackageType(req.getPackageType());
        ms.setStartDate(LocalDate.now());
        ms.setEndDate(calcEndDate(req.getPackageType()));
        ms.setStatus("ACTIVE");
        ms.setFreezeCount(0);
        membershipDAO.insert(ms);

        Payment payment = new Payment();
        payment.setMemberId(req.getMemberId());
        payment.setAmount(req.getAmount());
        payment.setPaymentType("MEMBERSHIP");
        payment.setDescription(req.getTier() + " - " + req.getPackageType());
        payment.setStatus("PAID");
        payment.setRecordedBy(recordedBy);
        paymentDAO.insert(payment);
    }

    // Rejects a pending registration request
    // For RENEWAL: only status is updated to REJECTED — member record is kept
    // For INITIAL: request and member record are both deleted (registration failed)
    public void rejectRegistration(int requestId) {
        List<RegistrationRequest> reqs = requestDAO.findPendingRegistrations();
        reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .ifPresent(r -> {
                    if ("RENEWAL".equals(r.getType())) {
                        requestDAO.updateRegistrationStatus(requestId, "REJECTED");
                    } else {
                        requestDAO.deleteRegistration(requestId);
                        memberDAO.deleteById(r.getMemberId());
                    }
                });
    }

    // Expires all pending requests that have passed their expiry timestamp
    // For INITIAL registrations: deletes the request and the member record
    // For RENEWAL registrations: only marks as EXPIRED (member record kept)
    // Also expires all pending tier upgrade requests
    public void expireOldRequests() {
        List<RegistrationRequest> pending = requestDAO.findPendingRegistrations();
        pending.stream()
                .filter(r -> r.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(r -> {
                    if (!"RENEWAL".equals(r.getType())) {
                        requestDAO.deleteRegistration(r.getRequestId());
                        memberDAO.deleteById(r.getMemberId());
                    }
                });
        requestDAO.expireOldRegistrationRequests();

        List<TierUpgradeRequest> pendingUpgrades = requestDAO.findPendingTierUpgrades();
        pendingUpgrades.stream()
                .filter(r -> r.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(r -> requestDAO.updateTierUpgradeStatus(r.getRequestId(), "EXPIRED"));
    }

    // Creates a tier upgrade request for a member
    // Member must have an active membership to request an upgrade
    public void createTierUpgradeRequest(int memberId, int membershipId,
                                         String oldTier, String newTier,
                                         String packageType, double fee) {
        Optional<Membership> ms = membershipDAO.findActiveByMemberId(memberId);
        if (ms.isEmpty()) throw new IllegalStateException("No active membership found.");

        TierUpgradeRequest req = new TierUpgradeRequest();
        req.setMemberId(memberId);
        req.setMembershipId(membershipId);
        req.setOldTier(oldTier);
        req.setNewTier(newTier);
        req.setPackageType(packageType);
        req.setUpgradeFee(fee);
        req.setExpiresAt(LocalDateTime.now().plusDays(3));
        requestDAO.insertTierUpgrade(req);
    }

    // Approves a tier upgrade request
    // Updates membership tier, package type, end date, and records payment
    public void approveTierUpgrade(int requestId, int recordedBy) {
        List<TierUpgradeRequest> reqs = requestDAO.findPendingTierUpgrades();
        TierUpgradeRequest req = reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tier upgrade request not found: " + requestId));

        requestDAO.updateTierUpgradeStatus(requestId, "APPROVED");
        membershipDAO.updateTier(req.getMembershipId(), req.getNewTier());

        if (req.getPackageType() != null) {
            membershipDAO.updatePackageType(req.getMembershipId(), req.getPackageType());
            LocalDate newEndDate = calcEndDate(req.getPackageType());
            membershipDAO.updateEndDate(req.getMembershipId(), newEndDate);
        }

        Payment payment = new Payment();
        payment.setMemberId(req.getMemberId());
        payment.setAmount(req.getUpgradeFee());
        payment.setPaymentType("UPGRADE");
        payment.setDescription(req.getOldTier() + " → " + req.getNewTier()
                + " (" + req.getPackageType() + ")");
        payment.setStatus("PAID");
        payment.setRecordedBy(recordedBy);
        paymentDAO.insert(payment);
    }

    // Rejects a tier upgrade request
    public void failTierUpgrade(int requestId) {
        requestDAO.updateTierUpgradeStatus(requestId, "REJECTED");
    }

    // Freezes a membership for a given number of days
    // Freeze limits per tier: CLASSIC = 1, GOLD = 2, VIP = 3
    // Cannot freeze within 3 days of membership expiry
    // Extends end date by the freeze duration and sets status to FROZEN
    public void freezeMembership(int membershipId, int days) {
        Membership ms = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new IllegalStateException("Membership not found."));

        // Determine freeze limit based on tier
        int maxFreeze = switch (ms.getTier()) {
            case "GOLD" -> 2;
            case "VIP"  -> 3;
            default     -> 1;
        };

        if (ms.getFreezeCount() >= maxFreeze)
            throw new IllegalStateException("Freeze limit reached for " + ms.getTier() + " tier.");

        // Cannot freeze within 3 days of membership expiry date
        if (ms.getEndDate().minusDays(3).isBefore(LocalDate.now()))
            throw new IllegalStateException("Cannot freeze within 3 days of expiry.");

        LocalDate newEnd = ms.getEndDate().plusDays(days);
        membershipDAO.updateEndDate(membershipId, newEnd);
        membershipDAO.updateStatus(membershipId, "FROZEN");
        membershipDAO.incrementFreezeCount(membershipId);
        memberDAO.updateStatus(ms.getMemberId(), "FROZEN");
    }

    // Unfreezes a membership — sets both membership and member status back to ACTIVE
    public void unfreezeMembership(int membershipId) {
        Membership ms = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new IllegalStateException("Membership not found."));
        membershipDAO.updateStatus(membershipId, "ACTIVE");
        memberDAO.updateStatus(ms.getMemberId(), "ACTIVE");
    }

    // Directly inserts a registration request — used for renewal submissions
    public void submitRegistrationRequest(RegistrationRequest req) {
        requestDAO.insertRegistration(req);
    }

    // --- Query Methods ---

    public Optional<Member> getMemberById(int memberId) {
        return memberDAO.findById(memberId);
    }

    public List<Member> getAllMembers() {
        return memberDAO.findAll();
    }

    public List<Member> getMembersByStatus(String status) {
        return memberDAO.findByStatus(status);
    }

    public Optional<Membership> getActiveMembership(int memberId) {
        return membershipDAO.findActiveByMemberId(memberId);
    }

    public List<Membership> getAllMemberships(int memberId) {
        return membershipDAO.findAllByMemberId(memberId);
    }

    public List<RegistrationRequest> getPendingRegistrations() {
        return requestDAO.findPendingRegistrations();
    }

    public List<TierUpgradeRequest> getPendingTierUpgrades() {
        return requestDAO.findPendingTierUpgrades();
    }

    public List<Payment> getPaymentsByMember(int memberId) {
        return paymentDAO.findByMemberId(memberId);
    }

    // Suspends a member account — member cannot log in while suspended
    public void suspendMember(int memberId) {
        memberDAO.updateStatus(memberId, "SUSPENDED");
    }

    // Unlocks a member account and resets failed attempt counter
    public void unlockMember(int memberId) {
        memberDAO.updateLockStatus(memberId, false);
        memberDAO.updateFailedAttempts(memberId, 0);
    }

    // Updates member profile fields and recalculates BMI if weight and height are provided
    public void updateMemberProfile(int memberId, Double weight, Double height,
                                    String ecName, String ecPhone) {
        memberDAO.updateProfile(memberId, weight, height, ecName, ecPhone);
        if (weight != null && height != null && height > 0) {
            double heightM = height / 100.0;
            double bmi = Math.round((weight / (heightM * heightM)) * 100.0) / 100.0;
            memberDAO.updateBmi(memberId, bmi, calcBmiCategory(bmi));
        }
    }

    // --- Utility Methods ---

    // Calculates the membership fee based on tier and package type
    // Base monthly prices: CLASSIC = 750, GOLD = 1250, VIP = 2000
    // ANNUAL_PREPAID: 15% discount on 12 months
    // ANNUAL_INSTALLMENT: 7% surcharge on monthly price
    public double calculateAmount(String tier, String packageType) {
        double monthly = switch (tier) {
            case "GOLD" -> 1250.0;
            case "VIP"  -> 2000.0;
            default     -> 750.0;
        };
        return switch (packageType) {
            case "ANNUAL_PREPAID"     -> monthly * 12 * 0.85;
            case "ANNUAL_INSTALLMENT" -> monthly * 1.07;
            default                   -> monthly;
        };
    }

    // Calculates the membership end date based on package type
    // ANNUAL packages: 365 days | MONTHLY: 30 days
    private LocalDate calcEndDate(String packageType) {
        return switch (packageType) {
            case "ANNUAL_PREPAID", "ANNUAL_INSTALLMENT" -> LocalDate.now().plusDays(365);
            default -> LocalDate.now().plusDays(30);
        };
    }

    // Returns the BMI category label based on BMI value
    private String calcBmiCategory(double bmi) {
        if (bmi < 18.5) return "UNDERWEIGHT";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "OVERWEIGHT";
        return "OBESE";
    }

    // Validates member data before insert
    // Checks: age >= 18, phone not blank, phone unique, email unique
    private void validateMember(Member member) {
        if (member.getDateOfBirth() == null)
            throw new IllegalArgumentException("Date of birth is required.");
        if (member.getPhone() == null || member.getPhone().isBlank())
            throw new IllegalArgumentException("Phone number is required.");
        if (member.getDateOfBirth().isAfter(LocalDate.now().minusYears(18)))
            throw new IllegalArgumentException("Minimum age is 18.");
        if (memberDAO.existsByPhone(member.getPhone()))
            throw new IllegalArgumentException("Phone number already registered.");
        if (member.getEmail() != null && memberDAO.existsByEmail(member.getEmail()))
            throw new IllegalArgumentException("Email address already registered.");
    }

    // Updates a member's phone number after uniqueness check
    // Skips the check if the new phone is the same as the current one
    public void updatePhone(int memberId, String newPhone) {
        memberDAO.findById(memberId).ifPresent(m -> {
            if (!newPhone.equals(m.getPhone()) && memberDAO.existsByPhone(newPhone))
                throw new IllegalArgumentException("Phone number already registered.");
        });
        memberDAO.updatePhone(memberId, newPhone);
    }

    // Updates a member's email address after uniqueness check
    // Skips the check if the new email is the same as the current one or blank
    public void updateEmail(int memberId, String newEmail) {
        memberDAO.findById(memberId).ifPresent(m -> {
            if (newEmail != null && !newEmail.isBlank()
                    && !newEmail.equals(m.getEmail())
                    && memberDAO.existsByEmail(newEmail))
                throw new IllegalArgumentException("Email address already registered.");
        });
        memberDAO.updateEmail(memberId, newEmail);
    }

    public List<RegistrationRequest> getAllRegistrations() {
        return requestDAO.findAllRegistrations();
    }

    public List<TierUpgradeRequest> getAllTierUpgrades() {
        return requestDAO.findAllTierUpgrades();
    }

    // Calculates BMI value from weight (kg) and height (cm)
    // Result is rounded to 2 decimal places
    public double calculateBmi(double weight, double height) {
        double heightM = height / 100.0;
        return Math.round((weight / (heightM * heightM)) * 100.0) / 100.0;
    }

    // Returns the BMI category label for a given BMI value
    public String getBmiCategory(double bmi) {
        if (bmi < 18.5) return "UNDERWEIGHT";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "OVERWEIGHT";
        return "OBESE";
    }

    // Returns lifestyle suggestions based on BMI category
    public String[] getBmiSuggestions(String category) {
        return switch (category) {
            case "UNDERWEIGHT" -> new String[]{
                    "Increase caloric intake with nutrient-dense foods.",
                    "Consider strength training to build muscle mass.",
                    "Consult a nutritionist for a personalized meal plan."
            };
            case "NORMAL" -> new String[]{
                    "Maintain your current healthy lifestyle.",
                    "Regular exercise 3-5 times per week is recommended.",
                    "Stay hydrated and keep a balanced diet."
            };
            case "OVERWEIGHT" -> new String[]{
                    "Aim for 150-300 minutes of moderate exercise per week.",
                    "Reduce processed foods and sugary drinks.",
                    "Consider consulting a nutritionist."
            };
            case "OBESE" -> new String[]{
                    "Consult a healthcare professional for a safe weight-loss plan.",
                    "Start with low-impact exercises such as walking or swimming.",
                    "Focus on gradual, sustainable lifestyle changes."
            };
            default -> new String[]{"No suggestions available."};
        };
    }

    // Calculates estimated daily calorie needs using the Harris-Benedict formula
    // FIX: Uses Period.between() for accurate age calculation
    // (year difference alone is inaccurate if birthday hasn't occurred yet this year)
    // Activity multiplier is set to 1.55 (moderately active)
    public double calculateDailyCalories(double weight, double height,
                                         String gender, LocalDate dob) {
        // Accurate age calculation — accounts for whether birthday has passed this year
        int age = Period.between(dob, LocalDate.now()).getYears();
        double bmr;
        if ("MALE".equals(gender))
            bmr = 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age);
        else
            bmr = 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age);
        return bmr * 1.55;
    }
}