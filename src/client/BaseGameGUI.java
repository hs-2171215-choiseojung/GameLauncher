package client;

import model.GamePacket;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseGameGUI extends JFrame {

    protected Socket socket;
    protected ObjectOutputStream out;
    protected ObjectInputStream in;
    protected String playerName;
    protected GameLauncher launcher;
    
    protected String gameMode = "경쟁";

    protected JLabel timerLabel;
    protected JLabel roundLabel;
    protected JTextArea statusArea;
    protected JTextArea scoreArea;
    protected BaseGameBoardPanel gameBoardPanel;

    protected int timeLeft = 120;
    protected Timer swingTimer;
    protected boolean isGameActive = false;
    protected int currentRound = 1;
    protected int totalAnswers = 0;
    protected int globalFoundCount = 0;
    protected int myFoundCount = 0;
    protected int currentTeamScore = 0; // 협동 모드용
    protected int myScore = 0;
    protected int hintsRemaining = 3;
    protected boolean isIntentionalExit = false;
    protected boolean isGameOver = false;
    
    // 경험치 중복 지급 방지 플래그
    protected boolean isExpGiven = false;
    
    protected final Color[] PLAYER_COLORS = { Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE };
    protected Map<String, Integer> playerIndexMap = new HashMap<>();

    // 카운트다운 변수
    protected int countdownValue = 3; 
    protected boolean isCountdownActive = false;
    protected String countdownMessage = "";

    public BaseGameGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                       String playerName, GameLauncher launcher) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.launcher = launcher;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isGameOver) {
                    handleGameExit();
                    return;
                }
                int choice = JOptionPane.showConfirmDialog(
                        BaseGameGUI.this, 
                        "게임을 종료하고 나가시겠습니까?", 
                        "종료 확인", 
                        JOptionPane.YES_NO_OPTION
                );
                if (choice == JOptionPane.YES_OPTION) {
                    handleGameExit();
                }
            }
        });
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        initGlobalKeyBindings();
        
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    protected void initGlobalKeyBindings() {
        JRootPane root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "GLOBAL_EXIT");
        am.put("GLOBAL_EXIT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int choice = JOptionPane.showConfirmDialog(BaseGameGUI.this, 
                        "게임을 종료하고 나가시겠습니까?", "종료 확인", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    handleGameExit();
                }
            }
        });
    }

    protected void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel(getGameTitle());
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        timerLabel = new JLabel("타이머: 대기중", SwingConstants.CENTER); 
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.CENTER);
        topBar.add(roundLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        gameBoardPanel = createGameBoardPanel();
        gameBoardPanel.setPreferredSize(new Dimension(500, 400));
        centerPanel.add(gameBoardPanel, BorderLayout.CENTER);

        centerPanel.add(createRightPanel(), BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    protected abstract String getGameTitle();
    protected abstract BaseGameBoardPanel createGameBoardPanel();
    protected abstract JPanel createBottomPanel();

    protected JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0));

        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);
        rightPanel.add(statusScroll, BorderLayout.CENTER);

        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setRows(4);
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        
        return rightPanel;
    }

    protected void listenFromServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;
                SwingUtilities.invokeLater(() -> handlePacket(p));
            }
        } catch (Exception e) {
        	if (!isIntentionalExit && !isGameOver) {
                SwingUtilities.invokeLater(() -> {
                    if (isVisible()) {
                        appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
                        handleGameExit();
                    }
                });
            }
        }
    }

    protected void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.out.println("패킷 전송 실패: " + e.getMessage());
        }
    }

    protected void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                onRoundStart(p);
                break;
            case TIMER_END:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                appendStatus("\n[시간 종료!]\n");
                break;
            case MESSAGE:
                if (p.getMessage() != null) {
                    if ("SERVER".equals(p.getSender())) {
                        appendStatus("[서버] " + p.getMessage() + "\n");
                    } else {
                        onChatMessage(p.getSender(), p.getMessage());
                    }
                }
                break;
            case GAME_OVER:
                handleGameOver(p);
                break;
            case RESULT:
                handleCommonResult(p);
                break;
            case SCORE:
                handleCommonScore(p);
                break;
            default:
                onPacketReceived(p);
                break;
        }
    }

    protected abstract void onPacketReceived(GamePacket p);
    
    protected void onChatMessage(String sender, String message) {
        appendStatus(sender + ": " + message + "\n");
    }

    protected void onRoundStart(GamePacket p) {
        currentRound = p.getRound();
        roundLabel.setText("라운드 " + currentRound);
        hintsRemaining = p.getRemainingHints();
        
        if (p.getGameMode() != null) {
            this.gameMode = p.getGameMode();
        }
        
        if (p.getPlayerIndexMap() != null) {
            this.playerIndexMap = p.getPlayerIndexMap();
        }
        
        if (currentRound == 1) {
            myScore = 0;
            currentTeamScore = 0;
            myFoundCount = 0;
            globalFoundCount = 0;
            isExpGiven = false; 
        } else {
            globalFoundCount = 0; 
        }
        
        setTitle(getGameTitle());
        
        String imagePath = p.getMessage();
        List<Rectangle> answers = p.getOriginalAnswers();
        Dimension dim = p.getOriginalDimension();

        if (imagePath != null && answers != null && dim != null) {
            totalAnswers = answers.size();
            gameBoardPanel.setRoundData(imagePath, answers, dim);
            appendStatus("=== 라운드 " + currentRound + " 준비 ===\n");
            
            isGameActive = false; 
            runGameStartSequence();
        }

        updateScoreDisplay();
    }
    
    protected void runGameStartSequence() {
        isCountdownActive = true;
        countdownValue = 3;
        countdownMessage = "3";
        gameBoardPanel.repaint();
        
        Timer countdownTimer = new Timer(1000, null);
        countdownTimer.addActionListener(e -> {
            countdownValue--;
            if (countdownValue > 0) {
                countdownMessage = String.valueOf(countdownValue);
                gameBoardPanel.repaint();
            } else if (countdownValue == 0) {
                countdownMessage = "START!";
                gameBoardPanel.repaint();
            } else {
                countdownTimer.stop();
                isCountdownActive = false;
                isGameActive = true; 
                appendStatus("=== 게임 시작! ===\n");
                startCountdownTimer(120);
                gameBoardPanel.repaint();
            }
        });
        countdownTimer.setInitialDelay(1000); 
        countdownTimer.start();
    }
    
    protected void drawCountdown(Graphics2D g2, int w, int h) {
        if (!isCountdownActive) return;

        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRect(0, 0, w, h);

        g2.setFont(new Font("Dialog", Font.BOLD, 80));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(countdownMessage);
        int th = fm.getAscent();

        int x = (w - tw) / 2;
        int y = (h + th) / 2;

        g2.setColor(Color.BLACK);
        g2.drawString(countdownMessage, x + 3, y + 3);

        if (countdownMessage.equals("START!")) {
            g2.setColor(Color.RED);
        } else {
            g2.setColor(Color.YELLOW);
        }
        g2.drawString(countdownMessage, x, y);
    }

    protected void handleGameOver(GamePacket p) {
    	isGameActive = false;
    	isGameOver = true;
    	
        if (swingTimer != null) swingTimer.stop();

        String msg = p.getMessage();

        if (msg != null && msg.startsWith("RANKING:")) {
            JPanel rankPanel = createRankingPanel(msg);
            JOptionPane.showMessageDialog(this, rankPanel, "게임 종료 - 랭킹", JOptionPane.PLAIN_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, msg, "게임 종료", JOptionPane.PLAIN_MESSAGE);
        }

        addExperience(); 
        
        appendStatus("\n3초 뒤 메인 메뉴로 이동합니다...\n");

        Timer exitTimer = new Timer(3000, e -> handleGameExit());
        exitTimer.setRepeats(false);
        exitTimer.start();
    }
    
    protected void addExperience() {
        if (isExpGiven) return;
        
    	int calcScore = 0;
        if ("협동".equals(gameMode)) {
            calcScore = myFoundCount * 10; 
        } else {
            calcScore = myScore;
        }

        int exp = 50 + (calcScore / 2);
        if (exp < 0) exp = 0;

        UserData.getInstance().addExperience(exp);
        appendStatus("[경험치 획득] " + exp + " EXP\n");
        
        isExpGiven = true; 
    }

    private JPanel createRankingPanel(String data) {
        String[] lines = data.substring(8).split("\n");
        boolean isCoop = "협동".equals(gameMode);

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(new Color(240, 240, 240));

        if (isCoop && lines.length > 0) {
            String[] firstLineParts = lines[0].split(",");
            if (firstLineParts.length >= 4) {
                String totalScore = firstLineParts[3]; 
                JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                scorePanel.setBackground(new Color(240, 240, 240));
                JLabel scoreLabel = new JLabel("팀 총점 : " + totalScore + "점");
                scoreLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
                scorePanel.add(scoreLabel);
                container.add(scorePanel);
                container.add(Box.createVerticalStrut(5));
            }
        }

        JPanel listPanel = new JPanel(new GridBagLayout());
        listPanel.setBackground(new Color(240, 240, 240));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 10, 3, 10);

        gbc.gridy = 0;
        addCell(listPanel, "순위", gbc, 0, true, SwingConstants.CENTER);
        gbc.weightx = 1.0;
        addCell(listPanel, "닉네임", gbc, 1, true, SwingConstants.LEFT);
        gbc.weightx = 0.0;
        addCell(listPanel, isCoop ? "기여도" : "개수", gbc, 2, true, SwingConstants.CENTER);
        if (!isCoop) addCell(listPanel, "점수", gbc, 3, true, SwingConstants.RIGHT);

        gbc.gridy++;
        gbc.gridwidth = isCoop ? 3 : 4;
        listPanel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length < 3) continue;

            gbc.gridy++;
            addCell(listPanel, parts[0], gbc, 0, false, SwingConstants.CENTER); 
            addCell(listPanel, parts[1], gbc, 1, false, SwingConstants.LEFT);   
            addCell(listPanel, parts[2], gbc, 2, false, SwingConstants.CENTER); 
            if (!isCoop && parts.length >= 4) {
                addCell(listPanel, parts[3], gbc, 3, false, SwingConstants.RIGHT); 
            }
        }
        
        container.add(listPanel);
        return container;
    }
    
    private void addCell(JPanel panel, String text, GridBagConstraints gbc, int x, boolean isHeader, int alignment) {
        gbc.gridx = x;
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(alignment);
        if (isHeader) {
            label.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        } else {
            label.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        }
        panel.add(label, gbc);
    }
    
    protected void handleCommonResult(GamePacket p) {
        boolean correct = p.isCorrect();
        String sender = p.getSender();
        
        if (correct) {
            int idx = p.getAnswerIndex();
            Point center = new Point(0,0);
            if (gameBoardPanel.originalAnswers != null && idx < gameBoardPanel.originalAnswers.size()) {
                Rectangle r = gameBoardPanel.originalAnswers.get(idx);
                center = new Point(r.x + r.width/2, r.y + r.height/2);
                gameBoardPanel.foundStatus[idx] = true;
            }
            Color markColor = Color.GREEN; 
            if ("경쟁".equals(gameMode)) {
                int pIdx = playerIndexMap.getOrDefault(sender, 0);
                int colorIdx = Math.max(0, Math.min(pIdx, PLAYER_COLORS.length - 1));
                markColor = PLAYER_COLORS[colorIdx];
            }
            
            gameBoardPanel.addMark(center, true, markColor);
            globalFoundCount++;
            if (playerName.equals(p.getSender())) {
                myFoundCount++;
                if (p.getMessage() == null) {
                	myScore += 10;
                }
            }
        } else {
            if ("협동".equals(gameMode) || playerName.equals(p.getSender())) {
                gameBoardPanel.addMark(new Point((int)p.getX(), (int)p.getY()), false, null);
            }
        }
        if (p.getMessage() != null && !p.getMessage().isEmpty()) {
            appendStatus(p.getMessage() + "\n");
        }
        updateScoreDisplay();
    }

    protected void handleCommonScore(GamePacket p) {
        String msg = p.getMessage();
        if (msg == null) return;

        if ("협동".equals(gameMode) && msg.startsWith("SCORE_COOP:")) {
            try {
                currentTeamScore = Integer.parseInt(msg.substring(11).trim());
            } catch (Exception e) {}
        } else if (msg.startsWith("[점수]")) {
            try {
                String[] lines = msg.split("\n");
                for (String line : lines) {
                    if (line.contains(playerName)) {
                        String[] parts = line.split(" : ");
                        if (parts.length > 1) {
                            String scoreStr = parts[1].replaceAll("[^0-9-]", "");
                            if (!scoreStr.isEmpty()) myScore = Integer.parseInt(scoreStr);
                        }
                        break;
                    }
                }
            } catch (Exception e) {}
        }
        updateScoreDisplay();
    }

    protected void handleGameExit() {
        isIntentionalExit = true;
        isGameActive = false;
        if (swingTimer != null) swingTimer.stop();

        try { if (socket != null) socket.close(); } catch (Exception e) {}
        dispose();

        SwingUtilities.invokeLater(() -> {
            if (launcher != null) {
                launcher.setVisible(true);
                launcher.switchToMainMenu();
            } else {
                new GameLauncher();
            }
        });
    }

    protected void startCountdownTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();
        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");
        timerLabel.setForeground(Color.BLACK);

        swingTimer = new Timer(1000, e -> {
            if (isGameActive && timeLeft > 0) {
                timeLeft--;
                timerLabel.setText("타이머: " + timeLeft + "초");
                if (timeLeft <= 30) timerLabel.setForeground(Color.RED);
                
                updateScoreDisplay();
                gameBoardPanel.removeExpiredMarks();

                if (timeLeft <= 0) {
                    ((Timer) e.getSource()).stop();
                    isGameActive = false;
                    sendPacket(new GamePacket(GamePacket.Type.TIMER_END, "TIME_OVER"));
                }
            }
        });
        swingTimer.start();
    }

    protected void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    protected abstract void updateScoreDisplay();

    protected class BaseGameBoardPanel extends JPanel {
        protected Image backgroundImage;
        protected List<Rectangle> originalAnswers;
        protected boolean[] foundStatus;
        protected Dimension originalDimension;
        
        protected final List<GameMark> marks = new ArrayList<>();
        protected final List<HintMark> hints = new ArrayList<>();
        
        protected Timer blinkTimer;
        protected boolean blinkState = true;

        public BaseGameBoardPanel() {
            blinkTimer = new Timer(500, e -> {
                blinkState = !blinkState;
                repaint();
            });
            blinkTimer.start();
        }

        public void setRoundData(String path, List<Rectangle> answers, Dimension dim) {
            this.originalAnswers = answers;
            this.originalDimension = dim;
            this.foundStatus = new boolean[answers.size()];
            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) > 0) {
                    double ratio = (double) dim.height / dim.width;
                    setPreferredSize(new Dimension(500, (int)(500 * ratio)));
                }
            } catch (Exception e) {
                appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }

        public void clearMarks() {
            marks.clear();
            hints.clear();
            repaint();
        }
        
        public void addHint(Point p) {
            hints.add(new HintMark(p));
            repaint();
        }

        public void addMark(Point p, boolean correct, Color color) {
            marks.add(new GameMark(p, correct, color));
            if (correct) {
                hints.removeIf(h -> h.position.distance(p) < 30);
            }
            repaint();
        }

        public void removeExpiredMarks() {
            long now = System.currentTimeMillis();
            if (marks.removeIf(m -> !m.correct && now > m.expiryTime)) {
                repaint();
            }
        }
        
        public Point2D.Double toGameCoords(Point screenPoint) {
            if (originalDimension == null) return new Point2D.Double(0, 0);
            double scale = getScale();
            Point offset = getOffset();
            return new Point2D.Double(
                (screenPoint.x - offset.x) / scale,
                (screenPoint.y - offset.y) / scale
            );
        }

        public int checkHit(double x, double y) {
            if (originalAnswers == null) return -1;
            for (int i = 0; i < originalAnswers.size(); i++) {
                if (foundStatus[i]) continue; 
                if (originalAnswers.get(i).contains(x, y)) {
                    return i; 
                }
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (backgroundImage != null && originalDimension != null) {
                drawBackground(g2);
                drawHints(g2);
                drawMarks(g2);
            } else {
                g2.drawString("로딩 중...", getWidth()/2 - 20, getHeight()/2);
            }
        }
        
        protected double getScale() {
            if (originalDimension == null) return 1.0;
            return Math.min((double) getWidth() / originalDimension.width, 
                            (double) getHeight() / originalDimension.height);
        }
        
        protected Point getOffset() {
            if (originalDimension == null) return new Point(0,0);
            double scale = getScale();
            int w = (int)(originalDimension.width * scale);
            int h = (int)(originalDimension.height * scale);
            return new Point((getWidth() - w)/2, (getHeight() - h)/2);
        }

        protected void drawBackground(Graphics2D g2) {
            double scale = getScale();
            Point offset = getOffset();
            g2.drawImage(backgroundImage, offset.x, offset.y, 
                        (int)(originalDimension.width * scale), 
                        (int)(originalDimension.height * scale), this);
        }

        protected void drawHints(Graphics2D g2) {
            if (!blinkState) return;
            double scale = getScale();
            Point offset = getOffset();
            
            for (HintMark hint : hints) {
                int x = (int)(offset.x + hint.position.x * scale);
                int y = (int)(offset.y + hint.position.y * scale);
                g2.setColor(new Color(255, 255, 0, 200));
                g2.setStroke(new BasicStroke(4));
                g2.drawOval(x - 25, y - 25, 50, 50);
                g2.setFont(new Font("Dialog", Font.BOLD, 30));
                g2.drawString("★", x - 15, y + 10);
            }
        }

        protected void drawMarks(Graphics2D g2) {
            double scale = getScale();
            Point offset = getOffset();
            
            for (GameMark m : marks) {
                int x = (int)(offset.x + m.p.x * scale);
                int y = (int)(offset.y + m.p.y * scale);
                
                if (m.correct) {
                    g2.setColor(m.color != null ? m.color : Color.GREEN);
                    g2.setStroke(new BasicStroke(3));
                    g2.drawOval(x - 20, y - 20, 40, 40);
                } else {
                    g2.setColor(Color.RED);
                    g2.setFont(new Font("Arial", Font.BOLD, 28));
                    g2.drawString("X", x - 10, y + 10);
                }
            }
        }

        protected class GameMark {
            Point p;
            boolean correct;
            Color color;
            long expiryTime;
            GameMark(Point p, boolean c, Color col) {
                this.p = p; this.correct = c; this.color = col;
                this.expiryTime = c ? -1 : System.currentTimeMillis() + 1000;
            }
        }
        protected class HintMark {
            Point position;
            HintMark(Point p) { this.position = p; }
        }
    }
}