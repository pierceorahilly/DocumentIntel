package com.example.desktop.ui;

import com.example.desktop.BankBuddyApp;
import com.example.desktop.api.ApiClient;
import com.example.desktop.utils.Config;
import com.example.desktop.utils.TokenManager;

import javax.swing.*;
import java.awt.*;

/**
 * Login/Signup panel with email confirmation.
 */
public class LoginPanel extends JPanel {

    private final BankBuddyApp app;

    private JTextField emailField;
    private JPasswordField passwordField;
    private JTextField nameField;
    private JTextField dobField;
    private JTextField addressField;
    private JTextField confirmCodeField;

    private JButton loginButton;
    private JButton signupButton;
    private JButton confirmEmailButton;
    private JButton toggleModeButton;

    private JPanel signupFieldsPanel;
    private JPanel confirmEmailPanel;
    private JLabel statusLabel;
    private JLabel demoLabel;

    private boolean isSignupMode = false;
    private boolean isConfirmMode = false;
    private String pendingEmail = null;

    public LoginPanel(BankBuddyApp app) {
        this.app = app;
        setLayout(new GridBagLayout());
        setBackground(new Color(245, 245, 245));
        initComponents();
    }

    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 20, 10, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel titleLabel = new JLabel("Guaide-ya", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titleLabel.setForeground(new Color(46, 232, 137));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(titleLabel, gbc);

        // Subtitle
        JLabel subtitleLabel = new JLabel("Your AI Financial Companion", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(Color.GRAY);
        gbc.gridy = 1;
        add(subtitleLabel, gbc);

        // Demo Mode Label
        if (Config.DEMO_MODE) {
            demoLabel = new JLabel("⚠️ DEMO MODE - No server connection", SwingConstants.CENTER);
            demoLabel.setFont(new Font("Arial", Font.BOLD, 14));
            demoLabel.setForeground(new Color(255, 140, 0));
            gbc.gridy = 2;
            add(demoLabel, gbc);
        }

        gbc.gridwidth = 1;
        gbc.gridy = 3;

        // Email Label
        JLabel emailLabel = new JLabel("Email:");
        gbc.gridx = 0;
        add(emailLabel, gbc);

        // Email Field
        emailField = new JTextField(20);
        emailField.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 1;
        add(emailField, gbc);

        // Password Label
        gbc.gridy = 4;
        gbc.gridx = 0;
        JLabel passwordLabel = new JLabel("Password:");
        add(passwordLabel, gbc);

        // Password Field
        passwordField = new JPasswordField(20);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 1;
        add(passwordField, gbc);

        // Signup Fields Panel (hidden by default)
        signupFieldsPanel = new JPanel(new GridBagLayout());
        signupFieldsPanel.setBackground(new Color(245, 245, 245));
        signupFieldsPanel.setVisible(false);

        GridBagConstraints signupGbc = new GridBagConstraints();
        signupGbc.insets = new Insets(10, 20, 10, 20);
        signupGbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nameLabel = new JLabel("Full Name:");
        signupGbc.gridx = 0;
        signupGbc.gridy = 0;
        signupFieldsPanel.add(nameLabel, signupGbc);

        nameField = new JTextField(20);
        nameField.setFont(new Font("Arial", Font.PLAIN, 14));
        signupGbc.gridx = 1;
        signupFieldsPanel.add(nameField, signupGbc);

        JLabel dobLabel = new JLabel("Date of Birth:");
        signupGbc.gridx = 0;
        signupGbc.gridy = 1;
        signupFieldsPanel.add(dobLabel, signupGbc);

        dobField = new JTextField(20);
        dobField.setFont(new Font("Arial", Font.PLAIN, 14));
        dobField.setToolTipText("Format: DD/MM/YYYY (e.g., 15/01/1990)");
        signupGbc.gridx = 1;
        signupFieldsPanel.add(dobField, signupGbc);

        JLabel addressLabel = new JLabel("Address:");
        signupGbc.gridx = 0;
        signupGbc.gridy = 2;
        signupFieldsPanel.add(addressLabel, signupGbc);

        addressField = new JTextField(20);
        addressField.setFont(new Font("Arial", Font.PLAIN, 14));
        signupGbc.gridx = 1;
        signupFieldsPanel.add(addressField, signupGbc);

        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(signupFieldsPanel, gbc);

        // Confirm Email Panel (hidden by default)
        confirmEmailPanel = new JPanel(new GridBagLayout());
        confirmEmailPanel.setBackground(new Color(245, 245, 245));
        confirmEmailPanel.setVisible(false);

        GridBagConstraints confirmGbc = new GridBagConstraints();
        confirmGbc.insets = new Insets(10, 20, 10, 20);
        confirmGbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel confirmLabel = new JLabel("Confirmation Code:");
        confirmGbc.gridx = 0;
        confirmGbc.gridy = 0;
        confirmEmailPanel.add(confirmLabel, confirmGbc);

        confirmCodeField = new JTextField(10);
        confirmCodeField.setFont(new Font("Arial", Font.PLAIN, 14));
        confirmGbc.gridx = 1;
        confirmEmailPanel.add(confirmCodeField, confirmGbc);

        JLabel confirmHintLabel = new JLabel("(6-digit code sent to your email)");
        confirmHintLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        confirmHintLabel.setForeground(Color.GRAY);
        confirmGbc.gridx = 0;
        confirmGbc.gridy = 1;
        confirmGbc.gridwidth = 2;
        confirmEmailPanel.add(confirmHintLabel, confirmGbc);

        gbc.gridy = 6;
        add(confirmEmailPanel, gbc);

        // Login Button
        loginButton = new JButton("Login");
        loginButton.setFont(new Font("Arial", Font.BOLD, 16));
        loginButton.setBackground(new Color(46, 232, 137));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setPreferredSize(new Dimension(200, 45));
        loginButton.addActionListener(e -> handleLogin());
        gbc.gridy = 7;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(loginButton, gbc);

        // Signup Button
        signupButton = new JButton("Sign Up");
        signupButton.setFont(new Font("Arial", Font.BOLD, 16));
        signupButton.setBackground(new Color(46, 232, 137));
        signupButton.setForeground(Color.WHITE);
        signupButton.setFocusPainted(false);
        signupButton.setPreferredSize(new Dimension(200, 45));
        signupButton.setVisible(false);
        signupButton.addActionListener(e -> handleSignup());
        gbc.gridy = 8;
        add(signupButton, gbc);

        // Confirm Email Button
        confirmEmailButton = new JButton("Confirm Email");
        confirmEmailButton.setFont(new Font("Arial", Font.BOLD, 16));
        confirmEmailButton.setBackground(new Color(46, 232, 137));
        confirmEmailButton.setForeground(Color.WHITE);
        confirmEmailButton.setFocusPainted(false);
        confirmEmailButton.setPreferredSize(new Dimension(200, 45));
        confirmEmailButton.setVisible(false);
        confirmEmailButton.addActionListener(e -> handleConfirmEmail());
        gbc.gridy = 9;
        add(confirmEmailButton, gbc);

        // Status Label
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        gbc.gridy = 10;
        add(statusLabel, gbc);

        // Toggle Mode Button
        toggleModeButton = new JButton("Don't have an account? Sign up");
        toggleModeButton.setFont(new Font("Arial", Font.PLAIN, 12));
        toggleModeButton.setForeground(new Color(46, 232, 137));
        toggleModeButton.setBorderPainted(false);
        toggleModeButton.setContentAreaFilled(false);
        toggleModeButton.setFocusPainted(false);
        toggleModeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleModeButton.addActionListener(e -> toggleMode());
        gbc.gridy = 11;
        add(toggleModeButton, gbc);
    }

    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter email and password");
            statusLabel.setForeground(Color.RED);
            return;
        }

