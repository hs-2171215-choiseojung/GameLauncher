package client;

import model.GamePacket;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;

// (신규) 대기방의 오른쪽 정보/설정 패널

public class InfoPanel extends JPanel {
    
    private GameLauncher launcher;
    private String playerName;

    // UI 컴포넌트
    private JComboBox<String> difficultyCombo;
    private JRadioButton coopRadio;
    private JRadioButton pvpRadio;
    private JButton startButton;
    private JButton readyButton; 
    private JTextArea playerListArea; 
    
    private boolean isHost = false; 
    private boolean isReady = false; 
    
    private ActionListener settingsListener; // 무한 루프 방지용

    public InfoPanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(5, 5));

        // 1. 플레이어 목록
        playerListArea = new JTextArea("플레이어:\n");
        playerListArea.setEditable(false);
        playerListArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        playerListArea.setBackground(Color.WHITE);
        JScrollPane playerScroll = new JScrollPane(playerListArea);
        playerScroll.setPreferredSize(new Dimension(200, 150)); 
        add(playerScroll, BorderLayout.NORTH);

        // 2. 게임 설정
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("게임 설정"));
        
        settingsPanel.add(new JLabel("난이도 선택:"));
        String[] difficulties = {"쉬움", "보통", "어려움"};
        difficultyCombo = new JComboBox<>(difficulties);
        difficultyCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, difficultyCombo.getPreferredSize().height));
        settingsPanel.add(difficultyCombo);

        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10))); 

        settingsPanel.add(new JLabel("게임 모드:"));
        coopRadio = new JRadioButton("협동", true); 
        pvpRadio = new JRadioButton("경쟁");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(coopRadio);
        modeGroup.add(pvpRadio);
        settingsPanel.add(coopRadio);
        settingsPanel.add(pvpRadio);

        settingsPanel.add(Box.createVerticalGlue()); 

        // 3. 준비/시작 버튼
        readyButton = new JButton("게임 준비");
        readyButton.setBackground(Color.LIGHT_GRAY);
        settingsPanel.add(readyButton);

        startButton = new JButton("게임 시작");
        startButton.setBackground(Color.WHITE);
        settingsPanel.add(startButton);
        
        add(settingsPanel, BorderLayout.CENTER);

        // --- 리스너 ---
        readyButton.addActionListener(e -> toggleReady());

        startButton.addActionListener(e -> {
            String difficulty = (String) difficultyCombo.getSelectedItem();
            String mode = coopRadio.isSelected() ? "협동" : "경쟁";
            launcher.requestStartGame(difficulty, mode);
        });
        
        settingsListener = e -> sendSettingsUpdate();
        difficultyCombo.addActionListener(settingsListener);
        coopRadio.addActionListener(settingsListener);
        pvpRadio.addActionListener(settingsListener);
    }

    // WatingRoom이 호출
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    // 준비/준비해제 토글
    private void toggleReady() {
        isReady = !isReady;
        readyButton.setText(isReady ? "준비 완료" : "게임 준비");
        readyButton.setBackground(isReady ? Color.GREEN : Color.LIGHT_GRAY);
        
        launcher.sendPacket(new GamePacket(GamePacket.Type.READY_STATUS, playerName, isReady));
    }
    
    // 방장이 설정 변경 시 서버로 전송
    private void sendSettingsUpdate() {
        if (!isHost) return; 
        
        String difficulty = (String) difficultyCombo.getSelectedItem();
        String mode = coopRadio.isSelected() ? "협동" : "경쟁";
        
        launcher.sendPacket(new GamePacket(
            GamePacket.Type.SETTINGS_UPDATE, 
            playerName, 
            difficulty, 
            mode
        ));
    }
    
    // LOBBY_UPDATE 패킷 수신 시 UI 갱신 (WatingRoom이 호출)
    public void updateUI(String hostName, Map<String, Boolean> playerStatus, String difficulty, String gameMode) {
        if (playerName == null) return; // 아직 설정 안됨
        
        isHost = playerName.equals(hostName);

        // 1. 플레이어 목록 갱신
        StringBuilder sb = new StringBuilder("플레이어 목록\n");
        for (Map.Entry<String, Boolean> entry : playerStatus.entrySet()) {
            String name = entry.getKey();
            boolean ready = entry.getValue();
            
            sb.append(name).append("");
            if (name.equals(hostName)) {
                sb.append("(방장)\n");
            } else {
                sb.append(ready ? "(준비됨)\n " : "(준비안됨)\n");
            }
            
        }
        playerListArea.setText(sb.toString());

        // 2. 방장/참여자 UI 구분
        if (isHost) {
            difficultyCombo.setEnabled(true);
            coopRadio.setEnabled(true);
            pvpRadio.setEnabled(true);
            readyButton.setVisible(false); 
            startButton.setVisible(true);
            
            boolean allReady = true;
            for (Map.Entry<String, Boolean> entry : playerStatus.entrySet()) {
                if (!entry.getKey().equals(hostName) && !entry.getValue()) {
                    allReady = false; 
                    break;
                }
            }
            startButton.setEnabled(allReady && playerStatus.size() >= 1); 

        } else {
            difficultyCombo.setEnabled(false);
            coopRadio.setEnabled(false);
            pvpRadio.setEnabled(false);
            readyButton.setVisible(true);
            startButton.setVisible(false);
        }
        
        // 3. 무한 루프 방지하며 UI 동기화
        difficultyCombo.removeActionListener(settingsListener);
        coopRadio.removeActionListener(settingsListener);
        pvpRadio.removeActionListener(settingsListener);
        
        difficultyCombo.setSelectedItem(difficulty);
        if (gameMode.equals("협동")) {
            coopRadio.setSelected(true);
        } else {
            pvpRadio.setSelected(true);
        }
        
        if (isHost) {
            difficultyCombo.addActionListener(settingsListener);
            coopRadio.addActionListener(settingsListener);
            pvpRadio.addActionListener(settingsListener);
        }
    }
}