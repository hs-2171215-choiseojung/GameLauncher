package client;

import model.GamePacket;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;

public class InfoPanel extends JPanel {

    private GameLauncher launcher;
    private String playerName;

    // NORMAL / FLASH 구분
    private final String gameType;

    // UI 컴포넌트
    private JComboBox<String> difficultyCombo;
    private JRadioButton coopRadio;
    private JRadioButton pvpRadio;
    private JButton startButton;
    private JButton readyButton;
    private JTextArea playerListArea;

    private boolean isHost = false;
    private boolean isReady = false;

    private ActionListener settingsListener;

    public InfoPanel(GameLauncher launcher) {
        this(launcher, "NORMAL");
    }

    public InfoPanel(GameLauncher launcher, String gameType) {
        this.launcher = launcher;
        this.gameType = gameType;

        setLayout(new BorderLayout(5, 5));

        // 플레이어 목록
        playerListArea = new JTextArea("플레이어:\n");
        playerListArea.setEditable(false);
        playerListArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(playerListArea);
        scroll.setPreferredSize(new Dimension(200, 150));
        add(scroll, BorderLayout.NORTH);

        // 설정 패널
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("게임 설정"));

        settingsPanel.add(new JLabel("난이도 선택:"));
        difficultyCombo = new JComboBox<>(new String[]{"쉬움", "보통", "어려움"});
        difficultyCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
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

        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        readyButton = new JButton("게임 준비");
        startButton = new JButton("게임 시작");

        // 기본값 → 게스트 기준
        readyButton.setVisible(true);
        readyButton.setEnabled(true);

        startButton.setVisible(false);
        startButton.setEnabled(false);

        difficultyCombo.setEnabled(false);
        coopRadio.setEnabled(false);
        pvpRadio.setEnabled(false);

        settingsPanel.add(readyButton);
        settingsPanel.add(startButton);

        add(settingsPanel, BorderLayout.CENTER);

        // 리스너 설정
        readyButton.addActionListener(e -> toggleReady());

        startButton.addActionListener(e -> {
            String difficulty = (String) difficultyCombo.getSelectedItem();
            String mode = coopRadio.isSelected() ? "협동" : "경쟁";
            launcher.requestStartGame(difficulty, mode, gameType);

            isReady = false;
            readyButton.setText("게임 준비");
            readyButton.setBackground(Color.LIGHT_GRAY);
        });

        settingsListener = e -> sendSettingsUpdate();
        difficultyCombo.addActionListener(settingsListener);
        coopRadio.addActionListener(settingsListener);
        pvpRadio.addActionListener(settingsListener);
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    private void toggleReady() {
        isReady = !isReady;
        readyButton.setText(isReady ? "준비 완료" : "게임 준비");
        readyButton.setBackground(isReady ? Color.GREEN : Color.LIGHT_GRAY);

        launcher.sendPacket(new GamePacket(GamePacket.Type.READY_STATUS, playerName, isReady));
    }

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

    public void resetUI() {
        isReady = false;
        isHost = false;

        readyButton.setText("게임 준비");
        readyButton.setVisible(true);
        readyButton.setBackground(Color.LIGHT_GRAY);

        startButton.setVisible(false);
        startButton.setEnabled(false);

        difficultyCombo.setEnabled(false);
        coopRadio.setEnabled(false);
        pvpRadio.setEnabled(false);

        playerListArea.setText("플레이어:\n");
    }

    public void updateUI(String hostName,
                         Map<String, Boolean> playerStatus,
                         String difficulty,
                         String gameMode) {

        
        // 🔥 방장 판단
        isHost = hostName != null && hostName.equals(playerName);

        // 플레이어 목록 갱신
        StringBuilder sb = new StringBuilder("플레이어 목록\n");
        for (Map.Entry<String, Boolean> entry : playerStatus.entrySet()) {
            String name = entry.getKey();
            boolean ready = entry.getValue();

            sb.append(name);
            if (name.equals(hostName)) sb.append(" (방장)");
            else sb.append(ready ? " (준비됨)" : " (대기중)");
            sb.append("\n");
        }
        playerListArea.setText(sb.toString());

        // 방장 / 게스트 UI 처리
        if (isHost) {
            // 방장 UI
            readyButton.setVisible(false);

            startButton.setVisible(true);
            startButton.setEnabled(false);

            difficultyCombo.setEnabled(true);
            coopRadio.setEnabled(true);
            pvpRadio.setEnabled(true);

            boolean allReady = true;
            for (Map.Entry<String, Boolean> entry : playerStatus.entrySet()) {
                if (!entry.getKey().equals(hostName) && !entry.getValue()) {
                    allReady = false;
                }
            }
            startButton.setEnabled(allReady && playerStatus.size() >= 2);

        } else {
            // 게스트 UI
            readyButton.setVisible(true);
            startButton.setVisible(false);

            difficultyCombo.setEnabled(false);
            coopRadio.setEnabled(false);
            pvpRadio.setEnabled(false);
        }

        // 설정값 반영
        difficultyCombo.removeActionListener(settingsListener);
        coopRadio.removeActionListener(settingsListener);
        pvpRadio.removeActionListener(settingsListener);

        difficultyCombo.setSelectedItem(difficulty);
        if ("협동".equals(gameMode)) coopRadio.setSelected(true);
        else pvpRadio.setSelected(true);

        if (isHost) {
            difficultyCombo.addActionListener(settingsListener);
            coopRadio.addActionListener(settingsListener);
            pvpRadio.addActionListener(settingsListener);
        }
    }
}
