package com.example.desktop;

import com.example.desktop.ui.LoginPanel;
import com.example.desktop.ui.MainPanel;
import com.example.desktop.utils.Config;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Main BankBuddy Desktop Application.
 *
 * Run this file to launch the GUI!
 */
public class BankBuddyApp extends JFrame {

    private static final String CARD_LOGIN = "login";
    private static final String CARD_MAIN = "main";

    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    private LoginPanel loginPanel;
    private MainPanel mainPanel;

    public BankBuddyApp() {
        setTitle("Guaide-ya - AI Financial Companion");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Use CardLayout to switch between screens
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // Create panels
        loginPanel = new LoginPanel(this);
        mainPanel = new MainPanel(this);

        // Add panels to card layout
        mainContainer.add(loginPanel, CARD_LOGIN);
        mainContainer.add(mainPanel, CARD_MAIN);

        // Add to frame
        add(mainContainer);

        // Show login screen first
        showLogin();
    }

    public void showLogin() {
        cardLayout.show(mainContainer, CARD_LOGIN);
        loginPanel.reset();
    }

    public void showMain(String userEmail) {
        mainPanel.setUserEmail(userEmail);
        cardLayout.show(mainContainer, CARD_MAIN);
    }

    public static void main(String[] args) {
        // Set modern look and feel
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("Failed to set look and feel: " + e.getMessage());
        }

        // Print configuration info
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║        Guaide-ya Desktop Client         ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        System.out.println("API Endpoint: " + Config.API_BASE_URL);
        System.out.println();
        System.out.println("To change the API endpoint, edit:");
        System.out.println("  src/main/java/com/example/desktop/utils/Config.java");
        System.out.println();

        // Launch GUI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            BankBuddyApp app = new BankBuddyApp();
            app.setVisible(true);
        });
    }
}
