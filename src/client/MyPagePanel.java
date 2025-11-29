package client;

import model.UserData;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MyPagePanel extends JPanel {
    private GameLauncher launcher;
    private JLabel nicknameLabel;
    private JLabel levelLabel;
    private JLabel expLabel;
    private JProgressBar expBar;
    
    public MyPagePanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(30, 30, 30, 30));
        setBackground(new Color(245, 245, 245));
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        
        JButton backToMenuButton = new JButton("← 메인 메뉴");
        backToMenuButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        backToMenuButton.setFocusPainted(false);
        backToMenuButton.addActionListener(e -> launcher.switchToMainMenu());
        titlePanel.add(backToMenuButton, BorderLayout.WEST);
        
        JLabel titleLabel = new JLabel("마이페이지");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        
        add(titlePanel, BorderLayout.NORTH);
        
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        infoPanel.setBorder(new EmptyBorder(30, 50, 30, 50));
        
        JPanel profileCard = new JPanel();
        profileCard.setLayout(new BoxLayout(profileCard, BoxLayout.Y_AXIS));
        profileCard.setBackground(Color.WHITE);
        profileCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            new EmptyBorder(30, 30, 30, 30)
        ));
        
        JPanel nicknamePanel = createInfoRow("아이디");
        nicknameLabel = new JLabel("Guest");
        nicknameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        nicknamePanel.add(nicknameLabel);
        profileCard.add(nicknamePanel);
        profileCard.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JPanel levelPanel = createInfoRow("레벨");
        levelLabel = new JLabel("Lv. 1");
        levelLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        levelLabel.setForeground(new Color(70, 130, 180));
        levelPanel.add(levelLabel);
        profileCard.add(levelPanel);
        profileCard.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JPanel expPanel = new JPanel();
        expPanel.setLayout(new BoxLayout(expPanel, BoxLayout.Y_AXIS));
        expPanel.setOpaque(false);
        expPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel expTitleLabel = new JLabel("경험치");
        expTitleLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        expTitleLabel.setForeground(Color.GRAY);
        expTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        expPanel.add(expTitleLabel);
        expPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        
        expLabel = new JLabel("0 / 100 EXP");
        expLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        expLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        expPanel.add(expLabel);
        expPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        expBar = new JProgressBar();
        expBar.setStringPainted(true);
        expBar.setPreferredSize(new Dimension(400, 25));
        expBar.setMaximumSize(new Dimension(400, 25));
        expBar.setForeground(new Color(70, 130, 180));
        expBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        expBar.setMaximum(100);
        expBar.setValue(0);
        expBar.setString("0%");
        expPanel.add(expBar);
        
        profileCard.add(expPanel);
        
        infoPanel.add(profileCard);
        add(infoPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setOpaque(false);
        
        JButton changePasswordButton = new JButton("비밀번호 변경");
        changePasswordButton.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        changePasswordButton.setPreferredSize(new Dimension(140, 35));
        
        JButton logoutButton = new JButton("로그아웃");
        logoutButton.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        logoutButton.setPreferredSize(new Dimension(140, 35));
        logoutButton.setBackground(new Color(220, 53, 69));
        logoutButton.setForeground(Color.WHITE);
        
        buttonPanel.add(changePasswordButton);
        buttonPanel.add(logoutButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // 리스너
        changePasswordButton.addActionListener(e -> changePassword());
        logoutButton.addActionListener(e -> logout());
    }
    
    private JPanel createInfoRow(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        titleLabel.setForeground(Color.GRAY);
        titleLabel.setPreferredSize(new Dimension(80, 25));
        panel.add(titleLabel);
        
        return panel;
    }
    
    public void refreshData() {
        UserData userData = UserData.getInstance();
        if (userData == null || userData.getNickname() == null) {
            nicknameLabel.setText("Guest");
            levelLabel.setText("Lv. 1");
            expLabel.setText("0 / 100 EXP");
            expBar.setMaximum(100);
            expBar.setValue(0);
            expBar.setString("0%");
            return;
        }
        
        nicknameLabel.setText(userData.getNickname());
        levelLabel.setText("Lv. " + userData.getLevel());
        
        int currentExp = userData.getExperience();
        int maxExp = userData.getExpForNextLevel();
        expLabel.setText(currentExp + " / " + maxExp + " EXP");
        
        expBar.setMaximum(maxExp);
        expBar.setValue(currentExp);
        expBar.setString(String.format("%.1f%%", (currentExp * 100.0 / maxExp)));
    }
    
    private void changePassword() {
        UserData userData = UserData.getInstance();
        if (userData == null) {
            JOptionPane.showMessageDialog(this,
                "로그인이 필요합니다.",
                "오류",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JPasswordField oldPasswordField = new JPasswordField();
        JPasswordField newPasswordField = new JPasswordField();
        
        panel.add(new JLabel("현재 비밀번호:"));
        panel.add(oldPasswordField);
        panel.add(new JLabel("새 비밀번호:"));
        panel.add(newPasswordField);
        
        int result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "비밀번호 변경",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            String oldPassword = new String(oldPasswordField.getPassword());
            String newPassword = new String(newPasswordField.getPassword());
            
            if (oldPassword.isEmpty() || newPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "모든 필드를 입력하세요.",
                    "오류",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (newPassword.length() < 4) {
                JOptionPane.showMessageDialog(this,
                    "새 비밀번호는 4자 이상이어야 합니다.",
                    "오류",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            try {
                userData.changePassword(oldPassword, newPassword);
                JOptionPane.showMessageDialog(this,
                    "비밀번호가 변경되었습니다!",
                    "성공",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    e.getMessage(),
                    "오류",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "로그아웃 하시겠습니까?",
            "로그아웃",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            UserData.logout();
            launcher.switchToNicknameSetup();
        }
    }
}