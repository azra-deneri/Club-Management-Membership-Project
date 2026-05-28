package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;
import com.iscms.service.policy.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Service class responsible for all member-related business operations.
//
// Batch 4 additions:
//   - InstallmentDAO injection for the ANNUAL_INSTALLMENT schedule lifecycle
//   - Cancellation flow (cancelMembership, deleteAccountSelf, deleteAccountByManager)
//   - Auto-delete sweep (expireOldPassiveMembers)
//   - Installment creation, payment, and overdue detection
//
// Two parallel registration flows still exist:
//   1. Cash flow:    user/manager submits → manager approves → ACTIVE
//   2. Online flow:  user pays directly via mock card → ACTIVE immediately
// ANNUAL_INSTALLMENT is online-only — the cash path's UI removes it from the package list.
public class MemberService {

    private final MemberDAO memberDAO;
    private final MembershipDAO membershipDAO;
    private final RequestDAO requestDAO;
    private final PaymentDAO paymentDAO;
    private final InstallmentDAO installmentDAO;

    // PASSIVE members are auto-deleted after this many days. 365 = 1 year.
    private static final int PASSIVE_AUTO_DELETE_DAYS = 365;

    // Number of monthly installments in an ANNUAL_INSTALLMENT schedule
    private static final int INSTALLMENT_COUNT = 12;

    public MemberService() {
        this.memberDAO       = new MemberDAOImpl();
        this.membershipDAO   = new MembershipDAOImpl();
        this.requestDAO      = new RequestDAOImpl();
        this.paymentDAO      = new PaymentDAOImpl();
        this.installmentDAO  = new InstallmentDAOImpl();
    }

    public MemberService(MemberDAO memberDAO, MembershipDAO membershipDAO,
                         RequestDAO requestDAO, PaymentDAO paymentDAO,
                         InstallmentDAO installmentDAO) {
        this.memberDAO       = memberDAO;
        this.membershipDAO   = membershipDAO;
        this.requestDAO      = requestDAO;
        this.paymentDAO      = paymentDAO;
        this.installmentDAO  = installmentDAO;
    }

    // === Cash flow — manager approval required ===

    public void createRegistrationRequest(Member member, String tier, String packageType) {
        validateMember(member);
        if (!member.getPassword().startsWith("$2a$"))
            member.setPassword(AuthService.hashPassword(member.getPassword()));
        member.setStatus("PENDING");
        memberDAO.insert(member);

        Member saved = memberDAO.findByPhone(member.getPhone())
                .orElseThrow(() -> new IllegalStateException("Member not found after insert: " + member.getPhone()));

        RegistrationRequest req = new RegistrationRequest();
        req.setMemberId(saved.getMemberId());
        req.setTier(tier);
        req.setPackageType(packageType);
        req.setAmount(calculateAmount(tier, packageType));
        req.setExpiresAt(LocalDateTime.now().plusDays(3));
        requestDAO.insertRegistration(req);
    }

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

    // Cash flow: manager registers a member directly. Goes straight to ACTIVE.
    // ANNUAL_INSTALLMENT is intentionally not supported via this path (UI hides it).
    public void registerMember(Member member, String tier, String packageType, int managerId) {
        validateMember(member);
        if (!member.getPassword().startsWith("$2a$"))
            member.setPassword(AuthService.hashPassword(member.getPassword()));
        member.setStatus("ACTIVE");
        memberDAO.insert(member);

        Member saved = memberDAO.findByPhone(member.getPhone())
                .orElseThrow(() -> new IllegalStateException("Member not found after insert: " + member.getPhone()));

        Membership ms = createMembership(saved.getMemberId(), tier, packageType);
        recordMembershipPayment(saved.getMemberId(), tier, packageType,
                calculateAmount(tier, packageType), managerId, "CASH", null);
    }

