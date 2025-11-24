package client;

import model.GamePacket;
import model.UserData;

import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class HiddenObjectClientGUI extends JFrame {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private GameLauncher launcher;

    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea chatArea;
    private JTextArea scoreArea;
    private JTextField inputField;
    private GameBoardPanel gameBoardPanel;

    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;

    private String gameMode;
    private int playerIndex;
    private Map<String, Integer> playerIndexMap;
    private Image[] cursorImages;
    private Image singleCursorImage;
    private Map<String, Point2D.Double> otherPlayerCursor = new HashMap<>();

    private Point myMousePoint = new Point(-100, -100);

    
    private int roundHintsRemaining = 3;

   
    private final Map<Integer, String> emotes = new HashMap<>();

    public HiddenObjectClientGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                                 String playerName, String difficulty, GamePacket roundStartPacket,
                                 GameLauncher launcher) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;
        this.launcher = launcher;

        this.playerIndexMap = roundStartPacket.getPlayerIndexMap();
        this.gameMode = roundStartPacket.getGameMode();
        this.playerIndex = playerIndexMap.getOrDefault(playerName, 0);

        loadCursorImages();

       
        emotes.put(1, "화이팅!");
        emotes.put(2, "좋아요!");
        emotes.put(3, "힘내요!");
        emotes.put(4, "GG!");

        setTitle("숨은 그림 찾기 (플레이어: " + playerName + ")");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleGameExit();
            }
        });
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        setLocalCursor();
        setupKeyBindings();

        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();

       
        handlePacket(roundStartPacket);

        pack();
        setResizable(false);
        setVisible(true);
    }

    private void loadCursorImages() {
        cursorImages = new Image[5];
        Toolkit tk = Toolkit.getDefaultToolkit();
        try {
            for (int i = 0; i < 5; i++) {
                cursorImages[i] = tk.getImage("images/cursor" + (i + 1) + ".png");
            }
            singleCursorImage = tk.getImage("images/singleMouse.png");
        } catch (Exception e) {
            System.out.println("커서 이미지 로드 실패: " + e.getMessage());
        }
    }

    private void setLocalCursor() {
        if (gameBoardPanel == null) return;

        Toolkit tk = Toolkit.getDefaultToolkit();
        Image transparentImage = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Cursor invisibleCursor = tk.createCustomCursor(transparentImage, new Point(0, 0), "InvisibleCursor");
        gameBoardPanel.setCursor(invisibleCursor);
    }

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("숨은 그림 찾기");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        timerLabel = new JLabel("타이머: 120초", SwingConstants.CENTER);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        JButton helpButton = new JButton("도움말");
        helpButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        helpButton.addActionListener(e -> showHelpDialog());

        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.CENTER);
        topBar.add(roundLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        gameBoardPanel = new GameBoardPanel();
        gameBoardPanel.setPreferredSize(new Dimension(500, 400));
        centerPanel.add(gameBoardPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0));

        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);

        chatArea = new JTextArea("[채팅창]\n");
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusScroll, chatScroll);
        splitPane.setResizeWeight(0.5);
        splitPane.setEnabled(false);
        splitPane.setDividerLocation(0.5);
        rightPanel.add(splitPane, BorderLayout.CENTER);

        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("힌트: 3/3 (공유)\n");
        scoreArea.setRows(4);
        rightPanel.add(scoreArea, BorderLayout.SOUTH);

        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel hintLabel = new JLabel("/H : 도움말   ESC : 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomBar.add(hintLabel, BorderLayout.WEST);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        JButton sendButton = new JButton("전송");
        sendButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);

        inputField.addActionListener(e -> sendChat());
        sendButton.addActionListener(e -> sendChat());

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('H'), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "EXIT");
        root.getActionMap().put("EXIT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleGameExit();
            }
        });
    }

    private void handleGameExit() {
        isGameActive = false;
        if (swingTimer != null) {
            swingTimer.stop();
            swingTimer = null;
        }
        this.dispose();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ex) {
            System.out.println("소켓 종료 중 오류: " + ex.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            if (launcher != null && launcher.isDisplayable()) {
                launcher.setVisible(true);
                launcher.switchToMainMenu();
            } else {
                new GameLauncher();
            }
        });
    }

    private void listenFromServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;
                SwingUtilities.invokeLater(() -> handlePacket(p));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                if (this.isVisible()) {
                    appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
                    JOptionPane.showMessageDialog(this, "서버 연결이 끊겼습니다.");
                    this.dispose();
                    try {
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                    } catch (Exception ignored) {}
                    SwingUtilities.invokeLater(() -> {
                        if (launcher != null && launcher.isDisplayable()) {
                            launcher.setVisible(true);
                            launcher.switchToMainMenu();
                        } else {
                            new GameLauncher();
                        }
                    });
                }
            });
        }
    }

    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
            }
        } catch (IOException e) {
            appendStatus("[에러] 패킷 전송 실패: " + e.getMessage() + "\n");
        }
    }

    private void sendChat() {
        String raw = inputField.getText().trim();
        if (raw.isEmpty()) return;

        String text = raw;

        // 1) 빠른 채팅 (/1 ~ /4)
        if (raw.startsWith("/") && raw.length() > 1) {
            try {
                int num = Integer.parseInt(raw.substring(1));
                if (emotes.containsKey(num)) {
                    text = emotes.get(num);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2) 힌트 명령 (/Q)
        if (raw.equalsIgnoreCase("/Q")) {
            if (roundHintsRemaining <= 0) {
                appendStatus("[힌트] 이번 라운드의 모든 힌트를 사용했습니다.\n");
                inputField.setText("");
                return;
            }
            sendPacket(new GamePacket(GamePacket.Type.HINT_REQUEST, playerName, "HINT"));
            inputField.setText("");
            return;
        }

        // 3) 도움말 명령 (/H)
        if (raw.equalsIgnoreCase("/H")) {
            showHelpDialog();
            inputField.setText("");
            return;
        }

        // 4) 일반 채팅
        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, text));
        inputField.setText("");
    }

    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                roundLabel.setText("라운드 " + p.getRound());
             
                int total = p.getOriginalAnswers().size();

                roundHintsRemaining = p.getRemainingHints();
                updateHintDisplay();

                String imagePath = p.getMessage();
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();

                this.playerIndexMap = p.getPlayerIndexMap();
                this.playerIndex = playerIndexMap.getOrDefault(playerName, 0);

                if (imagePath != null && !imagePath.isEmpty()
                        && originalAnswers != null && originalDimension != null) {
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("=== 라운드 " + p.getRound() + " 시작 ===\n");
                    appendStatus("[목표] 숨은 그림 " + total + "개를 모두 찾으세요!\n");
                    isGameActive = true;
                } else {
                    appendStatus("[시스템] " + p.getMessage() + "\n");
                }

                gameBoardPanel.clearMarks();
                startCountdownTimer(120);
                otherPlayerCursor.clear();
                break;

            case MOUSE_MOVE:
                if (p.getSender().equals(playerName)) return;
                otherPlayerCursor.put(p.getSender(), new Point2D.Double(p.getX(), p.getY()));
                gameBoardPanel.repaint();
                break;

            case RESULT:
                if (p.isCorrect()) {
                    gameBoardPanel.addMark(p.getAnswerIndex(), true, p.getSender());
                    appendStatus("[정답] 숨은 그림을 찾았습니다!\n");
                } else {
                    gameBoardPanel.addMark(-1, false, p.getSender());
                    appendStatus("[오답] 해당 위치에는 숨은 그림이 없습니다.\n");
                }
                break;


            case SCORE:
                if ("경쟁".equals(gameMode)) {
                    
                    String text = p.getMessage();
                    scoreArea.setText(text);  
                } else {
                   
                    String scoreText = p.getMessage();
                    if (scoreText != null) {
                        scoreArea.setText(scoreText);
                        updateHintDisplay();  
                    }
                }
                break;


            case HINT_RESPONSE:
                Point hintPos = p.getHintPosition();
                roundHintsRemaining = p.getRemainingHints();

                if (hintPos != null) {
                    gameBoardPanel.addHint(hintPos);
                    appendStatus(p.getMessage() + "\n");
                } else {
                    appendStatus("[힌트] " + p.getMessage() + "\n");
                }
                updateHintDisplay();
                break;

            case MESSAGE:
                if (p.getMessage() != null) {
                    if ("SERVER".equals(p.getSender())) {
                        appendStatus("[서버] " + p.getMessage() + "\n");
                    } else {
                        appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                    }
                }
                break;

            case LOBBY_UPDATE:
                appendStatus("방장: " + p.getHostName() + ", 난이도: "
                        + p.getDifficulty() + ", 모드: " + p.getGameMode() + "\n");
                break;

            case TIMER_END:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                timerLabel.setText("타이머: 0초");
                timerLabel.setForeground(Color.RED);
                appendStatus("[시스템] " + p.getMessage() + "\n");
                break;

            case GAME_OVER:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                appendStatus("[시스템] 게임이 종료되었습니다.\n");
                if (p.getMessage() != null) {
                    appendStatus(p.getMessage() + "\n");
                }

                UserData userData = UserData.getInstance();
                if (userData != null) {
                    int expGain = 30;
                    userData.addExperience(expGain);
                    appendStatus("[경험치 획득: " + expGain + " EXP]\n");
                }

                Timer exitTimer = new Timer(3000, e -> {
                    this.dispose();
                    try {
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                    } catch (Exception ex) {
                        System.out.println("소켓 종료 중 오류: " + ex.getMessage());
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (launcher != null && launcher.isDisplayable()) {
                            launcher.setVisible(true);
                            launcher.switchToMainMenu();
                        } else {
                            new GameLauncher();
                        }
                    });
                });
                exitTimer.setRepeats(false);
                exitTimer.start();
                break;

            default:
                break;
        }
    }

    private void updateHintDisplay() {
        String current = scoreArea.getText();
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (!line.startsWith("힌트:")) {
                sb.append(line).append("\n");
            }
        }
        sb.append("힌트: ").append(roundHintsRemaining).append("/3 (공유)\n");
        scoreArea.setText(sb.toString());
    }

    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void startCountdownTimer(int seconds) {
        if (swingTimer != null) swingTimer.stop();

        timeLeft = seconds;
        timerLabel.setText("타이머: " + timeLeft + "초");
        timerLabel.setForeground(Color.BLACK);

        swingTimer = new Timer(1000, e -> {
            if (isGameActive && timeLeft > 0) {
                timeLeft--;
                timerLabel.setText("타이머: " + timeLeft + "초");
                if (timeLeft <= 30) {
                    int red = 255;
                    int green = Math.max(0, 200 - (30 - timeLeft) * 7);
                    timerLabel.setForeground(new Color(red, green, 0));
                }
                gameBoardPanel.removeExpiredMarks();

                if (timeLeft <= 0) {
                    ((Timer) e.getSource()).stop();
                    isGameActive = false;
                    sendPacket(new GamePacket(GamePacket.Type.TIMER_END, "타이머 종료"));
                    appendStatus("\n[시간 종료!]\n");
                }
            } else if (!isGameActive) {
                ((Timer) e.getSource()).stop();
            }
        });
        swingTimer.start();
    }

    private void showHelpDialog() {
        JOptionPane.showMessageDialog(
                this,
                "🎮 숨은 그림 찾기 게임 방법\n\n"
                        + "✔ 마우스로 숨은 그림을 클릭해서 찾으세요.\n"
                        + "✔ 힌트는 한 라운드에 최대 3번까지 사용 가능 (/Q)\n"
                        + "✔ /1~4 : 빠른 채팅\n"
                        + "✔ ESC : 게임 종료\n\n"
                        + "⌨ 기본 조작\n"
                        + " - 마우스 이동 : 커서 이동\n"
                        + " - 마우스 클릭 : 정답 체크\n"
                        + " - /Q 입력 : 힌트 요청\n"
                        + " - /H 키 : 도움말 열기\n"
                        + " - ESC : 종료",
                "도움말",
                JOptionPane.INFORMATION_MESSAGE
        );
    }


    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private boolean[] foundStatus;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private final List<HintMark> hints = new ArrayList<>();
        private Timer blinkTimer;
        private boolean blinkState = true;
        private static final int RADIUS = 20;

        private final Color[] PLAYER_COLORS = {
                Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE
        };

        public GameBoardPanel() {
            blinkTimer = new Timer(500, e -> {
                blinkState = !blinkState;
                repaint();
            });
            blinkTimer.start();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isGameActive || timeLeft <= 0 || backgroundImage == null || originalDimension == null) return;

                    myMousePoint = e.getPoint();
                    repaint();

                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = originalDimension.width;
                    int imgH = originalDimension.height;

                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int offsetX = (panelW - drawW) / 2;
                    int offsetY = (panelH - drawH) / 2;

                    double originalX = (e.getX() - offsetX) / scale;
                    double originalY = (e.getY() - offsetY) / scale;

                    int foundIndex = -1;
                    for (int i = 0; i < originalAnswers.size(); i++) {
                        if (originalAnswers.get(i).contains(originalX, originalY) && !foundStatus[i]) {
                            foundIndex = i;
                            break;
                        }
                    }

                    if (foundIndex != -1) {
                     
                        sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                    } else {
                        
                        GamePacket missPacket = new GamePacket(
                                GamePacket.Type.CLICK,
                                playerName,
                                -1
                        );
                        missPacket.setX(originalX);
                        missPacket.setY(originalY);
                        sendPacket(missPacket);
                    }

                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    myMousePoint = e.getPoint();
                    repaint();

                    if (!isGameActive || backgroundImage == null || originalDimension == null) return;

                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = originalDimension.width;
                    int imgH = originalDimension.height;
                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) (imgW * scale);
                    int drawH = (int) (imgH * scale);
                    int offsetX = (panelW - drawW) / 2;
                    int offsetY = (panelH - drawH) / 2;

                    double originalX = (e.getX() - offsetX) / scale;
                    double originalY = (e.getY() - offsetY) / scale;

                    sendPacket(new GamePacket(
                            GamePacket.Type.MOUSE_MOVE,
                            playerName,
                            playerIndex,
                            originalX,
                            originalY
                    ));
                }
            });
        }
        
        public void addMissMark(double ox, double oy, String sender) {
            int idx = playerIndexMap.getOrDefault(sender, 0);

            marks.add(new GameMark(
                    new Point((int) ox, (int) oy),
                    false,
                    idx
            ));
            repaint();
        }


        public void addHint(Point hintPos) {
            hints.add(new HintMark(hintPos));
            repaint();
        }

        public void setRoundData(String path, List<Rectangle> originalAnswers, Dimension originalDimension) {
            this.originalAnswers = originalAnswers;
            this.originalDimension = originalDimension;
            this.foundStatus = new boolean[originalAnswers.size()];

            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) == -1) {
                    throw new IOException("이미지 로드 실패");
                }

                int imgWidth = originalDimension.width;
                int imgHeight = originalDimension.height;
                int baseWidth = 500;
                double ratio = (double) imgHeight / imgWidth;
                int newHeight = (int) (baseWidth * ratio);
                setPreferredSize(new Dimension(baseWidth, newHeight));

            } catch (Exception e) {
                e.printStackTrace();
                backgroundImage = null;
                HiddenObjectClientGUI.this.appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }

        public void clearMarks() {
            marks.clear();
            hints.clear();
            repaint();
        }

    
        public void addMark(int answerIndex, boolean correct, String senderName) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) {
                return;
            }

            Rectangle r = originalAnswers.get(answerIndex);
            Point center = new Point(r.x + r.width / 2, r.y + r.height / 2);

            int correctPlayerIdx = playerIndexMap.getOrDefault(senderName, 0);
            marks.add(new GameMark(center, correct, correctPlayerIdx));

            if (correct) {
                foundStatus[answerIndex] = true;
                hints.removeIf(h -> h.position.distance(center) < 30);
            }

            repaint();
        }

        public void removeExpiredMarks() {
            long now = System.currentTimeMillis();
            if (marks.removeIf(m -> !m.correct && now > m.expiryTime)) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int panelW = getWidth();
            int panelH = getHeight();

            if (backgroundImage != null && originalDimension != null) {
                int imgW = originalDimension.width;
                int imgH = originalDimension.height;
                double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);
                int offsetX = (panelW - drawW) / 2;
                int offsetY = (panelH - drawH) / 2;

                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);

                // 힌트 반짝임
                if (blinkState) {
                    for (HintMark hint : hints) {
                        int hintX = (int) (offsetX + hint.position.x * scale);
                        int hintY = (int) (offsetY + hint.position.y * scale);

                        g2.setColor(new Color(255, 255, 0, 200));
                        g2.setStroke(new BasicStroke(4));
                        g2.draw(new Ellipse2D.Double(
                                hintX - RADIUS - 5, hintY - RADIUS - 5,
                                (RADIUS + 5) * 2, (RADIUS + 5) * 2
                        ));

                        g2.setColor(Color.YELLOW);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 30));
                        g2.drawString("★", hintX - 15, hintY + 10);
                    }
                }

                // 정답/오답 마크
                for (GameMark m : marks) {
                    int drawX = (int) (offsetX + m.p.x * scale);
                    int drawY = (int) (offsetY + m.p.y * scale);

                    if (m.correct) {
                        if ("경쟁".equals(gameMode)) {
                            int idx = Math.max(0, Math.min(m.correctPlayer, 4));
                            g2.setColor(PLAYER_COLORS[idx]);
                        } else {
                            g2.setColor(new Color(0, 255, 0, 180));
                        }
                        g2.setStroke(new BasicStroke(3));
                        g2.draw(new Ellipse2D.Double(
                                drawX - RADIUS, drawY - RADIUS,
                                RADIUS * 2, RADIUS * 2
                        ));
                    } else {
                        g2.setColor(Color.RED);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 28));
                        g2.drawString("X", drawX - 10, drawY + 10);
                    }
                }

               
                if ("협동".equals(gameMode) && playerIndexMap.size() > 1) {
                    for (Map.Entry<String, Point2D.Double> entry : otherPlayerCursor.entrySet()) {
                        String name = entry.getKey();
                        Point2D.Double p = entry.getValue();

                        int drawX = (int) (offsetX + p.x * scale);
                        int drawY = (int) (offsetY + p.y * scale);

                        int idx = playerIndexMap.getOrDefault(name, 0);
                        if (idx >= 0 && idx < 5 && cursorImages[idx] != null) {
                            g2.drawImage(cursorImages[idx], drawX, drawY, 30, 30, HiddenObjectClientGUI.this);
                            g2.setColor(Color.WHITE);
                            g2.setFont(new Font("Dialog", Font.BOLD, 10));
                            g2.drawString(name, drawX, drawY);
                        }
                    }
                }

               
                Image myImg = (playerIndexMap.size() <= 1) ? singleCursorImage :
                        cursorImages[Math.max(0, Math.min(playerIndex, 4))];
                if (myImg != null && myMousePoint.x > -50 && myMousePoint.y > -50) {
                    g2.drawImage(myImg, myMousePoint.x, myMousePoint.y, 30, 30, HiddenObjectClientGUI.this);
                }
            }
        }

        class GameMark {
            Point p;
            boolean correct;
            long expiryTime;
            int correctPlayer;

            GameMark(Point centerPoint, boolean correct, int correctPlayer) {
                this.p = centerPoint;       
                this.correct = correct;
                this.correctPlayer = correctPlayer;
                this.expiryTime = correct ? -1 : System.currentTimeMillis() + 5000;
            }
        }

        class HintMark {
            Point position;
            HintMark(Point p) { this.position = p; }
        }
    }
}
