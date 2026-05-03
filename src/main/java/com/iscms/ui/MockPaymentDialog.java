package com.iscms.ui;

import com.iscms.service.MockPaymentProcessor;
import com.iscms.service.PaymentResult;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.time.YearMonth;

// Modal dialog that collects card details and delegates the actual payment
// simulation to MockPaymentProcessor. The dialog handles ONLY:
//   - Form rendering and user interaction
//   - Format-level validation (length, character class, parseable date)
//   - Surfacing format errors inline so the user can correct and retry
//
// Domain-level outcomes (success, declines, expiry decisions) are the
// processor's responsibility — this class doesn't decide payment results.
public class MockPaymentDialog extends JDialog {

    private final MockPaymentProcessor processor = new MockPaymentProcessor();

    // Default to CANCELLED so closing the window via the X button registers as cancel
    private PaymentResult result = PaymentResult.CANCELLED;

    private final JTextField     txtCardNumber = new JTextField(19);
    private final JTextField     txtCardName   = new JTextField(20);
    private final JTextField     txtExpiry     = new JTextField(5);
    private final JPasswordField txtCvv        = new JPasswordField(3);

    private final double amount;
    private final String purpose;

    private MockPaymentDialog(Window owner, double amount, String purpose) {
        super(owner, "Online Payment", ModalityType.APPLICATION_MODAL);
        this.amount = amount;
        this.purpose = purpose;

        setSize(440, 420);
        setLocationRelativeTo(owner);
        setResizable(false);

        applyInputMasks();
        initUI();
    }