        if (Config.DEMO_MODE) {
            // Demo mode: simulate successful login
            statusLabel.setText("✓ Login successful (DEMO MODE)");
            statusLabel.setForeground(new Color(0, 150, 0));
            TokenManager.saveUserEmail(email);
            SwingUtilities.invokeLater(() -> app.showMain(email));
            return;
        }

        // Real mode: call API
        setLoading(true);
        statusLabel.setText("Logging in...");
        statusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                ApiClient.LoginResponse response = ApiClient.login(email, password);
                TokenManager.saveTokens(response.idToken, response.accessToken, response.refreshToken);
                TokenManager.saveUserEmail(email);

                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("✓ Login successful!");
                    statusLabel.setForeground(new Color(0, 150, 0));
                    app.showMain(email);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("Login failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private void handleSignup() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        String name = nameField.getText().trim();
        String dob = dobField.getText().trim();
        String address = addressField.getText().trim();

        if (email.isEmpty() || password.isEmpty() || name.isEmpty() || dob.isEmpty() || address.isEmpty()) {
            statusLabel.setText("Please fill all fields");
            statusLabel.setForeground(Color.RED);
            return;
        }

        if (password.length() < 8) {
            statusLabel.setText("Password must be at least 8 characters");
            statusLabel.setForeground(Color.RED);
            return;
        }

        // Validate and convert date format from DD/MM/YYYY to YYYY-MM-DD
        String dobISO;
        if (!dob.matches("\\d{2}/\\d{2}/\\d{4}")) {
            statusLabel.setText("Date of birth must be in format DD/MM/YYYY (e.g., 15/01/1990)");
            statusLabel.setForeground(Color.RED);
            return;
        }

        // Convert DD/MM/YYYY to YYYY-MM-DD for AWS
        String[] dobParts = dob.split("/");
        dobISO = dobParts[2] + "-" + dobParts[1] + "-" + dobParts[0];

        if (Config.DEMO_MODE) {
            // Demo mode: simulate signup
            statusLabel.setText("✓ Account created! (DEMO MODE)");
            statusLabel.setForeground(new Color(0, 150, 0));
            showConfirmEmailMode(email);
            return;
        }

        setLoading(true);
        statusLabel.setText("Creating account...");
        statusLabel.setForeground(Color.BLUE);

        String finalDobISO = dobISO; // For lambda access
        new Thread(() -> {
            try {
                ApiClient.signup(email, password, name, finalDobISO, address);

                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("✓ Account created! Check your email for confirmation code.");
                    statusLabel.setForeground(new Color(0, 150, 0));
                    showConfirmEmailMode(email);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("Signup failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private void handleConfirmEmail() {
        String code = confirmCodeField.getText().trim();

        if (code.isEmpty() || pendingEmail == null) {
            statusLabel.setText("Please enter confirmation code");
            statusLabel.setForeground(Color.RED);
            return;
        }

        if (Config.DEMO_MODE) {
            // Demo mode: simulate confirmation
            statusLabel.setText("✓ Email confirmed! (DEMO MODE)");
            statusLabel.setForeground(new Color(0, 150, 0));
            showLoginMode();
            return;
        }

        setLoading(true);
        statusLabel.setText("Confirming email...");
        statusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                ApiClient.confirmEmail(pendingEmail, code);

                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("✓ Email confirmed! Please login.");
                    statusLabel.setForeground(new Color(0, 150, 0));
                    showLoginMode();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("Confirmation failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private void toggleMode() {
        if (isConfirmMode) {
            showLoginMode();
        } else {
            isSignupMode = !isSignupMode;
            refreshUI();
        }
    }

    private void showConfirmEmailMode(String email) {
        isConfirmMode = true;
        pendingEmail = email;
        emailField.setText(email);
        refreshUI();
    }

    private void showLoginMode() {
        isSignupMode = false;
        isConfirmMode = false;
        pendingEmail = null;
        refreshUI();
    }

    private void refreshUI() {
        if (isConfirmMode) {
            emailField.setEnabled(false);
            passwordField.setVisible(false);
            signupFieldsPanel.setVisible(false);
            confirmEmailPanel.setVisible(true);
            loginButton.setVisible(false);
            signupButton.setVisible(false);
            confirmEmailButton.setVisible(true);
            toggleModeButton.setText("Back to Login");

        } else if (isSignupMode) {
            emailField.setEnabled(true);
            passwordField.setVisible(true);
            signupFieldsPanel.setVisible(true);
            confirmEmailPanel.setVisible(false);
            loginButton.setVisible(false);
            signupButton.setVisible(true);
            confirmEmailButton.setVisible(false);
            toggleModeButton.setText("Already have an account? Login");

        } else {
            emailField.setEnabled(true);
            passwordField.setVisible(true);
            signupFieldsPanel.setVisible(false);
            confirmEmailPanel.setVisible(false);
            loginButton.setVisible(true);
            signupButton.setVisible(false);
            confirmEmailButton.setVisible(false);
            toggleModeButton.setText("Don't have an account? Sign up");
        }

        revalidate();
        repaint();
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        signupButton.setEnabled(!loading);
        confirmEmailButton.setEnabled(!loading);
        emailField.setEnabled(!loading && !isConfirmMode);
        passwordField.setEnabled(!loading);
        nameField.setEnabled(!loading);
        dobField.setEnabled(!loading);
        addressField.setEnabled(!loading);
        confirmCodeField.setEnabled(!loading);
    }

    public void reset() {
        emailField.setText("");
        passwordField.setText("");
        nameField.setText("");
        dobField.setText("");
        addressField.setText("");
        confirmCodeField.setText("");
        statusLabel.setText("");
        showLoginMode();
    }
}
