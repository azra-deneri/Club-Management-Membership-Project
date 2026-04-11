package com.iscms.ui;

import com.iscms.model.Member;
import com.iscms.service.MemberFactory;
import com.iscms.service.MemberService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

// Self-registration screen for new members
// Split into two panels: registration form (left) + tier benefits comparison (right)
public class RegisterFrame extends JFrame {

    // Service instance — no DAOs directly in UI layer
    private final MemberService memberService = new MemberService();

    // Form input fields
    private JTextField txtName, txtDob, txtPhone, txtEmail;
    private JTextField txtWeight, txtHeight, txtEcName, txtEcPhone;
    private JComboBox<String> cbGender, cbTier, cbPackage;
    private JPasswordField txtPass, txtPassConfirm;
    private JLabel lblAmount;

    public RegisterFrame() {
        setTitle("ISC-MS — Register");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 680);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
    }

    // Builds the main layout: form on the left, tier benefits panel on the right
    private void initUI() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(480);
        splitPane.setEnabled(false); // Prevent user from resizing the split
        splitPane.setLeftComponent(buildFormPanel());
        splitPane.setRightComponent(new TierBenefitsPanel());
        add(splitPane);
    }

    // Builds the scrollable registration form panel
    private JScrollPane buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Form input fields
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
        cbPackage      = new JComboBox<>(new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"});

        // Amount label — updated dynamically as tier/package changes
        lblAmount = new JLabel("Amount: 750.00 TL /month");
        lblAmount.setFont(new Font("Arial", Font.BOLD, 13));
        lblAmount.setForeground(new Color(33, 87, 141));

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

        // Submit button
        JButton btnRegister = new JButton("Submit Registration Request");
        btnRegister.setBackground(new Color(33, 87, 141));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setOpaque(true);
        btnRegister.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnRegister, c);

        // Back to login link button
        JButton btnBack = new JButton("Back to Login");
        btnBack.setBorderPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setForeground(Color.BLUE);
        c.gridy = inputs.length + 1;
        panel.add(btnBack, c);

        // Update amount label whenever tier or package selection changes
        cbTier.addActionListener(e    -> updateAmount());
        cbPackage.addActionListener(e -> updateAmount());
        btnRegister.addActionListener(e -> handleRegister());
        btnBack.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });

        return new JScrollPane(panel);
    }

    // Recalculates and updates the estimated amount label
    // Delegates price calculation to MemberService
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

    // Handles the registration form submission
    // Validates input, builds member via MemberFactory, submits registration request
    private void handleRegister() {
        String name    = txtName.getText().trim();
        String dob     = txtDob.getText().trim();
        String phone   = txtPhone.getText().trim();
        String email   = txtEmail.getText().trim();
        String pass    = new String(txtPass.getPassword());
        String confirm = new String(txtPassConfirm.getPassword());

        // Validation step 1: required fields must not be empty
        if (name.isEmpty() || dob.isEmpty() || phone.isEmpty()
                || email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all required fields (*).",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation step 2: password must be at least 8 characters
        if (pass.length() < 8) {
            JOptionPane.showMessageDialog(this,
                    "Password must be at least 8 characters.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation step 3: both password fields must match
        if (!pass.equals(confirm)) {
            JOptionPane.showMessageDialog(this,
                    "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validation step 4: phone must be exactly 10 digits
        if (phone.length() != 10 || !phone.matches("\\d+")) {
            JOptionPane.showMessageDialog(this,
                    "Phone must be exactly 10 digits.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Build member object via MemberFactory — status set to PENDING automatically
            // MemberBuilder validation (age >= 18, phone not blank) runs inside build()
            Member m = MemberFactory.createPendingMember(
                    name, LocalDate.parse(dob),
                    (String) cbGender.getSelectedItem(),
                    phone, email, pass);

            // Set optional fields after factory creation
            if (!txtWeight.getText().isBlank())
                m.setWeight(Double.parseDouble(txtWeight.getText().trim()));
            if (!txtHeight.getText().isBlank())
                m.setHeight(Double.parseDouble(txtHeight.getText().trim()));
            m.setEmergencyContactName(txtEcName.getText().trim());
            m.setEmergencyContactPhone(txtEcPhone.getText().trim());

            String tier = (String) cbTier.getSelectedItem();
            String pkg  = (String) cbPackage.getSelectedItem();

            // Submit to service — inserts member (PENDING) and registration request
            // Duplicate phone/email and age validation enforced in MemberService
            memberService.createRegistrationRequest(m, tier, pkg);

            JOptionPane.showMessageDialog(this,
                    "Registration request submitted!\n\n" +
                            "Please pay " + lblAmount.getText().replace("Amount: ", "") + " to the club.\n" +
                            "Manager will approve within 3 days.\n\n" +
                            "If payment is not received within 3 days, your request will expire.",
                    "Request Submitted", JOptionPane.INFORMATION_MESSAGE);

            // Return to login screen after successful submission
            dispose();
            new LoginFrame().setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}