    // Restrict input length and character classes at the document level
    // so the user can't type past the field's intended length.
    private void applyInputMasks() {
        txtCardNumber.setDocument(new DigitDocument(19));   // 16 digits + 3 spaces
        txtCvv.setDocument(new DigitDocument(3));
        txtExpiry.setDocument(new ExpiryDocument(5));       // MM/YY
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel header = new JPanel(new BorderLayout());
        JLabel lblTitle = new JLabel("💳 " + purpose);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        lblTitle.setForeground(new Color(33, 87, 141));

        JLabel lblAmount = new JLabel(String.format("Amount: %.2f TL", amount));
        lblAmount.setFont(new Font("Arial", Font.BOLD, 13));
        lblAmount.setForeground(new Color(33, 120, 80));

        header.add(lblTitle, BorderLayout.NORTH);
        header.add(lblAmount, BorderLayout.CENTER);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Card Details"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0.35;
        form.add(new JLabel("Card Number:"), c);
        c.gridx = 1; c.weightx = 0.65;
        form.add(txtCardNumber, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Name on Card:"), c);
        c.gridx = 1;
        form.add(txtCardName, c);

        JPanel rowExpCvv = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel expPanel  = new JPanel(new BorderLayout(4, 0));
        expPanel.add(new JLabel("Expiry (MM/YY):"), BorderLayout.WEST);
        expPanel.add(txtExpiry, BorderLayout.CENTER);

        JPanel cvvPanel = new JPanel(new BorderLayout(4, 0));
        cvvPanel.add(new JLabel("CVV:"), BorderLayout.WEST);
        cvvPanel.add(txtCvv, BorderLayout.CENTER);

        rowExpCvv.add(expPanel);
        rowExpCvv.add(cvvPanel);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        form.add(rowExpCvv, c);

        JLabel lblHint = new JLabel(
                "<html><i>Demo: 4242…4242 = success, 4000…0002 = insufficient funds, "
                        + "4000…0069 = expired, 4000…0127 = bad CVV.</i></html>");
        lblHint.setFont(new Font("Arial", Font.PLAIN, 10));
        lblHint.setForeground(new Color(120, 120, 120));
        c.gridy = 3;
        form.add(lblHint, c);

        JButton btnPay    = new JButton("Pay Now");
        JButton btnCancel = new JButton("Cancel");

        btnPay.setBackground(new Color(33, 120, 80));
        btnPay.setForeground(Color.WHITE);
        btnPay.setOpaque(true);
        btnPay.setBorderPainted(false);

        btnPay.addActionListener(e -> handlePayClick());
        btnCancel.addActionListener(e -> {
            result = PaymentResult.CANCELLED;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(btnCancel);
        buttons.add(btnPay);

        root.add(header, BorderLayout.NORTH);
        root.add(form,   BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        add(root);
    }

    // Handles a Pay Now click in two phases:
    //   Phase 1 — format validation: empty fields, wrong length, malformed expiry.
    //             On failure, surface a warning dialog and stay open so the user
    //             can correct the field and retry without losing other entries.
    //   Phase 2 — delegate to the processor for the simulated outcome.
    //             Result is captured and dialog disposes — caller reads via
    //             showAndProcess.
    private void handlePayClick() {
        String cardDigits = txtCardNumber.getText().replaceAll("\\s+", "");
        String cvv        = new String(txtCvv.getPassword());
        String expiry     = txtExpiry.getText().trim();
        String name       = txtCardName.getText().trim();

        // === Phase 1: Format validation ===

        if (cardDigits.isEmpty() || cvv.isEmpty() || expiry.isEmpty() || name.isEmpty()) {
            warn("Please fill in all card fields.");
            return;
        }

        if (cardDigits.length() != 16) {
            warn("Card number must be 16 digits. Please check and re-enter.");
            txtCardNumber.requestFocus();
            return;
        }

        if (cvv.length() != 3) {
            warn("CVV must be exactly 3 digits.");
            txtCvv.requestFocus();
            return;
        }

        YearMonth expiryMonth = parseExpiry(expiry);
        if (expiryMonth == null) {
            warn("Invalid expiry date. Please use MM/YY format with a valid month (01–12).");
            txtExpiry.requestFocus();
            return;
        }

        // === Phase 2: Delegate to processor ===

        result = processor.process(cardDigits, expiryMonth);
        dispose();
    }

    // Parses MM/YY input into YearMonth. Returns null if the input shape
    // is wrong, the month is out of range, or the digits aren't numeric.
    // Year is interpreted as 20YY — fine for this coursework demo.
    private YearMonth parseExpiry(String input) {
        if (input == null || input.length() != 5 || input.charAt(2) != '/') return null;
        try {
            int month = Integer.parseInt(input.substring(0, 2));
            int year  = Integer.parseInt(input.substring(3));
            if (month < 1 || month > 12) return null;
            return YearMonth.of(2000 + year, month);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // Helper: shows a non-blocking warning dialog without closing the payment dialog
    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Invalid Input", JOptionPane.WARNING_MESSAGE);
    }

    // Static helper: shows dialog, blocks until closed, returns result.
    public static PaymentResult showAndProcess(Window owner, double amount, String purpose) {
        MockPaymentDialog dlg = new MockPaymentDialog(owner, amount, purpose);
        dlg.setVisible(true);
        return dlg.result;
    }

    // Convenience: lets callers describe a result without importing the processor.
    // Delegates to MockPaymentProcessor — a single source of truth for messages.
    public static String describe(PaymentResult result) {
        return MockPaymentProcessor.describe(result);
    }

    // --- Input restriction documents ---

    // Allows only digits up to a max length (used for CVV and card number).
    private static class DigitDocument extends PlainDocument {
        private final int maxLength;

        DigitDocument(int maxLength) { this.maxLength = maxLength; }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            StringBuilder filtered = new StringBuilder();
            for (char ch : str.toCharArray()) {
                if (Character.isDigit(ch)) filtered.append(ch);
            }
            if (filtered.length() == 0) return;
            if (getLength() + filtered.length() > maxLength) return;
            super.insertString(offs, filtered.toString(), a);
        }
    }

    // Allows MM/YY entry: digits and a single slash, max 5 chars total.
    // The user types the slash themselves (e.g. "12/28") — auto-insert
    // logic was removed because it produced subtle off-by-one bugs when
    // the user typed digits one at a time.
    private static class ExpiryDocument extends PlainDocument {
        private final int maxLength;

        ExpiryDocument(int maxLength) { this.maxLength = maxLength; }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            StringBuilder filtered = new StringBuilder();
            for (char ch : str.toCharArray()) {
                if (Character.isDigit(ch) || ch == '/') filtered.append(ch);
            }
            if (filtered.length() == 0) return;
            if (getLength() + filtered.length() > maxLength) return;
            super.insertString(offs, filtered.toString(), a);
        }
    }
}