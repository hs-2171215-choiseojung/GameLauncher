package client;

import model.GamePacket;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.ObjectOutputStream;
import java.util.Map;

public class WaitingRoom extends JPanel {

    private GameLauncher launcher;
    private ObjectOutputStream out;
    private String playerName;
    private String roomNumber;

    private final String gameType;

    private JTextArea chatArea;
    private JTextField chatInput;
    private InfoPanel infoPanel;

    public WaitingRoom(GameLauncher launcher, String gameType) {
        this.launcher = launcher;
        this.gameType = gameType;

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setSize(600, 400);

        chatArea = new JTextArea("대기방에 입장했습니다.\n");
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        infoPanel = new InfoPanel(launcher, gameType);
        add(infoPanel, BorderLayout.EAST);

        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        chatInput = new JTextField();
        JButton sendButton = new JButton("전송");
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        add(chatInputPanel, BorderLayout.SOUTH);

        ActionListener sendChatAction = e -> sendChat();
        chatInput.addActionListener(sendChatAction);
        sendButton.addActionListener(sendChatAction);
    }

    // ★★★★★ 핵심: resetUI() 삭제 → 방장 UI가 깨지는 문제 해결
    public void setConnection(ObjectOutputStream out, String playerName, String roomNumber) {
        this.out = out;
        this.playerName = playerName;
        this.roomNumber = roomNumber;

        infoPanel.setPlayerName(playerName);

        chatArea.setText("");
        chatArea.append("=== [" + roomNumber + "] 번 대기방에 입장했습니다 (" + gameType + ") ===\n");

        // ★ resetUI() 절대 호출 금지
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

    public void resetUI() {
        chatArea.setText("");
        chatInput.setText("");
        infoPanel.resetUI();
    }

    public void updateLobbyInfo(String hostName, Map<String, Boolean> playerStatus,
                                String difficulty, String gameMode) {
        infoPanel.updateUI(hostName, playerStatus, difficulty, gameMode);
    }

    public void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}
