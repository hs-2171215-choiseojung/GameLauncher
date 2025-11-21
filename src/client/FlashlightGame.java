package client;

import model.GamePacket;
import model.UserData;

import javax.swing.*;

import client.SinglePlayerGUI.GameBoardPanel;
import client.SinglePlayerGUI.GameBoardPanel.GameMark;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FlashlightGame extends JFrame implements KeyListener {

	// 통신 관련
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private GameLauncher launcher;

    // UI 컴포넌트
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea scoreArea;
    private GameBoardPanel gameBoardPanel;
    private JTextField inputField;

    // 게임 상태
    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;
    private int score = 0;
    private int foundCount = 0;
    private int totalAnswers = 0;
    private int currentRound = 1;
    private boolean[] keys;

    // 스포트라이트, 이동
    private static final int PLAYER_SIZE = 40;       
    private static final int FLASHLIGHT_RADIUS = 150; 
    private static final int MOVE_SPEED = 5; // 이동 속도
    private static final int TIP_OFFSET_X = 13; // 커서 이미지 보정값
    private static final int TIP_OFFSET_Y = 0;

    // 내 커서 위치 (가상 좌표)
    private Point myMousePoint = new Point(300, 300);
    
    // 키보드 입력 상태
    private boolean up, down, left, right;
    private Timer moveTimer;
    
    private Image cursorImage;

    public FlashlightGame(Socket socket, ObjectInputStream in, ObjectOutputStream out, 
                          String playerName, String difficulty, GamePacket roundStartPacket,
                          GameLauncher launcher) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;
        this.launcher = launcher;
        
        keys = new boolean[256];

        setTitle("숨은 그림 찾기 - 동적 모드 (키보드 전용)");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleGameExit();
            }
        });
        
        setResizable(false);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        loadCursorImage();
        buildUI();
        
        // 물리 마우스 숨기기
        setLocalCursor(); 
        
        // 키보드 설정
        addKeyListener(this);
        setFocusable(true);
        
        // 이동 타이머 시작 (60FPS)
        moveTimer = new Timer(16, e -> updatePosition());
        moveTimer.start();

        // 서버 리스너 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();

        // 첫 라운드 데이터 처리
        handlePacket(roundStartPacket);

        pack();
        setVisible(true);
        
        // 키보드 입력을 받기 위해 포커스 요청
        this.requestFocusInWindow();
    }

    private void loadCursorImage() {
        try {
            // 손가락 커서 이미지 로드 (없으면 기본 마우스)
            cursorImage = new ImageIcon("images/cursor1.png").getImage();
            if (cursorImage.getWidth(null) == -1) {
                cursorImage = new ImageIcon("images/mouse1.png").getImage();
            }
        } catch (Exception e) {
            System.out.println("커서 이미지 로드 실패: " + e.getMessage());
        }
    }

    private void setLocalCursor() {
        // 게임 패널 위에서는 실제 마우스 포인터를 안 보이게 함 (투명 커서 적용)
        if (gameBoardPanel != null) {
            Toolkit tk = Toolkit.getDefaultToolkit();
            Image transparentImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Cursor invisibleCursor = tk.createCustomCursor(transparentImage, new Point(0, 0), "InvisibleCursor");
            gameBoardPanel.setCursor(invisibleCursor);
        }
    }

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("숨은 그림 찾기 (1인 플레이)");
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
        
        rightPanel.add(statusScroll, BorderLayout.CENTER);
        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13)); // 14 -> 13
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("점수: 0점\n찾은 개수: 0/0\n");
        scoreArea.setRows(3); 
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel hintLabel = new JLabel("방향키: 이동     스페이스바: 선택     Q: 힌트     H: 도움말     ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 12 -> 11
        bottomBar.add(hintLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    // 부드러운 이동 처리
    private void updatePosition() {
        if (!isGameActive) return;
        
        if (keys[KeyEvent.VK_UP] && myMousePoint.y > 0) {
            myMousePoint.y -= MOVE_SPEED;
        }
        if (keys[KeyEvent.VK_DOWN] && myMousePoint.y < gameBoardPanel.getHeight()) {
            myMousePoint.y += MOVE_SPEED;
        }
        if (keys[KeyEvent.VK_LEFT] && myMousePoint.x > 0) {
            myMousePoint.x -= MOVE_SPEED;
        }
        if (keys[KeyEvent.VK_RIGHT] && myMousePoint.x < gameBoardPanel.getWidth()) {
            myMousePoint.x += MOVE_SPEED;
        }
        
        gameBoardPanel.removeExpiredMarks();
        gameBoardPanel.repaint();
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        
        // 이동 키 상태 저장
        if (code >= 0 && code < keys.length) {
            keys[code] = true;
        }
        
        // 동작 (스페이스바, 힌트, 종료 등)
        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
            gameBoardPanel.processClick(myMousePoint);
        } else if (code == KeyEvent.VK_Q) {
            showHint();
        } else if (code == KeyEvent.VK_ESCAPE) {
            handleGameExit();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) {
            keys[code] = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        
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
                    handleGameExit();
                }
            });
        }
    }

    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                currentRound = p.getRound();
                roundLabel.setText("라운드 " + currentRound);
                String imagePath = p.getMessage();
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();

                if (imagePath != null) {
                    totalAnswers = originalAnswers.size();
                    foundCount = 0;
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("\n=== 라운드 " + currentRound + " 시작 ===\n");
                    appendStatus("[목표] 어둠 속에 숨겨진 " + totalAnswers + "개의 그림을 찾으세요!\n");
                    isGameActive = true;
                    
                    // 시작 시 커서 중앙으로
                    if (gameBoardPanel.getWidth() > 0) {
                        myMousePoint.setLocation(gameBoardPanel.getWidth()/2, gameBoardPanel.getHeight()/2);
                    }
                }
                
                gameBoardPanel.clearMarks();
                updateScoreDisplay();
                startCountdownTimer(120);
                break;

            case RESULT:
            	boolean isCorrect = p.isCorrect();
                int answerIndex = p.getAnswerIndex();
                
                gameBoardPanel.addMark(answerIndex, isCorrect);

                if (isCorrect) {
                    foundCount++;
                    appendStatus("[정답] 찾았습니다! (" + foundCount + "/" + totalAnswers + ")\n");
                } else {
                    appendStatus("[오답] 아무것도 없습니다.\n");
                }
                break;

            case SCORE:
                if (p.getMessage() != null) parseScore(p.getMessage());
                break;
            
            case MESSAGE:
                if (p.getMessage() != null) appendStatus("[서버] " + p.getMessage() + "\n");
                break;

            case GAME_OVER:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                moveTimer.stop();
                appendStatus("\n[게임 종료]\n" + p.getMessage() + "\n");
                UserData userData = UserData.getInstance();
                if (userData != null) userData.addExperience(50);
                Timer exitTimer = new Timer(3000, e -> handleGameExit());
                exitTimer.setRepeats(false);
                exitTimer.start();
                break;
        }
    }
    
    private void parseScore(String msg) {
        try {
            String[] lines = msg.split("\n");
            for (String line : lines) {
                if (line.contains(playerName)) {
                    String num = line.replaceAll("[^0-9-]", "");
                    if (!num.isEmpty()) score = Integer.parseInt(num);
                    updateScoreDisplay();
                    break;
                }
            }
        } catch (Exception e) {}
    }

    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
            }
        } catch (Exception e) {}
    }
    
    private void showHint() {
        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, "[힌트 요청]"));
        appendStatus("힌트 요청됨 (-5점)\n");
    }
    
    private void handleGameExit() {
        isGameActive = false;
        if (swingTimer != null) swingTimer.stop();
        if (moveTimer != null) moveTimer.stop();
        this.dispose();
        try { if (socket != null) socket.close(); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> {
            if (launcher != null) {
                launcher.setVisible(true);
                launcher.switchToMainMenu();
            }
        });
    }
    
    private void updateScoreDisplay() {
        scoreArea.setText("점수: " + score + "점\n찾은 개수: " + foundCount + "/" + totalAnswers + "\n남은 시간: " + timeLeft);
    }
    
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private void startCountdownTimer(int sec) {
        if (swingTimer != null) swingTimer.stop();
        timeLeft = sec;
        timerLabel.setText("타이머: " + timeLeft);
        swingTimer = new Timer(1000, e -> {
            if (isGameActive && timeLeft > 0) {
                timeLeft--;
                timerLabel.setText("타이머: " + timeLeft);
                if (timeLeft <= 10) timerLabel.setForeground(Color.RED);
                else timerLabel.setForeground(Color.YELLOW);
                
                updateScoreDisplay();
                if (timeLeft <= 0) {
                    isGameActive = false;
                    sendPacket(new GamePacket(GamePacket.Type.TIMER_END, "TIME_OVER"));
                    ((Timer)e.getSource()).stop();
                }
            }
        });
        swingTimer.start();
    }
    
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private boolean[] foundStatus;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private static final int RADIUS = 20; 
        
        public GameBoardPanel() {
            setBackground(Color.BLACK);
        }
        
        public void processClick(Point p) {
            if (!isGameActive || backgroundImage == null || originalDimension == null 
                || originalAnswers == null || foundStatus == null) {
                return;
            }
            
            int panelW = getWidth();
            int panelH = getHeight();
            double imgW = originalDimension.width;
            double imgH = originalDimension.height;
            
            double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
            int offsetX = (int)((panelW - (imgW * scale)) / 2);
            int offsetY = (int)((panelH - (imgH * scale)) / 2);
            
            int fingerX = p.x + TIP_OFFSET_X;
            int fingerY = p.y + TIP_OFFSET_Y;
            
            double originalX = (fingerX - offsetX) / scale;
            double originalY = (fingerY - offsetY) / scale;

            int foundIndex = -1;
            for (int i = 0; i < originalAnswers.size(); i++) {
                if (i < foundStatus.length && foundStatus[i]) continue;

                if (originalAnswers.get(i).contains(originalX, originalY)) {
                    foundIndex = i;
                    break;
                }
            }
            
            if (foundIndex != -1) {
                sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
            } else {
                marks.add(new GameMark(new Point((int)originalX, (int)originalY), false));
                repaint();
            }
        }
        
        public void setRoundData(String path, List<Rectangle> originalAnswers, Dimension originalDimension) {
            this.originalAnswers = originalAnswers;
            this.originalDimension = originalDimension;
            
            if (originalAnswers != null) {
                this.foundStatus = new boolean[originalAnswers.size()];
            }
            
            try {
                backgroundImage = new ImageIcon(path).getImage();
                if (backgroundImage.getWidth(null) == -1) {
                    throw new Exception("이미지 파일 로드 실패: " + path);
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
                FlashlightGame.this.appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }
        
        public void clearMarks() { marks.clear(); repaint(); }
        
        public void removeExpiredMarks() {
            if (marks.removeIf(m -> !m.correct && System.currentTimeMillis() > m.expiryTime)) repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int panelW = getWidth();
            int panelH = getHeight();
            int imgW = originalDimension.width;
            int imgH = originalDimension.height;

            // 1. 배경 그리기
            if (backgroundImage != null && originalDimension != null) {
            	double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                int drawW = (int) (originalDimension.width * scale);
                int drawH = (int) (originalDimension.height * scale);
                int offsetX = (panelW - drawW) / 2;
                int offsetY = (panelH - drawH) / 2;

                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);
                
                for (GameMark m : marks) {
                	int drawX = (int) (m.p.x * scale) + offsetX;
                    int drawY = (int) (m.p.y * scale) + offsetY;
                    int r = 20;
                    
                    if (m.correct) {
                        g2.setColor(new Color(0, 255, 0, 200)); // 녹색
                        g2.setStroke(new BasicStroke(3));
                        g2.draw(new Ellipse2D.Double(drawX - r, drawY - r, r*2, r*2));
                    } else {
                        g2.setColor(Color.RED); // 빨강
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 28));
                        g2.drawString("X", drawX - 10, drawY + 10);
                    }
                }
                
                if (isGameActive) {
                    Point2D center = new Point2D.Float(myMousePoint.x + TIP_OFFSET_X, myMousePoint.y + TIP_OFFSET_Y);
                    float[] dist = {0.0f, 1.0f};
                    Color[] colors = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 250) }; 
                    
                    if (myMousePoint.x > -100) {
                        RadialGradientPaint p = new RadialGradientPaint(center, FLASHLIGHT_RADIUS, dist, colors);
                        g2.setPaint(p);
                        g2.fillRect(0, 0, panelW, panelH);
                    }
                }

                if (cursorImage != null && myMousePoint.x > -50) {
                    g2.drawImage(cursorImage, myMousePoint.x, myMousePoint.y, PLAYER_SIZE, PLAYER_SIZE, this);
                }

            } else {
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, panelW, panelH);
                g2.setColor(Color.WHITE);
                g2.drawString("Loading...", panelW/2 - 30, panelH/2);
            }
        }
        
        public void addMark(int answerIndex, boolean correct) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) {
                return;
            }
            
            Rectangle originalRect = originalAnswers.get(answerIndex);
            Point center = new Point(originalRect.x + originalRect.width / 2, originalRect.y + originalRect.height / 2);
            
            marks.add(new GameMark(center, correct));
            repaint();
        }
        
        class GameMark {
            Point p; 
            boolean correct; 
            long expiryTime;
            GameMark(Point p, boolean c) {
                this.p = p; 
                this.correct = c;
                this.expiryTime = c ? -1 : System.currentTimeMillis() + 1000;
            }
        }
    }
}