    public void approveRegistration(int requestId, int recordedBy) {
        List<RegistrationRequest> reqs = requestDAO.findPendingRegistrations();
        RegistrationRequest req = reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Registration request not found: " + requestId));

        requestDAO.updateRegistrationStatus(requestId, "APPROVED");
        memberDAO.updateStatus(req.getMemberId(), "ACTIVE");
        // Renewing — clear PASSIVE-state markers if present
        memberDAO.setPassiveSince(req.getMemberId(), null);
        memberDAO.clearCancellationRequested(req.getMemberId());

        if ("RENEWAL".equals(req.getType())) {
            membershipDAO.findAllByMemberId(req.getMemberId()).stream()
                    .filter(m -> "ACTIVE".equals(m.getStatus()))
                    .forEach(m -> membershipDAO.updateStatus(m.getMembershipId(), "PASSIVE"));
        }

        Membership ms = createMembership(req.getMemberId(), req.getTier(), req.getPackageType());
        recordMembershipPayment(req.getMemberId(), req.getTier(), req.getPackageType(),
                req.getAmount(), recordedBy, "CASH", null);
    }

    public void rejectRegistration(int requestId) {
        List<RegistrationRequest> reqs = requestDAO.findPendingRegistrations();
        reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .ifPresent(r -> {
                    // Mark the request REJECTED so it remains visible in the audit
                    // trail. For NEW/INITIAL the placeholder member account is
                    // deleted so the same phone/email can register again.
                    requestDAO.updateRegistrationStatus(requestId, "REJECTED");
                    if (!"RENEWAL".equals(r.getType())) {
                        memberDAO.deleteById(r.getMemberId());
                    }
                });
    }

