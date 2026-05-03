package com.iscms.ui;

import com.iscms.model.Member;
import com.iscms.service.MemberFactory;
import com.iscms.service.MemberService;
import com.iscms.service.PaymentResult;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

// Self-registration screen for new members.
// Two payment paths:
//   CASH   → submits a PENDING registration request awaiting manager approval.
//            Package limited to MONTHLY / ANNUAL_PREPAID — installment requires online.
//   ONLINE → opens MockPaymentDialog. On SUCCESS, member is created ACTIVE
//            immediately (no manager approval). All three packages allowed.
public class RegisterFrame extends JFrame {

    private final MemberService memberService = new MemberService();

    // Form input fields
    private JTextField txtName, txtDob, txtPhone, txtEmail;
    private JTextField txtWeight, txtHeight, txtEcName, txtEcPhone;
    private JComboBox<String> cbGender, cbTier, cbPackage;
    private JPasswordField txtPass, txtPassConfirm;
    private JLabel lblAmount;

    // Payment method radios — drive the package list and the submit logic
    private JRadioButton rbCash, rbOnline;

    public RegisterFrame() {
        setTitle("ISC-MS — Register");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 720);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
    }

    private void initUI() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setEnabled(false);
        splitPane.setLeftComponent(buildFormPanel());
        splitPane.setRightComponent(new TierBenefitsPanel());
        add(splitPane);
    }

    private JScrollPane buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Form fields
        txtName        = new JTextField(20);
        txtDob         = new JTextField("YYYY-MM-DD");
        cbGender       = new JComboBox<>(new String[]{"MALE", "FEMALE", "OTHER"});
        txtPhone       = new JTextField(20);
        txtEmail       = new JTextField(20);
        txtPass        = new JPasswordField(20);
        txtPassConfirm = new JPasswordField(20);
        txtWeight      = new JTextField(20);
        txtHeight      = new JTextField(20);
        txtEcName      = new JTextField(20);
        txtEcPhone     = new JTextField(20);
        cbTier         = new JComboBox<>(new String[]{"CLASSIC", "GOLD", "VIP"});

        // Package combo populated dynamically based on payment method selection
        cbPackage = new JComboBox<>();

        lblAmount = new JLabel("Amount: 750.00 TL /month");
        lblAmount.setFont(new Font("Arial", Font.BOLD, 13));
        lblAmount.setForeground(new Color(33, 87, 141));

        // Payment method panel (Cash / Online radios)
        rbCash   = new JRadioButton("Cash (pay at the club)", true);
        rbOnline = new JRadioButton("Online (pay by card now)");
        ButtonGroup bgPay = new ButtonGroup();
        bgPay.add(rbCash); bgPay.add(rbOnline);

        JPanel payPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        payPanel.setBorder(BorderFactory.createTitledBorder("Payment Method"));
        payPanel.add(rbCash);
        payPanel.add(rbOnline);

        String[] labels = {
                "Full Name *", "Date of Birth * (YYYY-MM-DD)", "Gender *",
                "Phone * (10 digits)", "Email *", "Password *", "Confirm Password *",
                "Weight (kg)", "Height (cm)",
                "Emergency Contact Name", "Emergency Contact Phone",
                "Tier *", "Package *", "Estimated Amount"
        };
        Component[] inputs = {
                txtName, txtDob, cbGender, txtPhone, txtEmail,
                txtPass, txtPassConfirm, txtWeight, txtHeight,
                txtEcName, txtEcPhone, cbTier, cbPackage, lblAmount
        };

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.35;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.65;
            panel.add(inputs[i], c);
        }

        // Payment method panel sits below the estimated amount row
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(payPanel, c);

        // Submit button
        JButton btnRegister = new JButton("Submit Registration");
        btnRegister.setBackground(new Color(33, 87, 141));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setOpaque(true);
        btnRegister.setBorderPainted(false);
        c.gridy = inputs.length + 1;
        panel.add(btnRegister, c);

        // Back to login link
        JButton btnBack = new JButton("Back to Login");
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setForeground(Color.BLUE);
        c.gridy = inputs.length + 2;
        panel.add(btnBack, c);

        // Wire dynamic behaviour
        cbTier.addActionListener(e    -> updateAmount());
        cbPackage.addActionListener(e -> updateAmount());

        // Repopulate package list whenever payment method changes
        rbCash.addActionListener(e   -> refreshPackageList());
        rbOnline.addActionListener(e -> refreshPackageList());

        btnRegister.addActionListener(e -> handleRegister());
        btnBack.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });

        // Initial population — defaults to Cash, so installment is hidden
        refreshPackageList();

        return new JScrollPane(panel);
    }

    // Updates the package combo based on the selected payment method.
    // Cash: MONTHLY + ANNUAL_PREPAID only. Online: all three packages.
    // Preserves the current selection when possible to avoid surprising the user.
    private void refreshPackageList() {
        String previousSelection = (String) cbPackage.getSelectedItem();
        String[] packages = rbOnline.isSelected()
                ? new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"}
                : new String[]{"MONTHLY", "ANNUAL_PREPAID"};

        cbPackage.removeAllItems();
        for (String pkg : packages) cbPackage.addItem(pkg);

        // Try to restore the previous choice; falls back to first item if not in new list
        if (previousSelection != null) {
            for (int i = 0; i < cbPackage.getItemCount(); i++) {
                if (previousSelection.equals(cbPackage.getItemAt(i))) {
                    cbPackage.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateAmount();
    }

    private void updateAmount() {
        String tier = cbTier.getSelectedItem() != null
                ? cbTier.getSelectedItem().toString() : "CLASSIC";
        String pkg  = cbPackage.getSelectedItem() != null
                ? cbPackage.getSelectedItem().toString() : "MONTHLY";
        double amount = memberService.calculateAmount(tier, pkg);
        String note = switch (pkg) {
            case "ANNUAL_PREPAID"     -> " (15% discount, total)";
            case "ANNUAL_INSTALLMENT" -> " /month (+7%)";
            default                   -> " /month";
        };
        lblAmount.setText(String.format("Amount: %.2f TL%s", amount, note));
    }

    // Validates the form, then dispatches to either the cash flow (manager approval)
    // or the online flow (mock payment → immediate activation).
    private void handleRegister() {
        String name    = txtName.getText().trim();
        String dob     = txtDob.getText().trim();
        String phone   = txtPhone.getText().trim();
        String email   = txtEmail.getText().trim();
        String pass    = new String(txtPass.getPassword());
        String confirm = new String(txtPassConfirm.getPassword());

        // Required field check
        if (name.isEmpty() || dob.isEmpty() || phone.isEmpty()
                || email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all required fields (*).",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (pass.length() < 8) {
            JOptionPane.showMessageDialog(this,
                    "Password must be at least 8 characters.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!pass.equals(confirm)) {
            JOptionPane.showMessageDialog(this,
                    "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (phone.length() != 10 || !phone.matches("\\d+")) {
            JOptionPane.showMessageDialog(this,
                    "Phone must be exactly 10 digits.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String tier = (String) cbTier.getSelectedItem();
        String pkg  = (String) cbPackage.getSelectedItem();

        // Build the member object once — reused by both flows
        Member m;
        try {
            m = MemberFactory.createPendingMember(
                    name, LocalDate.parse(dob),
                    (String) cbGender.getSelectedItem(),
                    phone, email, pass);

            if (!txtWeight.getText().isBlank())
                m.setWeight(Double.parseDouble(txtWeight.getText().trim()));
            if (!txtHeight.getText().isBlank())
                m.setHeight(Double.parseDouble(txtHeight.getText().trim()));
            m.setEmergencyContactName(txtEcName.getText().trim());
            m.setEmergencyContactPhone(txtEcPhone.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Dispatch by payment method
        if (rbOnline.isSelected()) {
            handleOnlineRegistration(m, tier, pkg);
        } else {
            handleCashRegistration(m, tier, pkg);
        }
    }

    // Cash flow: insert PENDING request → manager approval queue
    private void handleCashRegistration(Member m, String tier, String pkg) {
        try {
            memberService.createRegistrationRequest(m, tier, pkg);
            JOptionPane.showMessageDialog(this,
                    "Registration request submitted!\n\n" +
                            "Please pay " + lblAmount.getText().replace("Amount: ", "")
                            + " in cash at the club.\n" +
                            "Manager will approve within 3 days.\n\n" +
                            "If payment is not received within 3 days, your request will expire.",
                    "Request Submitted", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            new LoginFrame().setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Online flow: open mock payment, then on SUCCESS create ACTIVE member directly
    private void handleOnlineRegistration(Member m, String tier, String pkg) {
        double amount = memberService.calculateAmount(tier, pkg);
        String purpose = "Membership: " + tier + " - " + pkg;

        PaymentResult result = MockPaymentDialog.showAndProcess(this, amount, purpose);

        if (result == PaymentResult.CANCELLED) {
            // User backed out — nothing to do, leave the form intact
            return;
        }

        if (!result.isSuccess()) {
            // Show specific failure reason and let the user retry
            JOptionPane.showMessageDialog(this,
                    MockPaymentDialog.describe(result) + "\n\nNo account has been created. Please try again.",
                    "Payment Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Payment succeeded — create the member as ACTIVE
        try {
            memberService.selfRegisterMember(m, tier, pkg);
            JOptionPane.showMessageDialog(this,
                    "Welcome to Istanbul Sports Club!\n\n" +
                            "Your payment was processed successfully and your membership is now active.\n" +
                            "You can log in immediately with your phone number and password.",
                    "Registration Complete", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            new LoginFrame().setVisible(true);
        } catch (Exception ex) {
            // Payment went through but DB insert failed — surface the error so the
            // user knows to contact the club (their card was charged in real life)
            JOptionPane.showMessageDialog(this,
                    "Payment was processed but registration failed:\n" + ex.getMessage()
                            + "\n\nPlease contact the club for assistance.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}