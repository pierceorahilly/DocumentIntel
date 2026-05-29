package com.example.desktop.ui;

import com.example.desktop.BankBuddyApp;
import com.example.desktop.api.ApiClient;
import com.example.desktop.utils.Config;
import com.example.desktop.utils.TokenManager;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main panel for PDF upload and results display.
 */
public class MainPanel extends JPanel {

    private final BankBuddyApp app;

    private JLabel welcomeLabel;
    private JButton selectPdfButton;
    private JButton uploadButton;
    private JButton logoutButton;
    private JLabel selectedFileLabel;
    private JTextArea adviceArea;
    private JTextArea transactionsArea;
    private JLabel statusLabel;
    private ChartPanel chartPanel;
    private JTabbedPane tabbedPane;
    private JPanel billFlagContainer;

    private File selectedFile = null;

    public MainPanel(BankBuddyApp app) {
        this.app = app;
        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(245, 245, 245));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        initComponents();
    }

    private void initComponents() {
        // Top Panel (Welcome + Logout)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(245, 245, 245));

        welcomeLabel = new JLabel("Welcome!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 20));
        topPanel.add(welcomeLabel, BorderLayout.WEST);

        logoutButton = new JButton("Logout");
        logoutButton.setFont(new Font("Arial", Font.PLAIN, 12));
        logoutButton.addActionListener(e -> logout());
        topPanel.add(logoutButton, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel (Main Content)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(245, 245, 245));

        // Instructions
        JLabel instructionsLabel = new JLabel("<html><div style='text-align: center;'>" +
                "Upload your bank statement PDF to get personalized savings advice from AI<br>" +
                (Config.DEMO_MODE ? "<b style='color: orange;'>⚠️ DEMO MODE - Shows sample data</b>" : "") +
                "</div></html>");
        instructionsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        instructionsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionsLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        centerPanel.add(instructionsLabel);

        // Select PDF Button
        selectPdfButton = new JButton("Select PDF File");
        selectPdfButton.setFont(new Font("Arial", Font.BOLD, 16));
        selectPdfButton.setBackground(new Color(46, 232, 137));
        selectPdfButton.setForeground(Color.WHITE);
        selectPdfButton.setFocusPainted(false);
        selectPdfButton.setPreferredSize(new Dimension(200, 50));
        selectPdfButton.setMaximumSize(new Dimension(200, 50));
        selectPdfButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        selectPdfButton.addActionListener(e -> selectPdf());
        centerPanel.add(selectPdfButton);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Selected File Label
        selectedFileLabel = new JLabel(" ");
        selectedFileLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        selectedFileLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(selectedFileLabel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Upload Button
        uploadButton = new JButton("Upload & Analyze");
        uploadButton.setFont(new Font("Arial", Font.BOLD, 16));
        uploadButton.setBackground(new Color(33, 150, 243));
        uploadButton.setForeground(Color.WHITE);
        uploadButton.setFocusPainted(false);
        uploadButton.setPreferredSize(new Dimension(200, 50));
        uploadButton.setMaximumSize(new Dimension(200, 50));
        uploadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> uploadPdf());
        centerPanel.add(uploadButton);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Status Label
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(statusLabel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Bill Flag Alert Container (populated after analysis, hidden by default)
        billFlagContainer = new JPanel(new BorderLayout());
        billFlagContainer.setVisible(false);
        billFlagContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        billFlagContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        centerPanel.add(billFlagContainer);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Results Panel (split into advice and tabbed pane for transactions/chart)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(450);

        // Financial Advice Area
        JPanel advicePanel = new JPanel(new BorderLayout());
        JLabel adviceLabel = new JLabel("💡 Financial Advice:");
        adviceLabel.setFont(new Font("Arial", Font.BOLD, 14));
        adviceLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        advicePanel.add(adviceLabel, BorderLayout.NORTH);

        adviceArea = new JTextArea();
        adviceArea.setEditable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setFont(new Font("Arial", Font.PLAIN, 13));
        adviceArea.setText("Your personalized savings advice will appear here...");
        JScrollPane adviceScroll = new JScrollPane(adviceArea);
        advicePanel.add(adviceScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(advicePanel);

        // Tabbed Pane for Transactions and Chart
        tabbedPane = new JTabbedPane();

        // Transactions Tab
        JPanel transactionsPanel = new JPanel(new BorderLayout());
        transactionsArea = new JTextArea();
        transactionsArea.setEditable(false);
        transactionsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        transactionsArea.setText("Extracted transactions will appear here...");
        JScrollPane transactionsScroll = new JScrollPane(transactionsArea);
        transactionsPanel.add(transactionsScroll, BorderLayout.CENTER);
        tabbedPane.addTab("📊 Transactions", transactionsPanel);

        // Chart Tab (initially empty)
        JPanel chartPanelContainer = new JPanel(new BorderLayout());
        chartPanelContainer.add(new JLabel("Spending chart will appear here after upload", SwingConstants.CENTER), BorderLayout.CENTER);
        tabbedPane.addTab("📈 Spending Chart", chartPanelContainer);

        splitPane.setBottomComponent(tabbedPane);

        centerPanel.add(splitPane);

        JScrollPane centerScroll = new JScrollPane(centerPanel);
        centerScroll.setBorder(null);
        centerScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(centerScroll, BorderLayout.CENTER);
    }

    private void selectPdf() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            selectedFileLabel.setText("Selected: " + selectedFile.getName());
            uploadButton.setEnabled(true);
            statusLabel.setText(" ");
        }
    }

    private void uploadPdf() {
        if (selectedFile == null) {
            statusLabel.setText("Please select a PDF file first");
            statusLabel.setForeground(Color.RED);
            return;
        }

        // CRITICAL: Validate PDF BEFORE uploading to save bandwidth and Lambda costs!
        String validationError = validatePdf(selectedFile);
        if (validationError != null) {
            statusLabel.setText(validationError);
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, validationError, "Invalid PDF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (Config.DEMO_MODE) {
            // Demo mode: show sample data
            showDemoResults();
            return;
        }

        setLoading(true);
        statusLabel.setText("Uploading PDF...");
        statusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                // Step 1: Upload PDF (returns immediately with uploadId)
                ApiClient.UploadResponse uploadResponse = ApiClient.uploadPdf(selectedFile);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✓ Uploaded! Processing in background...");
                });

                // Step 2: Poll for status until complete
                String uploadId = uploadResponse.uploadId;
                ApiClient.UploadResponse finalResponse = null;
                int pollCount = 0;
                int maxPolls = 60; // Max 2 minutes (60 polls x 2 seconds)

                while (pollCount < maxPolls) {
                    Thread.sleep(2000); // Poll every 2 seconds

                    ApiClient.UploadResponse statusResponse = ApiClient.getUploadStatus(uploadId);

                    if ("completed".equalsIgnoreCase(statusResponse.status)) {
                        finalResponse = statusResponse;
                        break;
                    } else if ("failed".equalsIgnoreCase(statusResponse.status)) {
                        throw new Exception("Processing failed: " + statusResponse.message);
                    }

                    // Update status message
                    pollCount++;
                    final int currentPoll = pollCount;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Processing... (" + (currentPoll * 2) + "s)");
                    });
                }

                if (finalResponse == null) {
                    throw new Exception("Processing timeout - please check status later");
                }

                // Step 3: Display results
                final ApiClient.UploadResponse result = finalResponse;
                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    displayResults(result);
                    statusLabel.setText("✓ Successfully processed " + result.transactionCount + " transactions in " +
                            String.format("%.1f", result.processingTime) + " seconds");
                    statusLabel.setForeground(new Color(0, 150, 0));
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setLoading(false);
                    statusLabel.setText("Upload failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(),
                            "Upload Failed", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void showDemoResults() {
        // Simulate processing delay
        setLoading(true);
        statusLabel.setText("Processing... (DEMO MODE)");
        statusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simulate processing time
            } catch (InterruptedException ignored) {}

            SwingUtilities.invokeLater(() -> {
                setLoading(false);

                // Sample advice
                adviceArea.setText("Great news! I've analyzed your bank statement and found some opportunities to save money:\n\n" +
                        "💰 Subscription Savings: I noticed you have Netflix ($15.99), Spotify ($10.99), and HBO Max ($15.99). " +
                        "Consider bundling or choosing just 1-2 services. Potential savings: $20-40/month ($240-480/year)\n\n" +
                        "🍔 Dining Out: You spent $387 on restaurants this month. Try cooking at home 2-3 more times per week. " +
                        "Potential savings: $150/month ($1,800/year)\n\n" +
                        "☕ Coffee Habits: $142 at coffee shops this month! Making coffee at home could save you $100/month ($1,200/year)\n\n" +
                        "📱 Check your phone plan: You're paying $85/month. Consider switching to a prepaid plan for $30-40/month. " +
                        "Potential savings: $45/month ($540/year)\n\n" +
                        "💡 Total Potential Savings: $315-335/month or $3,780-4,020/year!\n\n" +
                        "Remember, small changes add up. Start with one or two suggestions and build from there. You've got this! 💪");

                // Sample transactions
                transactionsArea.setText(String.format("%-12s %-40s %12s %12s\n", "Date", "Description", "Amount", "Balance") +
                        "─".repeat(80) + "\n" +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-15", "AMAZON.COM", "-45.99", "1,234.56") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-14", "STARBUCKS #1234", "-6.75", "1,280.55") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-13", "NETFLIX SUBSCRIPTION", "-15.99", "1,287.30") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-12", "WHOLE FOODS MARKET", "-87.23", "1,303.29") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-11", "UBER RIDE", "-24.50", "1,390.52") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-10", "CHIPOTLE", "-12.35", "1,415.02") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-09", "SPOTIFY SUBSCRIPTION", "-10.99", "1,427.37") +
                        String.format("%-12s %-40s %12s %12s\n", "2025-11-08", "SALARY DEPOSIT", "+2,500.00", "1,438.36") +
                        "\n... and 34 more transactions");

                // Create demo pie chart
                DefaultPieDataset<String> demoDataset = new DefaultPieDataset<>();
                demoDataset.setValue("Shopping", 145.99);
                demoDataset.setValue("Dining", 143.60);
                demoDataset.setValue("Subscriptions", 26.98);
                demoDataset.setValue("Groceries", 87.23);
                demoDataset.setValue("Transportation", 24.50);

                JFreeChart demoChart = ChartFactory.createPieChart(
                    "Spending by Category",
                    demoDataset,
                    true,
                    true,
                    false
                );

                if (chartPanel == null) {
                    chartPanel = new ChartPanel(demoChart);
                    chartPanel.setPreferredSize(new Dimension(600, 400));
                    tabbedPane.setComponentAt(1, chartPanel);
                } else {
                    chartPanel.setChart(demoChart);
                }

                statusLabel.setText("✓ Processed 42 transactions in 2.0 seconds (DEMO MODE)");
                statusLabel.setForeground(new Color(0, 150, 0));
            });
        }).start();
    }

    private void displayResults(ApiClient.UploadResponse response) {
        // Display bill flag warnings (hard-coded threshold advice)
        if (response.billFlags != null && !response.billFlags.isEmpty()) {
            displayBillFlags(response.billFlags);
        }

        // Display AI advice FIRST (with null check)
        StringBuilder adviceText = new StringBuilder();

        if (response.advice != null && !response.advice.isEmpty()) {
            adviceText.append(response.advice);
        } else {
            adviceText.append("⚠️ Advice generation failed. Transactions extracted successfully.");
        }

        adviceArea.setText(adviceText.toString());

        // THEN prepend spending insights (so they appear above the advice)
        displaySpendingInsights(response);

        // Display transactions (with null check)
        if (response.transactions == null || response.transactions.isEmpty()) {
            transactionsArea.setText("⚠️ No transactions found or transactions data unavailable.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %-40s %12s %12s\n", "Date", "Description", "Amount", "Balance"));
        sb.append("─".repeat(80)).append("\n");

        for (ApiClient.Transaction txn : response.transactions) {
            sb.append(String.format("%-12s %-40s %12s %12s\n",
                    txn.date,
                    txn.description.length() > 40 ? txn.description.substring(0, 37) + "..." : txn.description,
                    txn.amount,
                    txn.balance));
        }

        transactionsArea.setText(sb.toString());

        // Update pie chart with category data
        updatePieChart(response.categoryAnalysis);
    }

    private void displayBillFlags(List<ApiClient.BillFlag> billFlags) {
        billFlagContainer.removeAll();

        // Outer wrapper with orange left accent bar
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(255, 248, 240));
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(255, 152, 0)),
                BorderFactory.createLineBorder(new Color(235, 225, 215), 1)
        ));

        // --- Header bar ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(255, 240, 220));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 225, 215)),
                BorderFactory.createEmptyBorder(10, 14, 10, 10)
        ));

        JLabel titleLabel = new JLabel("\u26A0  Bill Alert \u2014 You may be overpaying");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 15));
        titleLabel.setForeground(new Color(200, 80, 0));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        headerButtons.setOpaque(false);

        JButton collapseButton = new JButton("\u2212");
        collapseButton.setToolTipText("Collapse");
        collapseButton.setFont(new Font("Arial", Font.BOLD, 16));
        collapseButton.setPreferredSize(new Dimension(30, 28));
        collapseButton.setMargin(new Insets(0, 0, 0, 0));
        collapseButton.setFocusPainted(false);
        collapseButton.setBorderPainted(false);
        collapseButton.setContentAreaFilled(false);
        collapseButton.setForeground(new Color(150, 120, 80));
        collapseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton dismissButton = new JButton("\u2715");
        dismissButton.setToolTipText("Dismiss");
        dismissButton.setFont(new Font("Arial", Font.PLAIN, 14));
        dismissButton.setPreferredSize(new Dimension(30, 28));
        dismissButton.setMargin(new Insets(0, 0, 0, 0));
        dismissButton.setFocusPainted(false);
        dismissButton.setBorderPainted(false);
        dismissButton.setContentAreaFilled(false);
        dismissButton.setForeground(new Color(150, 120, 80));
        dismissButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        headerButtons.add(collapseButton);
        headerButtons.add(dismissButton);
        headerPanel.add(headerButtons, BorderLayout.EAST);

        wrapper.add(headerPanel, BorderLayout.NORTH);

        // --- Collapsible content panel ---
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(255, 248, 240));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));

        // Individual flag cards
        for (ApiClient.BillFlag flag : billFlags) {
            JPanel card = new JPanel(new BorderLayout(0, 6));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(225, 225, 225), 1),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Card header row: provider name on the left, amount on the right
            JPanel cardHeader = new JPanel(new BorderLayout());
            cardHeader.setOpaque(false);

            String providerDisplay = flag.providerName != null ? flag.providerName : "Unknown";
            JLabel providerLabel = new JLabel(providerDisplay);
            providerLabel.setFont(new Font("Arial", Font.BOLD, 14));
            providerLabel.setForeground(new Color(50, 50, 50));
            cardHeader.add(providerLabel, BorderLayout.WEST);

            if (flag.amount != null) {
                JLabel amountLabel = new JLabel(String.format("\u00A3%.2f / month", flag.amount));
                amountLabel.setFont(new Font("Arial", Font.BOLD, 14));
                amountLabel.setForeground(new Color(210, 55, 55));
                cardHeader.add(amountLabel, BorderLayout.EAST);
            }

            card.add(cardHeader, BorderLayout.NORTH);

            // Card body: reason + advice tip
            JPanel cardBody = new JPanel();
            cardBody.setLayout(new BoxLayout(cardBody, BoxLayout.Y_AXIS));
            cardBody.setOpaque(false);

            if (flag.reason != null) {
                JLabel reasonLabel = new JLabel("<html><body style='width:100%'>" + flag.reason + "</body></html>");
                reasonLabel.setFont(new Font("Arial", Font.PLAIN, 12));
                reasonLabel.setForeground(new Color(100, 100, 100));
                reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                cardBody.add(reasonLabel);
                cardBody.add(Box.createRigidArea(new Dimension(0, 6)));
            }

            if (flag.advice != null) {
                JLabel adviceLabel = new JLabel("<html><body style='width:100%'><b>Tip:</b> " + flag.advice + "</body></html>");
                adviceLabel.setFont(new Font("Arial", Font.PLAIN, 12));
                adviceLabel.setForeground(new Color(60, 120, 60));
                adviceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                cardBody.add(adviceLabel);
            }

            card.add(cardBody, BorderLayout.CENTER);

            contentPanel.add(card);
            contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        // Separator before contact CTA
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setForeground(new Color(220, 215, 205));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(separator);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Contact CTA section
        JLabel contactQuestion = new JLabel("Would you like a support advisor to contact you?");
        contactQuestion.setFont(new Font("Arial", Font.BOLD, 13));
        contactQuestion.setForeground(new Color(80, 80, 80));
        contactQuestion.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(contactQuestion);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton contactButton = new JButton("  Yes \u2014 Contact me with help  ");
        contactButton.setFont(new Font("Arial", Font.BOLD, 13));
        contactButton.setBackground(new Color(255, 152, 0));
        contactButton.setForeground(Color.WHITE);
        contactButton.setFocusPainted(false);
        contactButton.setBorderPainted(false);
        contactButton.setPreferredSize(new Dimension(280, 38));
        contactButton.setMaximumSize(new Dimension(280, 38));
        contactButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        contactButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        contactButton.addActionListener(e -> {
            contactButton.setEnabled(false);
            contactButton.setText("Submitting...");
            new Thread(() -> {
                try {
                    ApiClient.submitContactRequest(billFlags);
                    SwingUtilities.invokeLater(() -> {
                        contactButton.setText("\u2713  Submitted \u2014 We'll be in touch");
                        contactButton.setBackground(new Color(76, 175, 80));
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        contactButton.setText("Failed \u2014 Try again");
                        contactButton.setEnabled(true);
                        contactButton.setBackground(new Color(255, 152, 0));
                    });
                }
            }).start();
        });
        contentPanel.add(contactButton);

        wrapper.add(contentPanel, BorderLayout.CENTER);

        // Collapse toggle action
        collapseButton.addActionListener(e -> {
            boolean visible = contentPanel.isVisible();
            contentPanel.setVisible(!visible);
            collapseButton.setText(visible ? "+" : "\u2212");
            collapseButton.setToolTipText(visible ? "Expand" : "Collapse");
            billFlagContainer.revalidate();
            billFlagContainer.repaint();
        });

        // Dismiss action
        dismissButton.addActionListener(e -> {
            billFlagContainer.setVisible(false);
            billFlagContainer.revalidate();
        });

        billFlagContainer.add(wrapper, BorderLayout.CENTER);
        billFlagContainer.setVisible(true);
        billFlagContainer.revalidate();
        billFlagContainer.repaint();
    }

    private void displaySpendingInsights(ApiClient.UploadResponse response) {
        if (response.categoryAnalysis == null) {
            return;
        }

        ApiClient.CategoryAnalysis analysis = response.categoryAnalysis;
        StringBuilder insights = new StringBuilder();

        insights.append("=== SPENDING ANALYSIS ===\n\n");

        // Total spending
        insights.append(String.format("Total Spent: €%.2f\n\n", analysis.totalSpent));

        // Biggest category
        if (analysis.biggestCategory != null && !analysis.biggestCategory.equals("None")) {
            Double biggestAmount = analysis.categoryTotals.get(analysis.biggestCategory);
            insights.append(String.format("Biggest Category: %s (€%.2f)\n\n",
                    analysis.biggestCategory, biggestAmount));
        }

        // Subscriptions
        if (analysis.subscriptions != null && !analysis.subscriptions.isEmpty()) {
            insights.append("RECURRING SUBSCRIPTIONS:\n");
            double totalSubscriptions = 0.0;
            for (ApiClient.Subscription sub : analysis.subscriptions) {
                insights.append(String.format("  • %s: €%.2f (%s, %d occurrences)\n",
                        sub.merchant, sub.amount, sub.frequency, sub.occurrences));
                totalSubscriptions += sub.amount;
            }
            insights.append(String.format("\nTotal Monthly Subscriptions: €%.2f\n\n", totalSubscriptions));
        }

        // Category breakdown with individual transactions
        insights.append("SPENDING BY CATEGORY:\n");
        insights.append("─".repeat(60)).append("\n");

        analysis.categoryTotals.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    String catName = entry.getKey();
                    double catTotal = entry.getValue();
                    Integer count = analysis.categoryCounts.get(catName);
                    if (count == null) count = 0;

                    if (catTotal > 0) {
                        insights.append(String.format("\n[%s]  €%.2f  (%d transactions)\n",
                                catName, catTotal, count));

                        // List individual transactions in this category
                        if (analysis.categoryTransactions != null) {
                            List<ApiClient.TransactionDetail> txns = analysis.categoryTransactions.get(catName);
                            if (txns != null && !txns.isEmpty()) {
                                for (ApiClient.TransactionDetail txn : txns) {
                                    String desc = txn.description != null ? txn.description : "Unknown";
                                    if (desc.length() > 45) desc = desc.substring(0, 42) + "...";
                                    String date = txn.date != null ? txn.date : "";
                                    insights.append(String.format("    %-10s  %-45s  €%.2f\n",
                                            date, desc, txn.amount));
                                }
                            }
                        }
                    } else {
                        insights.append(String.format("\n[%s]  €0.00  (0 transactions)\n", catName));
                    }
                });

        insights.append("\n" + "─".repeat(60) + "\n\n");

        // Prepend insights to advice area
        String currentText = adviceArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            adviceArea.setText(insights.toString() + currentText);
        } else {
            adviceArea.setText(insights.toString());
        }
    }

    private void updatePieChart(ApiClient.CategoryAnalysis analysis) {
        if (analysis == null || analysis.categoryTotals == null) {
            return;
        }

        // Create pie dataset from category totals
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        analysis.categoryTotals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .forEach(entry -> dataset.setValue(entry.getKey(), entry.getValue()));

        // Create chart
        JFreeChart chart = ChartFactory.createPieChart(
            "Spending by Category",
            dataset,
            true,  // legend
            true,  // tooltips
            false  // URLs
        );

        // Update chart panel
        if (chartPanel == null) {
            chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new Dimension(600, 400));
            tabbedPane.setComponentAt(1, chartPanel);
        } else {
            chartPanel.setChart(chart);
        }
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void setLoading(boolean loading) {
        selectPdfButton.setEnabled(!loading);
        uploadButton.setEnabled(!loading && selectedFile != null);
        logoutButton.setEnabled(!loading);
    }

    private void logout() {
        TokenManager.clearTokens();
        app.showLogin();
    }

    public void setUserEmail(String email) {
        welcomeLabel.setText("Welcome, " + email + "!");
    }

    /**
     * Validate PDF file BEFORE uploading to cloud.
     * This saves bandwidth, Lambda invocations, and provides instant feedback.
     *
     * @param file The PDF file to validate
     * @return Error message if invalid, null if valid
     */
    private String validatePdf(File file) {
        // 1. Check file exists
        if (!file.exists()) {
            return "File does not exist: " + file.getName();
        }

        // 2. Check file is readable
        if (!file.canRead()) {
            return "Cannot read file (check permissions): " + file.getName();
        }

        // 3. Check file is not empty
        long fileSize = file.length();
        if (fileSize == 0) {
            return "File is empty (0 bytes): " + file.getName();
        }

        // 4. Check file size (min 1 KB, max 4 MB)
        // Note: 4 MB limit ensures base64-encoded payload stays under Lambda's 6 MB limit
        // (4 MB × 1.33 base64 overhead = 5.33 MB, safely under 6 MB)
        long minSize = 1024; // 1 KB
        long maxSize = 4 * 1024 * 1024; // 4 MB

        if (fileSize < minSize) {
            return "File too small (minimum 1 KB): " + file.getName() + " is " + fileSize + " bytes";
        }

        if (fileSize > maxSize) {
            long sizeMB = fileSize / (1024 * 1024);
            return "File too large (maximum 4 MB): " + file.getName() + " is " + sizeMB + " MB";
        }

        // 5. Check file extension (case-insensitive)
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            return "File must have .pdf extension: " + file.getName();
        }

        // 6. Check PDF magic bytes (%PDF at start of file)
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] header = new byte[4];
            int bytesRead = fis.read(header);
            fis.close();

            if (bytesRead < 4) {
                return "File too small to be a valid PDF: " + file.getName();
            }

            // PDF files start with %PDF
            if (header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                return "File is not a valid PDF (missing %PDF header): " + file.getName();
            }

        } catch (java.io.IOException e) {
            return "Cannot read file: " + e.getMessage();
        }

        // All checks passed!
        return null;
    }
}
