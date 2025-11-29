package client;

import model.GamePacket;
import model.UserData;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HomePanel extends JPanel {
    private GameLauncher launcher; 
    private JTextField roomNumberField;
    private JButton connectButton;
    private JLabel statusLabel;
    private JButton backButton;
    private JLabel userLabel; 
    
    public HomePanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setBackground(new Color(240, 248, 255));
        
      
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
        
       
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(30, 40, 30, 40));
        
        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        userInfoPanel.setOpaque(false);
        userLabel = new JLabel("플레이어: Guest"); // 기본값
        userLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        userLabel.setForeground(new Color(70, 130, 180));
        userInfoPanel.add(userLabel);
        centerPanel.add(userInfoPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
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
        
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(0, 20, 10, 20));
        
        statusLabel = new JLabel("방 번호를 입력하고 접속하세요.");
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
        
        connectButton.addActionListener(e -> connectToServer());
        roomNumberField.addActionListener(e -> connectToServer());
    }
    
    private static class ConnectionContext {
        Socket socket;
        ObjectOutputStream out;
        ObjectInputStream in;
        GamePacket firstPacket; 

        public ConnectionContext(Socket socket, ObjectOutputStream out, ObjectInputStream in, GamePacket firstPacket) {
            this.socket = socket;
            this.out = out;
            this.in = in;
            this.firstPacket = firstPacket;
        }
    }

    
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
        statusLabel.setText("방 번호를 입력하고 접속하세요.");
        statusLabel.setForeground(Color.BLACK);
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
        
        SwingWorker<ConnectionContext, Void> worker = new SwingWorker<ConnectionContext, Void>() {
            @Override
            protected ConnectionContext doInBackground() throws Exception {
                Socket socket = new Socket(host, port);
                
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                GamePacket joinPacket = new GamePacket(GamePacket.Type.JOIN, name, roomNumber, true);
                joinPacket.setGameType(launcher.getGameModeType()); // NORMAL 또는 FLASHLIGHT
                out.writeObject(joinPacket);
                out.flush();
                
                GamePacket firstPacket = null;

                Object response = in.readObject();
                GamePacket packet = (GamePacket) response;

                if (packet.getType() == GamePacket.Type.MESSAGE &&
                    packet.getMessage().startsWith("오류")) {

                    socket.close();
                    throw new Exception(packet.getMessage());
                }

                return new ConnectionContext(socket, out, in, packet);

            }
            
            @Override
            protected void done() {
                try {
                    ConnectionContext context = get();
                 
                    launcher.switchToLobby(
                        context.socket,
                        context.out,
                        context.in,
                        name,
                        roomNumber,
                        context.firstPacket
                    );
                    connectButton.setEnabled(true);
                    connectButton.setText("접속하기");
                    statusLabel.setText("방 번호를 입력하고 접속하세요.");
                    
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    if (ex instanceof java.util.concurrent.ExecutionException) {
                        msg = ex.getCause().getMessage();
                    }
                    
                    statusLabel.setText(msg);
                    statusLabel.setForeground(Color.RED);
           
                    JOptionPane.showMessageDialog(HomePanel.this,
                        "서버 접속 실패:\n" + msg,
                        "연결 오류",
                        JOptionPane.ERROR_MESSAGE);
                    
                    connectButton.setEnabled(true);
                    connectButton.setText("접속하기");
                }
            }
        };
        worker.execute();
    }
}