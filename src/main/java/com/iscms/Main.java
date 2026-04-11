package com.iscms;

import com.iscms.service.EventService;
import com.iscms.service.MemberService;
import com.iscms.ui.LoginFrame;

import javax.swing.*;

// Application entry point
public class Main {

    public static void main(String[] args) {

        // Step 1: Expire past events before UI loads
        // Sets ACTIVE events whose date has passed to EXPIRED status
        new EventService().expirePastEvents();

        // Step 2: Expire outdated registration and tier upgrade requests
        // Marks PENDING requests past their 3-day expiry as EXPIRED
        // Also deletes INITIAL registration requests and their member records if expired
        new MemberService().expireOldRequests();

        // Step 3: Launch the login screen on the Swing Event Dispatch Thread (EDT)
        // invokeLater ensures UI creation happens on the correct thread (Swing thread safety)
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}