    public void expireOldRequests() {
        List<RegistrationRequest> pending = requestDAO.findPendingRegistrations();
        pending.stream()
                .filter(r -> r.getExpiresAt().isBefore(LocalDateTime.now()))
                .filter(r -> !"RENEWAL".equals(r.getType()))
                .forEach(r -> {
                    // The request itself stays in the DB — the bulk update below
                    // flips its status to EXPIRED so it remains visible in the
                    // audit trail. Only the placeholder member is removed so the
                    // phone/email can be reused.
                    memberDAO.deleteById(r.getMemberId());
                });
        requestDAO.expireOldRegistrationRequests();   // PENDING → EXPIRED in bulk

        List<TierUpgradeRequest> pendingUpgrades = requestDAO.findPendingTierUpgrades();
        pendingUpgrades.stream()
                .filter(r -> r.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(r -> requestDAO.updateTierUpgradeStatus(r.getRequestId(), "EXPIRED"));
    }

    public void createTierUpgradeRequest(int memberId, int membershipId,
                                         String oldTier, String newTier, double fee) {
        Optional<Membership> ms = membershipDAO.findActiveByMemberId(memberId);
        if (ms.isEmpty()) throw new IllegalStateException("No active membership found.");

        TierUpgradeRequest req = new TierUpgradeRequest();
        req.setMemberId(memberId);
        req.setMembershipId(membershipId);
        req.setOldTier(oldTier);
        req.setNewTier(newTier);
        req.setPackageType(ms.get().getPackageType());
        req.setUpgradeFee(fee);
        req.setExpiresAt(LocalDateTime.now().plusDays(3));
        requestDAO.insertTierUpgrade(req);
    }

    public void approveTierUpgrade(int requestId, int recordedBy) {
        List<TierUpgradeRequest> reqs = requestDAO.findPendingTierUpgrades();
        TierUpgradeRequest req = reqs.stream()
                .filter(r -> r.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tier upgrade request not found: " + requestId));

        requestDAO.updateTierUpgradeStatus(requestId, "APPROVED");
        membershipDAO.updateTier(req.getMembershipId(), req.getNewTier());

        Payment payment = new Payment();
        payment.setMemberId(req.getMemberId());
        payment.setAmount(req.getUpgradeFee());
        payment.setPaymentType("UPGRADE");
        payment.setDescription(req.getOldTier() + " → " + req.getNewTier());
        payment.setStatus("PAID");
        payment.setRecordedBy(recordedBy);
        payment.setPaymentMethod("CASH");
        paymentDAO.insert(payment);
    }

    public void failTierUpgrade(int requestId) {
        requestDAO.updateTierUpgradeStatus(requestId, "REJECTED");
    }

    // === Online flow — payment is the approval ===

    // Online flow: self-register with successful card payment. Member is ACTIVE
    // immediately. For ANNUAL_INSTALLMENT, also creates the 12-month installment schedule
    // with month #1 marked PAID (covered by this initial payment).
    public void selfRegisterMember(Member member, String tier, String packageType) {
        validateMember(member);
        if (!member.getPassword().startsWith("$2a$"))
            member.setPassword(AuthService.hashPassword(member.getPassword()));
        member.setStatus("ACTIVE");
        memberDAO.insert(member);

        Member saved = memberDAO.findByPhone(member.getPhone())
                .orElseThrow(() -> new IllegalStateException("Member not found after insert: " + member.getPhone()));

        Membership ms = createMembership(saved.getMemberId(), tier, packageType);
        Payment firstPayment = recordMembershipPayment(saved.getMemberId(), tier, packageType,
                calculateAmount(tier, packageType), 0, "ONLINE", null);

        if (PackageStrategyRegistry.forPackage(packageType).requiresInstallmentSchedule()) {
            createInstallmentSchedule(ms.getMembershipId(), saved.getMemberId(),
                    calculateAmount(tier, packageType), firstPayment.getPaymentId());
        }
    }

    // Online flow: PASSIVE member renews via card payment. Same shape as self-register
    // but for an existing member — no new member row, just status reset and a fresh membership.
    //
    // BR-78: ANNUAL_INSTALLMENT is a 12-month commitment. A member who has unpaid
    // installments from a previous membership cannot renew until that debt is cleared.
    // This prevents members from gaming the system by paying a few installments,
    // letting the membership go PASSIVE, then renewing with a clean slate.
    public void selfRenewMembership(int memberId, String tier, String packageType) {
        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Member not found: " + memberId));

        // BR-78: Block renewal if there are unpaid installments on file.
        int unpaid = countUnpaidInstallments(memberId);
        if (unpaid > 0) {
            throw new IllegalStateException(
                    "You have " + unpaid + " unpaid installment(s) from your previous membership. "
                            + "Please pay all installments before renewing.");
        }

        memberDAO.updateStatus(member.getMemberId(), "ACTIVE");
        // Member is back — clear the cancellation flag and the passive countdown
        memberDAO.setPassiveSince(member.getMemberId(), null);
        memberDAO.clearCancellationRequested(member.getMemberId());

        // Defensive: passivate any lingering ACTIVE memberships before inserting new one
        membershipDAO.findAllByMemberId(memberId).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .forEach(m -> membershipDAO.updateStatus(m.getMembershipId(), "PASSIVE"));

        Membership ms = createMembership(memberId, tier, packageType);
        Payment firstPayment = recordMembershipPayment(memberId, tier, packageType,
                calculateAmount(tier, packageType), 0, "ONLINE", null);

        if (PackageStrategyRegistry.forPackage(packageType).requiresInstallmentSchedule()) {
            createInstallmentSchedule(ms.getMembershipId(), memberId,
                    calculateAmount(tier, packageType), firstPayment.getPaymentId());
        }
    }
    // BR-78 helper: counts PENDING + OVERDUE installments across ALL of this
    // member's memberships (including PASSIVE ones). Used to block renewal
    // when there's outstanding debt from a previous ANNUAL_INSTALLMENT membership.
    public int countUnpaidInstallments(int memberId) {
        return (int) installmentDAO.findByMemberId(memberId).stream()
                .filter(i -> "PENDING".equals(i.getStatus()) || "OVERDUE".equals(i.getStatus()))
                .count();
    }

    // Online flow: tier upgrade via card payment. Package stays the same.
    public void selfUpgradeTier(int memberId, int membershipId,
                                String oldTier, String newTier, double fee) {
        Optional<Membership> ms = membershipDAO.findActiveByMemberId(memberId);
        if (ms.isEmpty()) throw new IllegalStateException("No active membership found.");

        membershipDAO.updateTier(membershipId, newTier);

        Payment payment = new Payment();
        payment.setMemberId(memberId);
        payment.setAmount(fee);
        payment.setPaymentType("UPGRADE");
        payment.setDescription(oldTier + " → " + newTier);
        payment.setStatus("PAID");
        payment.setRecordedBy(0);
        payment.setPaymentMethod("ONLINE");
        paymentDAO.insert(payment);
    }

    // === Installment lifecycle ===

    // Creates a 12-row installment schedule for an ANNUAL_INSTALLMENT membership.
    // Month #1 is marked PAID at creation time — it's covered by the activation payment
    // (linked via firstPaymentId). Months #2..12 are PENDING with monthly due dates.
    private void createInstallmentSchedule(int membershipId, int memberId,
                                           double monthlyAmount, int firstPaymentId) {
        List<Installment> schedule = new ArrayList<>();
        LocalDate today = LocalDate.now();
        BigDecimal amount = BigDecimal.valueOf(monthlyAmount);

        for (int i = 1; i <= INSTALLMENT_COUNT; i++) {
            Installment inst = new Installment();
            inst.setMembershipId(membershipId);
            inst.setMemberId(memberId);
            inst.setInstallmentNo(i);
            // Month 1 = today; month i = today + (i-1) months
            inst.setDueDate(today.plusMonths(i - 1));
            inst.setAmount(amount);

            if (i == 1) {
                inst.setStatus("PAID");
                inst.setPaidDate(LocalDateTime.now());
                inst.setPaymentId(firstPaymentId);
            } else {
                inst.setStatus("PENDING");
            }
            schedule.add(inst);
        }
        installmentDAO.insertAll(schedule);
    }

    // Pays a single overdue or pending installment via online card payment.
    // Caller must have already confirmed payment SUCCESS via MockPaymentDialog.
    // Creates a Payment row of type INSTALLMENT, then marks the installment PAID
    // and links it to the new payment.
    public void payInstallment(int installmentId) {
        Installment inst = installmentDAO.findById(installmentId)
                .orElseThrow(() -> new IllegalStateException("Installment not found: " + installmentId));

        if ("PAID".equals(inst.getStatus()))
            throw new IllegalStateException("This installment is already paid.");

        Payment payment = new Payment();
        payment.setMemberId(inst.getMemberId() != null ? inst.getMemberId() : 0);
        payment.setAmount(inst.getAmount().doubleValue());
        payment.setPaymentType("INSTALLMENT");
        payment.setDescription("Installment #" + inst.getInstallmentNo() + "/" + INSTALLMENT_COUNT);
        payment.setStatus("PAID");
        payment.setRecordedBy(0);                  // online self-payment
        payment.setPaymentMethod("ONLINE");
        payment.setInstallmentId(installmentId);
        paymentDAO.insert(payment);                // sets payment.paymentId via generated keys

        installmentDAO.markPaid(installmentId, payment.getPaymentId());

        // Auto-reactivate if this payment cleared all overdue debt.
        // Member doesn't need to do anything else — the next page they see
        // will be the unrestricted dashboard.
        reactivateIfPaidUp(inst.getMemberId() != null ? inst.getMemberId() : 0);
    }

    // Bulk transition: PENDING installments past their due_date become OVERDUE.
    // Called from manager dashboard load (sweep) and member dashboard load (cosmetic).
    public int markOverdueInstallments() {
        return installmentDAO.markOverdueGlobal();
    }

    // Threshold: how many overdue installments trigger an automatic PAYMENT_HOLD.
// 3 strikes is a common industry default — gives enough warning without dragging on.
    private static final int OVERDUE_THRESHOLD_FOR_HOLD = 3;

    // Called at login (after markOverdueInstallments) to enforce the auto-hold rule.
// If the member has accumulated OVERDUE_THRESHOLD_FOR_HOLD or more overdue
// installments AND is currently ACTIVE, transition them to PAYMENT_HOLD.
//
// Manager-applied SUSPENDED is left untouched — that's a different concept
// (disciplinary) and shouldn't be auto-reversed by this method.
//
// Returns true if the member was placed on hold, false otherwise.
    public boolean checkAndApplyPaymentHold(int memberId) {
        Optional<Member> opt = memberDAO.findById(memberId);
        if (opt.isEmpty()) return false;
        Member m = opt.get();

        // Only apply auto-hold to ACTIVE members. Don't touch PASSIVE, SUSPENDED, etc.
        if (!"ACTIVE".equals(m.getStatus())) return false;

        long overdueCount = installmentDAO.findByMemberId(memberId).stream()
                .filter(i -> "OVERDUE".equals(i.getStatus()))
                .count();

        if (overdueCount >= OVERDUE_THRESHOLD_FOR_HOLD) {
            memberDAO.updateStatus(memberId, "PAYMENT_HOLD");
            m.setStatus("PAYMENT_HOLD");
            return true;
        }
        return false;
    }

    // Called after a successful installment payment. If the member is currently
// on PAYMENT_HOLD AND has zero remaining OVERDUE installments, lift the hold
// and put them back to ACTIVE automatically.
//
// This is the "self-service recovery" path — member pays their dues, account
// reopens. No manager intervention required.
//
// Returns true if the member was reactivated, false otherwise.
    public boolean reactivateIfPaidUp(int memberId) {
        Optional<Member> opt = memberDAO.findById(memberId);
        if (opt.isEmpty()) return false;
        Member m = opt.get();

        if (!"PAYMENT_HOLD".equals(m.getStatus())) return false;

        // Only reactivate if there's an ACTIVE membership contract. Without one,
        // the member's "ACTIVE" status would be inconsistent with their membership
        // state — they'd be PAYMENT_HOLD with an expired contract, not an active one.
        Optional<Membership> activeMs = membershipDAO.findActiveByMemberId(memberId);
        if (activeMs.isEmpty()) return false;

        // Refresh OVERDUE flags before counting — a freshly-paid installment
        // should already be PAID, but sweeping is cheap insurance.
        markOverdueInstallments();

        long stillOverdue = installmentDAO.findByMemberId(memberId).stream()
                .filter(i -> "OVERDUE".equals(i.getStatus()))
                .count();

        if (stillOverdue == 0) {
            memberDAO.updateStatus(memberId, "ACTIVE");
            m.setStatus("ACTIVE");
            return true;
        }
        return false;
    }

    // Returns all installments for a member (for the Installments tab UI)
    public List<Installment> getInstallmentsForMember(int memberId) {
        return installmentDAO.findByMemberId(memberId);
    }


    // Returns installments scoped to ONE membership — used by the Installments
    // tab so stale rows from a previous (now PASSIVE) membership don't show up
    // after a renewal.
    public List<Installment> getInstallmentsForMembership(int membershipId) {
        return installmentDAO.findByMembershipId(membershipId);
    }

    // Returns only OVERDUE + PENDING-past-due installments for a member
    public List<Installment> getOverdueInstallments(int memberId) {
        return installmentDAO.findOverdueForMember(memberId);
    }

    // Returns true if the member has an active ANNUAL_INSTALLMENT membership.
    // The Installments tab in the member dashboard is hidden when this returns false.
    public boolean hasActiveInstallmentMembership(int memberId) {
        return membershipDAO.findActiveByMemberId(memberId)
                .map(ms -> "ANNUAL_INSTALLMENT".equals(ms.getPackageType()))
                .orElse(false);
    }

    // === Cancellation / deletion lifecycle ===

    // Member self-cancellation: stamps cancellation_requested_at but keeps the membership
    // ACTIVE until end_date. On next login after expiry, the existing AuthService logic
    // transitions the member to PASSIVE and stamps passive_since for the 1-year countdown.
    public void cancelMembership(int memberId) {
        Optional<Membership> ms = membershipDAO.findActiveByMemberId(memberId);
        if (ms.isEmpty()) throw new IllegalStateException("No active membership to cancel.");
        memberDAO.setCancellationRequested(memberId, LocalDateTime.now());
    }

    // Member self-deletes their account. FK ON DELETE SET NULL preserves audit records
    // (payments, appointments, event registrations) — those rows stay with member_id = NULL,
    // and UI helpers (getMemberById().orElse(...)) render "(deleted)" for the missing name.
    public void deleteAccountSelf(int memberId) {
        memberDAO.deleteById(memberId);
    }

    // Manager-initiated deletion. Restricted to SUSPENDED members — the manager must
    // suspend first (acts as a confirmation gate). Active members can't be deleted by
    // a manager click; this prevents accidental destruction.
    public void deleteAccountByManager(int memberId) {
        Member m = memberDAO.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Member not found: " + memberId));
        if (!"SUSPENDED".equals(m.getStatus()))
            throw new IllegalStateException(
                    "Only SUSPENDED members can be deleted. Suspend the member first.");
        memberDAO.deleteById(memberId);
    }

    // Sweep: deletes all PASSIVE members whose passive_since is older than 1 year.
    // Called from ManagerDashboard buildMembersPanel load (and AuthService loginMember
    // handles the per-account check on login). Returns count of deleted members.
    public int expireOldPassiveMembers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(PASSIVE_AUTO_DELETE_DAYS);
        List<Member> toDelete = memberDAO.findPassiveOlderThan(cutoff);
        for (Member m : toDelete) {
            memberDAO.deleteById(m.getMemberId());
        }
        return toDelete.size();
    }

    // === Membership lifecycle (existing) ===

    public void freezeMembership(int membershipId, int days) {
        Membership ms = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new IllegalStateException("Membership not found."));

        TierPolicy policy = TierPolicyRegistry.forTier(ms.getTier());
        if (ms.getFreezeCount() >= policy.maxFreezePerMonth())
            throw new IllegalStateException("Freeze limit reached for " + ms.getTier() + " tier.");

        if (ms.getEndDate().minusDays(3).isBefore(LocalDate.now()))
            throw new IllegalStateException("Cannot freeze within 3 days of expiry.");

        LocalDate today = LocalDate.now();
        LocalDate freezeEnd = today.plusDays(days);
        LocalDate newEnd = ms.getEndDate().plusDays(days);

        membershipDAO.updateEndDate(membershipId, newEnd);
        membershipDAO.updateStatus(membershipId, "FROZEN");
        membershipDAO.incrementFreezeCount(membershipId);
        // Persist the freeze window so partial-unfreeze can refund unused days.
        membershipDAO.insertFreezeLog(membershipId, today, freezeEnd);
        memberDAO.updateStatus(ms.getMemberId(), "FROZEN");
    }

