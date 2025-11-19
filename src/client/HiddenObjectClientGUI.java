package client;

import model.GamePacket;
import model.UserData;
import javax.swing.*;

import client.SinglePlayerGUI.GameBoardPanel.GameMark;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 실제 '게임 화면'을 담당, GameLauncher에 의해 실행됨.
public class HiddenObjectClientGUI extends JFrame {

    // --- 통신 관련 ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private GameLauncher launcher;

    // --- UI 컴포넌트 ---
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea chatArea;
    private JTextArea scoreArea;
    private JTextField inputField;
    private GameBoardPanel gameBoardPanel;

    // --- 게임 상태 ---
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
        
        // 리스너 스레드 즉시 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        // 전달받은 ROUND_START 패킷으로 즉시 1라운드 시작
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
                cursorImages[i] = tk.getImage("images/mouse" + (i + 1) + ".png");
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

    // UI 구성
    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("숨은 그림 찾기");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20)); // 24 -> 20
        timerLabel = new JLabel("타이머: 120초", SwingConstants.CENTER);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18)); // 20 -> 18
        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18)); // 20 -> 18
        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.CENTER);
        topBar.add(roundLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        gameBoardPanel = new GameBoardPanel();
        gameBoardPanel.setPreferredSize(new Dimension(500, 400)); // 600x450 -> 500x400
        centerPanel.add(gameBoardPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0)); // 220 -> 200
        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 12 -> 11
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);
        chatArea = new JTextArea("[채팅창]\n");
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 12 -> 11
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
        scoreArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12)); // 13 -> 12
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("A 플레이어 : 0점\nB 플레이어 : 0점\n");
        scoreArea.setRows(3); 
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel hintLabel = new JLabel("Q: 힌트     H: 도움말     ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 12 -> 11
        bottomBar.add(hintLabel, BorderLayout.WEST);
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        JButton sendButton = new JButton("전송");
        sendButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 폰트 크기 축소
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);
        inputField.addActionListener(e -> sendChat());
        sendButton.addActionListener(e -> sendChat());
        add(bottomBar, BorderLayout.SOUTH);
    }
    
    // 키 바인딩
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('Q'), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isGameActive) return;
                GamePacket p = new GamePacket(
                        GamePacket.Type.MESSAGE,
                        playerName,
                        "[힌트 요청]"
                );
                sendPacket(p);
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('H'), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(
                        HiddenObjectClientGUI.this,
                        "Q: 힌트 요청\nH: 도움말 보기\nESC: 게임 종료\n\n" +
                        "메시지 입력창에 채팅을 입력하면 아래 채팅창에 표시됩니다."
                );
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

    // 게임 종료 처리
    private void handleGameExit() {
        isGameActive = false;
        if (swingTimer != null) swingTimer.stop();
        
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

    // 인게임 리스너
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
                    } catch (Exception ex) {
                        // 무시
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
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        GamePacket chatPacket = new GamePacket(
                GamePacket.Type.MESSAGE,
                playerName,
                text
        );
        sendPacket(chatPacket);
        inputField.setText("");
    }
    
    // 인게임 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                roundLabel.setText("라운드 " + p.getRound());
                String imagePath = p.getMessage(); 
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();
                
                this.playerIndexMap = p.getPlayerIndexMap();
                this.playerIndex = playerIndexMap.getOrDefault(playerName, 0);
                
                if (imagePath != null && !imagePath.isEmpty() && originalAnswers != null && originalDimension != null) {
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("[시스템] 라운드 " + p.getRound() + " 시작!\n");
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
                gameBoardPanel.addMark(p.getAnswerIndex(), p.isCorrect(), p.getSender());
                if (p.getMessage() != null) {
                    appendStatus(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;
            case SCORE:
                scoreArea.setText(p.getMessage());
                break;
            case MESSAGE:
                appendChat(p.getSender() + ": " + p.getMessage() + "\n");
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
                
                // 경험치 추가
                UserData userData = UserData.getInstance();
                if (userData != null) {
                    int expGain = 30;
                    userData.addExperience(expGain);
                    appendStatus("[경험치 획득: " + expGain + " EXP]\n");
                }
                
                // 게임 종료 후 3초 뒤 런처로 복귀
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
            case LOBBY_UPDATE:
                break;
            case JOIN:
            default:
                break;
        }
    }
    
    // 로그 출력
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }
    
    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 타이머
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
                }
            } else if (!isGameActive) {
                 ((Timer) e.getSource()).stop();
            }
        });
        swingTimer.start();
    }
    
    // 게임 보드 패널
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private boolean[] foundStatus;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private static final int RADIUS = 20; 
        
        private final Color[] PLAYER_COLORS = {
                Color.BLUE,   // 1p (0번)
                Color.RED,    // 2p (1번)
                Color.GREEN,  // 3p (2번)
                Color.YELLOW, // 4p (3번)
                Color.ORANGE  // 5p (4번)
        };
        
        public GameBoardPanel() {
            backgroundImage = null; 
            foundStatus = new boolean[0];
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isGameActive || timeLeft <= 0 || backgroundImage == null || originalDimension == null) {
                        return;
                    }
                    
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
                    
                    /*sendPacket(new GamePacket(
                            GamePacket.Type.MOUSE_MOVE, 
                            playerName, 
                            playerIndex, 
                            originalX, 
                            originalY
                        ));*/

                    int foundIndex = -1;
                    for (int i = 0; i < originalAnswers.size(); i++) {
                        if (originalAnswers.get(i).contains(originalX, originalY) && !foundStatus[i]) {
                            foundIndex = i;
                            break;
                        }
                    }
                    
                    if (foundIndex != -1) {
                        System.out.println("클라이언트: 정답 " + foundIndex + "번 클릭");
                        sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                    } else {
                        System.out.println("클라이언트: 오답 클릭 (" + e.getX() + ", " + e.getY() + ")");
                        marks.add(new GameMark(new Point((int) originalX, (int) originalY), false, playerIndex));
                        repaint();
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
                @Override
                public void mouseDragged(MouseEvent e) {
                     myMousePoint = e.getPoint();
                     repaint();
                }
            });
        }
        
        public void setRoundData(String path, List<Rectangle> originalAnswers, Dimension originalDimension) {
            this.originalAnswers = originalAnswers;
            this.originalDimension = originalDimension;
            this.foundStatus = new boolean[originalAnswers.size()];
            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) == -1) {
                    throw new IOException("이미지 파일 로드 실패: " + path);
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
            repaint();
        }
        
        public void addMark(int answerIndex, boolean correct, String senderName) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) {
                System.out.println("클라이언트: 서버로부터 잘못된 RESULT 인덱스 수신: " + answerIndex);
                return;
            }
            
            Rectangle originalRect = originalAnswers.get(answerIndex);
            Point center = new Point(originalRect.x + originalRect.width / 2, originalRect.y + originalRect.height / 2);
            
            int correctPlayerIdx = playerIndexMap.getOrDefault(senderName, 0);
            
            marks.add(new GameMark(center, correct, correctPlayerIdx));
            
            if (correct) {
                foundStatus[answerIndex] = true;
            }
            repaint();
        }
        
        public void removeExpiredMarks() {
            long currentTime = System.currentTimeMillis();
            if (marks.removeIf(m -> !m.correct && currentTime > m.expiryTime)) {
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
            double scale = 1.0;
            int drawW = 0, drawH = 0;
            int offsetX = 0, offsetY = 0;
            int imgW = 1, imgH = 1;
            
            if (backgroundImage != null && originalDimension != null) {
                imgW = originalDimension.width;
                imgH = originalDimension.height;
                scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                drawW = (int) (imgW * scale);
                drawH = (int) (imgH * scale);
                offsetX = (panelW - drawW) / 2;
                offsetY = (panelH - drawH) / 2;

                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);

                for (GameMark m : marks) {
                    int drawX = (int) (offsetX + m.p.x * scale);
                    int drawY = (int) (offsetY + m.p.y * scale);
                    
                    if (m.correct) {
                    	if ("경쟁".equals(gameMode)) {
                            int colorIdx = Math.max(0, Math.min(m.correctPlayer, 4)); // 0~4 범위 제한
                            g2.setColor(PLAYER_COLORS[colorIdx]);
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
                        String otherName = entry.getKey();
                        Point2D.Double p = entry.getValue(); 
                        
                        int drawX = (int) (offsetX + p.x * scale);
                        int drawY = (int) (offsetY + p.y * scale);
                        
                        int idx = playerIndexMap.getOrDefault(otherName, 0);
                        if(idx >= 0 && idx < 5 && cursorImages[idx] != null) {
                            g2.drawImage(cursorImages[idx], drawX, drawY, 30, 30, this); 
                            g2.setColor(Color.WHITE);
                            g2.setFont(new Font("Dialog", Font.BOLD, 10));
                            g2.drawString(otherName, drawX, drawY); // 다른 사람은 이름 표시
                        }
                    }
                }
                
                Image myImg = null;
                if (playerIndexMap.size() <= 1) {
                    myImg = singleCursorImage;
                } else {
                    int idx = Math.max(0, Math.min(playerIndex, 4));
                    myImg = cursorImages[idx];
                }

                if (myImg != null && myMousePoint.x > -50 && myMousePoint.y > -50) {
                    g2.drawImage(myImg, myMousePoint.x, myMousePoint.y, 30, 30, this);
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
                if (correct) {
                    this.expiryTime = -1; 
                } else {
                    this.expiryTime = System.currentTimeMillis() + 5000; 
                }
            }
        }
    }
}