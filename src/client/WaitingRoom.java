package client;

import model.GamePacket;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.ObjectOutputStream;
import java.util.Map;

// 대기방(WaitingRoom) 패널
public class WaitingRoom extends JPanel {
    private GameLauncher launcher;
    private ObjectOutputStream out;
    private String playerName;
    private String roomNumber;
    
    // UI 컴포넌트
    private JTextArea chatArea;
    private JTextField chatInput;
    private InfoPanel infoPanel;
    
    public WaitingRoom(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setSize(600, 400);
        
        // 1. 중앙: 채팅창
        chatArea = new JTextArea("대기방에 입장했습니다.\n");
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        
        // 2. 오른쪽: 정보/설정 패널
        infoPanel = new InfoPanel(launcher);
        add(infoPanel, BorderLayout.EAST);
        
        // 3. 하단: 채팅 입력
        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        chatInput = new JTextField();
        JButton sendButton = new JButton("전송");
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        add(chatInputPanel, BorderLayout.SOUTH);
        
        // 리스너
        ActionListener sendChatAction = e -> sendChat();
        chatInput.addActionListener(sendChatAction);
        sendButton.addActionListener(sendChatAction);
    }
    
    public void setConnection(ObjectOutputStream out, String playerName, String roomNumber) {
        this.out = out;
        this.playerName = playerName;
        this.roomNumber = roomNumber;
        infoPanel.setPlayerName(playerName);
        
        GamePacket join = new GamePacket(
            GamePacket.Type.JOIN,
            playerName,
            roomNumber,
            true
        );
        launcher.sendPacket(join);
    }
    
    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        
        GamePacket chatPacket = new GamePacket(
            GamePacket.Type.MESSAGE,
            playerName,
            text
        );
        launcher.sendPacket(chatPacket);
        chatInput.setText("");
    }
    
    // GameLauncher가 호출하여 InfoPanel 갱신
    public void updateLobbyInfo(String hostName, Map<String, Boolean> playerStatus, 
                                String difficulty, String gameMode) {
        infoPanel.updateUI(hostName, playerStatus, difficulty, gameMode);
    }
    
    // GameLauncher가 호출하여 채팅창 갱신
    public void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}