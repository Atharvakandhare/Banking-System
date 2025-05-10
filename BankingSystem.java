package bank;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class BankingSystem extends JFrame {
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    private JTextField loginUserField, regNameField;
    private JPasswordField loginPassField, regPassField;
    private JLabel welcomeLabel, balanceLabel;
    private JTextArea transactionArea;
    private String currentUser;

    private final double MIN_BALANCE = 1000;
    Connection conn;

    public BankingSystem() {
        connectToDB();
        setTitle("Bank Management System");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainPanel.add(loginPanel(), "Login");
        mainPanel.add(registerPanel(), "Register");
        mainPanel.add(dashboardPanel(), "Dashboard");

        add(mainPanel);
        cardLayout.show(mainPanel, "Login");
        setVisible(true);
    }

    void connectToDB() {
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/bankdb", "root", "mysql12");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Database connection failed!");
            System.exit(0);
        }
    }

    JPanel loginPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1));
        loginUserField = new JTextField();
        loginPassField = new JPasswordField();
        JButton loginBtn = new JButton("Login");
        JButton goToRegBtn = new JButton("Register");

        loginBtn.addActionListener(e -> login());
        goToRegBtn.addActionListener(e -> cardLayout.show(mainPanel, "Register"));

        panel.add(new JLabel("Username (e.g., user1):"));
        panel.add(loginUserField);
        panel.add(new JLabel("Password:"));
        panel.add(loginPassField);
        panel.add(loginBtn);
        panel.add(goToRegBtn);

        return panel;
    }

    JPanel registerPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1));
        regNameField = new JTextField();
        regPassField = new JPasswordField();
        JButton registerBtn = new JButton("Register");
        JButton backBtn = new JButton("Back to Login");

        registerBtn.addActionListener(e -> register());
        backBtn.addActionListener(e -> cardLayout.show(mainPanel, "Login"));

        panel.add(new JLabel("Full Name:"));
        panel.add(regNameField);
        panel.add(new JLabel("Password:"));
        panel.add(regPassField);
        panel.add(registerBtn);
        panel.add(backBtn);

        return panel;
    }

    JPanel dashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel topPanel = new JPanel();
        JPanel centerPanel = new JPanel(new GridLayout(5, 1, 10, 10));

        welcomeLabel = new JLabel();
        balanceLabel = new JLabel();
        transactionArea = new JTextArea(8, 30);
        transactionArea.setEditable(false);

        JButton depositBtn = new JButton("Deposit");
        JButton withdrawBtn = new JButton("Withdraw");
        JButton historyBtn = new JButton("Transaction History");
        JButton logoutBtn = new JButton("Logout");

        depositBtn.addActionListener(e -> deposit());
        withdrawBtn.addActionListener(e -> withdraw());
        historyBtn.addActionListener(e -> showHistory());
        logoutBtn.addActionListener(e -> logout());

        topPanel.add(welcomeLabel);
        topPanel.add(balanceLabel);

        centerPanel.add(depositBtn);
        centerPanel.add(withdrawBtn);
        centerPanel.add(historyBtn);
        centerPanel.add(new JScrollPane(transactionArea));
        centerPanel.add(logoutBtn);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    void register() {
        String name = regNameField.getText().trim();
        String password = String.valueOf(regPassField.getPassword()).trim();

        if (name.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and password are required.");
            return;
        }

        try {
            // Insert user with NULL username to get auto-generated ID
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (name, password) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, password);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                String username = "user" + id;

                // Update username based on ID
                PreparedStatement ps2 = conn.prepareStatement("UPDATE users SET username=? WHERE id=?");
                ps2.setString(1, username);
                ps2.setInt(2, id);
                ps2.executeUpdate();

                JOptionPane.showMessageDialog(this, "Registered successfully!\nYour username: " + username);
                cardLayout.show(mainPanel, "Login");
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Registration failed!");
            e.printStackTrace();
        }
    }

    void login() {
        String username = loginUserField.getText().trim();
        String password = String.valueOf(loginPassField.getPassword()).trim();

        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username=? AND password=?");
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                currentUser = username;
                updateDashboard();
                cardLayout.show(mainPanel, "Dashboard");
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void updateDashboard() {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT name, balance FROM users WHERE username=?");
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                welcomeLabel.setText("Welcome, " + rs.getString("name") + " (" + currentUser + ")");
                balanceLabel.setText("Balance: ₹" + rs.getDouble("balance"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void deposit() {
        String amountStr = JOptionPane.showInputDialog(this, "Enter amount to deposit:");
        if (amountStr != null) {
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) throw new NumberFormatException();

                PreparedStatement ps = conn.prepareStatement("UPDATE users SET balance = balance + ? WHERE username=?");
                ps.setDouble(1, amount);
                ps.setString(2, currentUser);
                ps.executeUpdate();

                recordTransaction("deposit", amount);
                updateDashboard();
                JOptionPane.showMessageDialog(this, "Deposited ₹" + amount + " successfully.");

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Invalid amount!");
            }
        }
    }

    void withdraw() {
        String amountStr = JOptionPane.showInputDialog(this, "Enter amount to withdraw:");
        if (amountStr != null) {
            try {
                double amount = Double.parseDouble(amountStr);

                PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE username=?");
                ps.setString(1, currentUser);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    if (balance - amount >= MIN_BALANCE) {
                        PreparedStatement ps2 = conn.prepareStatement("UPDATE users SET balance = balance - ? WHERE username=?");
                        ps2.setDouble(1, amount);
                        ps2.setString(2, currentUser);
                        ps2.executeUpdate();

                        recordTransaction("withdraw", amount);
                        updateDashboard();
                        JOptionPane.showMessageDialog(this, "Withdrawn ₹" + amount + " successfully.");
                    } else {
                        JOptionPane.showMessageDialog(this, "Insufficient balance! Minimum ₹1000 required.");
                    }
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Invalid amount!");
            }
        }
    }

    void recordTransaction(String type, double amount) {
        try {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO transactions (username, type, amount) VALUES (?, ?, ?)");
            ps.setString(1, currentUser);
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void showHistory() {
        transactionArea.setText("");
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT type, amount, timestamp FROM transactions WHERE username=? ORDER BY timestamp DESC");
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                transactionArea.append(rs.getString("timestamp") + " - " + rs.getString("type").toUpperCase() + ": ₹" + rs.getDouble("amount") + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void logout() {
        currentUser = null;
        loginUserField.setText("");
        loginPassField.setText("");
        transactionArea.setText("");
        cardLayout.show(mainPanel, "Login");
    }

    public static void main(String[] args) {
        new BankingSystem();
    }
}