    public void unfreezeMembership(int membershipId) {
        Membership ms = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new IllegalStateException("Membership not found."));
        membershipDAO.updateStatus(membershipId, "ACTIVE");
        memberDAO.updateStatus(ms.getMemberId(), "ACTIVE");
    }

    public void submitRegistrationRequest(RegistrationRequest req) {
        requestDAO.insertRegistration(req);
    }

    // === Query Methods ===

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

    public void suspendMember(int memberId) {
        memberDAO.updateStatus(memberId, "SUSPENDED");
    }

    public void unlockMember(int memberId) {
        memberDAO.updateLockStatus(memberId, false);
        memberDAO.updateFailedAttempts(memberId, 0);
    }

    public void updateMemberProfile(int memberId, Double weight, Double height,
                                    String ecName, String ecPhone) {
        memberDAO.updateProfile(memberId, weight, height, ecName, ecPhone);
        if (weight != null && height != null && height > 0) {
            double heightM = height / 100.0;
            double bmi = Math.round((weight / (heightM * heightM)) * 100.0) / 100.0;
            memberDAO.updateBmi(memberId, bmi, calcBmiCategory(bmi));
        }
    }

    // === Pricing & validation ===

    public double calculateAmount(String tier, String packageType) {
        double base = TierPolicyRegistry.forTier(tier).baseMonthlyPrice();
        return PackageStrategyRegistry.forPackage(packageType).calculatePrice(base);
    }

    private LocalDate calcEndDate(String packageType) {
        return PackageStrategyRegistry.forPackage(packageType)
                .calculateEndDate(LocalDate.now());
    }

    private String calcBmiCategory(double bmi) {
        if (bmi < 18.5) return "UNDERWEIGHT";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "OVERWEIGHT";
        return "OBESE";
    }

    public void validateMember(Member member) {
        if (member.getDateOfBirth() == null)
            throw new IllegalArgumentException("Date of birth is required.");
        if (member.getPhone() == null || member.getPhone().isBlank())
            throw new IllegalArgumentException("Phone number is required.");
        if (!member.getPhone().matches("\\d{10}"))
            throw new IllegalArgumentException("Phone must be exactly 10 digits.");
        if (member.getDateOfBirth().isAfter(LocalDate.now().minusYears(18)))
            throw new IllegalArgumentException("Minimum age is 18.");
        if (memberDAO.existsByPhone(member.getPhone()))
            throw new IllegalArgumentException("Phone number already registered.");
        if (member.getEmail() != null && memberDAO.existsByEmail(member.getEmail()))
            throw new IllegalArgumentException("Email address already registered.");
    }

    public void updatePhone(int memberId, String newPhone) {
        memberDAO.findById(memberId).ifPresent(m -> {
            if (!newPhone.equals(m.getPhone()) && memberDAO.existsByPhone(newPhone))
                throw new IllegalArgumentException("Phone number already registered.");
        });
        memberDAO.updatePhone(memberId, newPhone);
    }

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

    public double calculateBmi(double weight, double height) {
        double heightM = height / 100.0;
        return Math.round((weight / (heightM * heightM)) * 100.0) / 100.0;
    }

    public String getBmiCategory(double bmi) {
        if (bmi < 18.5) return "UNDERWEIGHT";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "OVERWEIGHT";
        return "OBESE";
    }

    public String[] getBmiSuggestions(String category) {
        return BmiAdviceRegistry.forCategory(category).suggestions();
    }

    public double calculateDailyCalories(double weight, double height,
                                         String gender, LocalDate dob) {
        int age = Period.between(dob, LocalDate.now()).getYears();
        double bmr = BmrStrategyRegistry.forGender(gender)
                .calculate(weight, height, age);
        return bmr * 1.55;   // sedentary activity multiplier
    }

    // === Internal helpers ===

    // Creates and inserts a new ACTIVE membership row, then re-fetches to populate
    // its DB-generated membership_id back onto the returned object.
    private Membership createMembership(int memberId, String tier, String packageType) {
        Membership ms = new Membership();
        ms.setMemberId(memberId);
        ms.setTier(tier);
        ms.setPackageType(packageType);
        ms.setStartDate(LocalDate.now());
        ms.setEndDate(calcEndDate(packageType));
        ms.setStatus("ACTIVE");
        ms.setFreezeCount(0);
        membershipDAO.insert(ms);
        // Re-fetch so callers can use the generated membership_id (e.g. for installment FK)
        return membershipDAO.findActiveByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException(
                        "Just-inserted membership not found for member " + memberId));
    }

    // Helper: build and insert a MEMBERSHIP-type payment record.
    // Returns the inserted Payment with its generated paymentId so callers (e.g. installment
    // schedule creation) can link to it.
    private Payment recordMembershipPayment(int memberId, String tier, String packageType,
                                            double amount, int managerId,
                                            String method, Integer installmentId) {
        Payment payment = new Payment();
        payment.setMemberId(memberId);
        payment.setAmount(amount);
        payment.setPaymentType("MEMBERSHIP");
        payment.setDescription(tier + " - " + packageType);
        payment.setStatus("PAID");
        payment.setRecordedBy(managerId);
        payment.setPaymentMethod(method);
        payment.setInstallmentId(installmentId);
        paymentDAO.insert(payment);
        return payment;
    }
    /**
     * Phase 2: Unfreeze a membership early and refund the unused frozen days.
     * Reads the latest freeze_log entry, computes how many days remain between
     * today and the planned freeze_end, and deducts those unused days from the
     * membership's end_date. Also flips the membership and member status back to ACTIVE.
     */
    public void unfreezeMembershipPartial(int membershipId) {
        Optional<Membership> opt = membershipDAO.findById(membershipId);
        if (opt.isEmpty()) throw new IllegalStateException("Membership not found: " + membershipId);
        Membership ms = opt.get();

        if (!"FROZEN".equals(ms.getStatus()))
            throw new IllegalStateException("Membership is not frozen.");

        Optional<FreezeLog> logOpt = membershipDAO.findLatestFreezeLog(membershipId);
        if (logOpt.isEmpty())
            throw new IllegalStateException("No freeze log found for this membership.");

        FreezeLog log = logOpt.get();
        java.time.LocalDate today = java.time.LocalDate.now();
        long unusedDays = java.time.temporal.ChronoUnit.DAYS.between(today, log.freezeEnd());
        if (unusedDays < 0) unusedDays = 0;

        java.time.LocalDate newEnd = ms.getEndDate().minusDays(unusedDays);

        membershipDAO.updateEndDate(membershipId, newEnd);
        membershipDAO.updateStatus(membershipId, "ACTIVE");

        memberDAO.updateStatus(ms.getMemberId(), "ACTIVE");
    }

    // ============================================================
    // PHASE 2: Strict chronological payment order + PASSIVE escalation
    // BR-76: Installments must be paid in chronological order (oldest first).
    // BR-77: 6+ OVERDUE installments escalate the member to PASSIVE.
    // ============================================================

    private static final int OVERDUE_THRESHOLD_FOR_PASSIVE = 6;

    /** Throws if any earlier installment is still PENDING/OVERDUE. */
    public void assertCanPayInstallment(int installmentId) {
        Installment target = installmentDAO.findById(installmentId)
                .orElseThrow(() -> new IllegalStateException("Installment not found: " + installmentId));

        for (Installment other : installmentDAO.findByMemberId(target.getMemberId())) {
            if (other.getInstallmentId() == target.getInstallmentId()) continue;
            if (other.getInstallmentNo() < target.getInstallmentNo()) {
                String st = other.getStatus();
                if ("PENDING".equals(st) || "OVERDUE".equals(st)) {
                    throw new IllegalStateException(
                            "You must pay installment #" + other.getInstallmentNo()
                                    + " before paying #" + target.getInstallmentNo() + ".");
                }
            }
        }
    }

    /** Earliest unpaid installment (smallest installment_no with PENDING/OVERDUE). */
    public Optional<Installment> findEarliestUnpaid(int memberId) {
        return installmentDAO.findByMemberId(memberId).stream()
                .filter(i -> "PENDING".equals(i.getStatus()) || "OVERDUE".equals(i.getStatus()))
                .min((a, b) -> Integer.compare(a.getInstallmentNo(), b.getInstallmentNo()));
    }

    /**
     * Payment with chronological check. PAYMENT_HOLD/ACTIVE transitions are
     * handled by reactivateIfPaidUp() inside payInstallment() itself.
     */
    public void payInstallmentInOrder(int installmentId) {
        assertCanPayInstallment(installmentId);
        payInstallment(installmentId);
    }

    // ============================================================
    // PROJECT 2 — REPORTS SWEEP
    // ============================================================

    /**
     * Sweeps all ACTIVE members whose current membership end date is in the past
     * and transitions them to PASSIVE (mirroring the AuthService login check).
     * Used by the Reports page so operational lists never show "stuck" ACTIVE
     * members with negative days-left.
     *
     * Returns the number of members transitioned.
     *
     * NOTE: This duplicates the AuthService "lazy expiration" logic deliberately.
     * Project 1 expired members only on next login; this sweep makes the same
     * decision proactively so Reports never displays stale data.
     */
    public int sweepExpiredActiveMembers() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (Member m : memberDAO.findByStatus("ACTIVE")) {
            Optional<Membership> ms = membershipDAO.findActiveByMemberId(m.getMemberId());
            if (ms.isEmpty()) continue;
            if (ms.get().getEndDate().isBefore(today)) {
                membershipDAO.updateStatus(ms.get().getMembershipId(), "PASSIVE");
                memberDAO.updateStatus(m.getMemberId(), "PASSIVE");
                memberDAO.setPassiveSince(m.getMemberId(), LocalDateTime.now());
                count++;
            }
        }
        return count;
    }

    // ============================================================
    // Membership eligibility — decision logic centralised here so
    // controllers stay thin and templates remain condition-free.
    // ============================================================

    /**
     * Pre-computed booleans driving the member's membership UI.
     * Pure data — no behaviour — so controllers can pass these straight
     * to the view layer without re-deriving them from raw status strings.
     */
    public record MembershipEligibility(
            boolean canUpgrade,
            boolean canFreeze,
            boolean canUnfreeze,
            boolean canCancel,
            boolean showRenew,
            boolean onPaymentHold,
            boolean cancellationPending
    ) {}

    /**
     * Computes which actions are currently available for the given member's
     * membership. The caller (controller) must supply a non-null Member and
     * a Membership instance — typically the active one, falling back to the
     * latest history entry when no active membership exists.
     */
    public MembershipEligibility computeMembershipEligibility(Member m, Membership ms) {
        if (m == null)  throw new IllegalArgumentException("Member is required.");
        if (ms == null) throw new IllegalArgumentException("Membership is required.");

        boolean isFrozen  = "FROZEN".equals(ms.getStatus());
        boolean isActive  = "ACTIVE".equals(ms.getStatus());
        boolean isPassive = "PASSIVE".equals(ms.getStatus());
        boolean onHold    = "PAYMENT_HOLD".equals(m.getStatus());
        boolean pending   = m.getCancellationRequestedAt() != null;

        return new MembershipEligibility(
                isActive && !"VIP".equals(ms.getTier()) && !onHold,  // canUpgrade
                isActive && !onHold,                                 // canFreeze
                isFrozen,                                            // canUnfreeze
                isActive && !onHold && !pending,                     // canCancel
                isPassive,                                           // showRenew
                onHold,                                              // onPaymentHold
                pending                                              // cancellationPending
        );
    }

    public void clearCancellationRequested(int memberId) {
        memberDAO.clearCancellationRequested(memberId);
    }

    public List<Membership> getAllMembershipsForMember(int memberId) {
        return membershipDAO.findAllByMemberId(memberId);
    }
}
