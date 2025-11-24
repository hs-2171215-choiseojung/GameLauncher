package client;

import model.UserData;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

// 로그인/회원가입 패널
public class NicknameSetupPanel extends JPanel {
    private GameLauncher launcher;
    private JTextField nicknameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;
    
    public NicknameSetupPanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(30, 30, 30, 30));
        setBackground(new Color(240, 248, 255));
        
        // 상단: 타이틀
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        
        JLabel titleLabel = new JLabel("숨은 그림 찾기");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 32));
        titleLabel.setForeground(new Color(70, 130, 180));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel subtitleLabel = new JLabel("로그인 또는 회원가입");
        subtitleLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        titlePanel.add(subtitleLabel);
        add(titlePanel, BorderLayout.NORTH);
        
        // 중앙: 입력 필드
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(50, 50, 50, 50));
        
        // 닉네임
        JLabel nicknameLabel = new JLabel("닉네임 (2~12자)");
        nicknameLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        nicknameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(nicknameLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        nicknameField = new JTextField();
        nicknameField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        nicknameField.setMaximumSize(new Dimension(300, 40));
        nicknameField.setHorizontalAlignment(JTextField.CENTER);
        nicknameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(nicknameField);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // 비밀번호
        JLabel passwordLabel = new JLabel("비밀번호 (4자 이상)");
        passwordLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(passwordLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        passwordField = new JPasswordField();
        passwordField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        passwordField.setMaximumSize(new Dimension(300, 40));
        passwordField.setHorizontalAlignment(JTextField.CENTER);
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(passwordField);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setForeground(Color.RED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(statusLabel);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // 하단: 버튼들
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.setOpaque(false);
        
        loginButton = new JButton("로그인");
        loginButton.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        loginButton.setPreferredSize(new Dimension(140, 45));
        loginButton.setBackground(new Color(70, 130, 180));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        bottomPanel.add(loginButton);
        
        registerButton = new JButton("회원가입");
        registerButton.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        registerButton.setPreferredSize(new Dimension(140, 45));
        registerButton.setBackground(new Color(40, 167, 69));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        bottomPanel.add(registerButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // 리스너
        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());
        nicknameField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> login());
    }
    
    // 로그인
    private void login() {
        String nickname = nicknameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (nickname.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "닉네임과 비밀번호를 모두 입력하세요.",
                "입력 오류",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (UserData.login(nickname, password)) {
                statusLabel.setText("로그인 성공!");
                statusLabel.setForeground(new Color(34, 139, 34));
                launcher.switchToMainMenu();
            } else {
                JOptionPane.showMessageDialog(this,
                    "닉네임 또는 비밀번호가 일치하지 않습니다.",
                    "로그인 실패",
                    JOptionPane.ERROR_MESSAGE);
                passwordField.setText("");
                passwordField.requestFocus();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "로그인 중 오류가 발생했습니다: " + e.getMessage(),
                "오류",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // 회원가입
    private void register() {
        String nickname = nicknameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        // 유효성 검사
        if (nickname.isEmpty() || password.isEmpty()) {
            statusLabel.setText("닉네임과 비밀번호를 입력하세요");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        if (nickname.length() < 2 || nickname.length() > 12) {
            statusLabel.setText("닉네임은 2~12자 사이여야 합니다");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        if (password.length() < 4) {
            statusLabel.setText("비밀번호는 4자 이상이어야 합니다");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        // 특수문자 검사
        if (!nickname.matches("^[a-zA-Z0-9가-힣_]+$")) {
            statusLabel.setText("닉네임에는 영문, 한글, 숫자, _ 만 사용 가능합니다");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        // 중복 검사 및 회원가입
        if (UserData.register(nickname, password)) {
            statusLabel.setText("회원가입 성공! 로그인 해주세요");
            statusLabel.setForeground(new Color(34, 139, 34));
            passwordField.setText("");
        } else {
            statusLabel.setText("이미 사용 중인 닉네임입니다");
            statusLabel.setForeground(Color.RED);
            nicknameField.selectAll();
        }
    }
    
    // UI 초기화 (로그아웃 시)
    public void resetUI() {
        nicknameField.setText("");
        passwordField.setText("");
        statusLabel.setText(" ");
        nicknameField.requestFocus();
    }
}