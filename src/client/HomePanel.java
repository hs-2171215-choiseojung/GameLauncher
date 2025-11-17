package client;

import model.UserData;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

// 서버 접속 패널 (IP, 포트 입력)
public class HomePanel extends JPanel {
    private GameLauncher launcher; 
    private JTextField roomNumberField;
    private JButton connectButton;
    private JLabel statusLabel;
    private JButton backButton;
    private JLabel userLabel; // 사용자 정보 레이블을 필드로 변경
    
    public HomePanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setBackground(new Color(240, 248, 255));
        
        // 상단: 타이틀
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        
        backButton = new JButton("← 뒤로가기");
        backButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        backButton.setFocusPainted(false);
        backButton.addActionListener(e -> launcher.switchToMainMenu());
        topPanel.add(backButton, BorderLayout.WEST);
        
        JLabel titleLabel = new JLabel("멀티플레이 접속");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 중앙: 입력 필드들
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(30, 40, 30, 40));
        
        // 사용자 정보 표시 (필드로 저장)
        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        userInfoPanel.setOpaque(false);
        userLabel = new JLabel("플레이어: Guest"); // 기본값
        userLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        userLabel.setForeground(new Color(70, 130, 180));
        userInfoPanel.add(userLabel);
        centerPanel.add(userInfoPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // 서버 IP
        JPanel roomNumberPanel = new JPanel(new BorderLayout(5, 5));
        roomNumberPanel.setOpaque(false);
        roomNumberPanel.setMaximumSize(new Dimension(400, 90));
        JLabel roomNumberLabel = new JLabel("방 번호:");
        roomNumberLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        roomNumberField = new JTextField("001");
        roomNumberField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        roomNumberPanel.add(roomNumberLabel, BorderLayout.NORTH);
        roomNumberPanel.add(roomNumberField, BorderLayout.CENTER);
        centerPanel.add(roomNumberPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        
        add(centerPanel, BorderLayout.CENTER);
        
        // 하단: 상태 메시지 및 접속 버튼
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(0, 20, 10, 20));
        
        statusLabel = new JLabel("서버 정보를 입력하고 접속하세요.");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomPanel.add(statusLabel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        connectButton = new JButton("접속하기");
        connectButton.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        connectButton.setPreferredSize(new Dimension(200, 40));
        connectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectButton.setBackground(new Color(70, 130, 180));
        connectButton.setForeground(Color.WHITE);
        connectButton.setFocusPainted(false);
        bottomPanel.add(connectButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // 리스너
        connectButton.addActionListener(e -> connectToServer());
        roomNumberField.addActionListener(e -> connectToServer());
    }
    
    // 사용자 정보 업데이트 (패널이 표시될 때 호출)
    public void updateUserInfo() {
        UserData userData = UserData.getInstance();
        if (userData != null && userData.getNickname() != null) {
            userLabel.setText("플레이어: " + userData.getNickname());
        } else {
            userLabel.setText("플레이어: Guest");
        }
    }
    
    public void resetUI() {
        connectButton.setEnabled(true);
        connectButton.setText("접속하기");
        statusLabel.setText("서버 연결이 끊겼습니다. 다시 시도하세요.");
        statusLabel.setForeground(Color.RED);
    }
    
    private void connectToServer() {
        String roomNumber = roomNumberField.getText().trim();
        UserData userData = UserData.getInstance();
        
        if (userData == null || userData.getNickname() == null) {
            statusLabel.setText("오류: 로그인이 필요합니다.");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        String name = userData.getNickname();
        
        String host = "127.0.0.1";
        int port = 9999;
        
        if (roomNumber.isEmpty()) {
            statusLabel.setText("오류: 방 번호를 입력하세요.");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        connectButton.setEnabled(false);
        connectButton.setText("접속 중...");
        statusLabel.setText(roomNumber + " 방에 연결 중...");
        statusLabel.setForeground(Color.BLACK);
        
        SwingWorker<Socket, Void> worker = new SwingWorker<Socket, Void>() {
            @Override
            protected Socket doInBackground() throws Exception {
                return new Socket(host, port);
            }
            
            @Override
            protected void done() {
                try {
                    Socket socket = get();
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    
                    launcher.switchToLobby(socket, out, in, name, roomNumber);
                } catch (Exception ex) {
                    statusLabel.setText("오류: 서버 연결 실패 - " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                    connectButton.setEnabled(true);
                    connectButton.setText("접속하기");
                }
            }
        };
        worker.execute();
    }
}