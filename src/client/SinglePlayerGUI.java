package client;

import model.GamePacket;
import model.UserData;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

// 1인 플레이 게임 화면 (서버 통신 버전 - 다중 라운드 지원)
public class SinglePlayerGUI extends JFrame {

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

    // 게임 상태
    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;
    private int score = 0;
    private int foundCount = 0;
    private int totalAnswers = 0;
    private int currentRound = 1; // 현재 라운드 추적

    public SinglePlayerGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                          String playerName, String difficulty, GamePacket roundStartPacket, GameLauncher launcher) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;
        this.launcher = launcher;

        setTitle("숨은 그림 찾기 - 1인 플레이 (플레이어: " + playerName + ")");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                endGame(false);
            }
        });
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildUI();
        setupKeyBindings();
        
        // 서버 리스너 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        // 라운드 시작
        handlePacket(roundStartPacket);

        pack(); 
        setResizable(false);
        setVisible(true);
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
        JLabel hintLabel = new JLabel("Q: 힌트     H: 도움말     ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 12 -> 11
        bottomBar.add(hintLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);
    }
    
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('Q'), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isGameActive) return;
                showHint();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('H'), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(
                        SinglePlayerGUI.this,
                        "Q: 힌트 요청\nH: 도움말 보기\nESC: 게임 종료\n\n" +
                        "화면을 클릭하여 숨은 그림을 찾으세요!"
                );
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "EXIT");
        root.getActionMap().put("EXIT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                endGame(false);
            }
        });
    }

    // 서버로부터 패킷 수신
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
                if (isVisible()) {
                    appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
                    endGame(false);
                }
            });
        }
    }

    // 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case ROUND_START:
                currentRound = p.getRound();
                roundLabel.setText("라운드 " + currentRound);
                String imagePath = p.getMessage(); 
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();
                
                if (imagePath != null && !imagePath.isEmpty() && originalAnswers != null && originalDimension != null) {
                    totalAnswers = originalAnswers.size();
                    foundCount = 0; // 라운드 시작 시 찾은 개수 초기화
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("[시스템] 라운드 " + currentRound + " 시작!\n");
                    appendStatus("[목표] " + totalAnswers + "개의 숨은 그림을 찾으세요!\n");
                    isGameActive = true; 
                }
                
                gameBoardPanel.clearMarks();
                updateScoreDisplay();
                startCountdownTimer(120);
                break;
                
            case RESULT:
                // 서버로부터 정답 확인 결과 수신
                boolean isCorrect = p.isCorrect();
                int answerIndex = p.getAnswerIndex();
                
                gameBoardPanel.addMark(answerIndex, isCorrect);
                
                if (isCorrect) {
                    foundCount++;
                    appendStatus("[정답!] " + foundCount + "/" + totalAnswers + " 찾음!\n");
                } else {
                    appendStatus("[오답] 이미 찾은 그림이거나 정답이 아닙니다.\n");
                }
                break;
                
            case SCORE:
                // 점수 업데이트
                String scoreText = p.getMessage();
                if (scoreText != null && scoreText.contains("점")) {
                    try {
                        String[] lines = scoreText.split("\n");
                        for (String line : lines) {
                            if (line.contains(playerName)) {
                                String scoreStr = line.replaceAll("[^0-9-]", "");
                                if (!scoreStr.isEmpty()) {
                                    score = Integer.parseInt(scoreStr);
                                    updateScoreDisplay();
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // 파싱 실패 시 무시
                    }
                }
                break;
                
            case MESSAGE:
                // 서버 메시지 (라운드 완료 알림 등)
                if (p.getMessage() != null) {
                    appendStatus("[서버] " + p.getMessage() + "\n");
                }
                break;
                
            case GAME_OVER:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                appendStatus("\n[게임 완료!]\n");
                appendStatus(p.getMessage() + "\n");
                updateScoreDisplay();
                endGame(true);
                break;
                
            default:
                break;
        }
    }
    
    // 서버로 패킷 전송 (라운드 정보 포함)
    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
            }
        } catch (Exception e) {
            appendStatus("[에러] 서버 통신 실패\n");
        }
    }
    
    private void showHint() {
        // 힌트 요청 메시지 전송
        GamePacket hintPacket = new GamePacket(
            GamePacket.Type.MESSAGE,
            playerName,
            "[힌트 요청]"
        );
        sendPacket(hintPacket);
        appendStatus("[힌트] 힌트를 요청했습니다. (-5점)\n");
    }
    
    private void updateScoreDisplay() {
        scoreArea.setText(
            "점수: " + score + "점\n" +
            "찾은 개수: " + foundCount + "/" + totalAnswers + "\n" +
            "남은 시간: " + timeLeft + "초"
        );
    }
    
    private void appendStatus(String msg) {
        statusArea.append(msg);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
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
                updateScoreDisplay();
                gameBoardPanel.removeExpiredMarks();
                
                if (timeLeft <= 0) {
                    ((Timer) e.getSource()).stop();
                    isGameActive = false;
                    
                    // 서버에 타이머 종료 알림
                    sendPacket(new GamePacket(GamePacket.Type.TIMER_END, "타이머 종료"));
                    appendStatus("\n[시간 종료!]\n");
                }
            } else if (!isGameActive) {
                 ((Timer) e.getSource()).stop();
            }
        });
        swingTimer.start();
    }
    
    private void endGame(boolean isComplete) {
        isGameActive = false;
        if (swingTimer != null) swingTimer.stop();
        
        // 경험치 계산
        int expGain = 0;
        if (isComplete) {
            expGain = 50 + (score / 2);
        } else if (score > 0) {
            expGain = score / 5;
        }
        
        if (expGain > 0) {
            UserData userData = UserData.getInstance();
            if (userData != null) {
                userData.addExperience(expGain);
                appendStatus("\n[경험치 획득: " + expGain + " EXP]\n");
            }
        }
        
        // 서버 연결 종료
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            System.out.println("[클라이언트] 소켓 종료 중 오류: " + e.getMessage());
        }
        
        // 3초 후 메인 메뉴로 (새 GameLauncher 생성하지 않고 기존 것 재사용)
        Timer exitTimer = new Timer(3000, e -> {
            this.dispose();
            SwingUtilities.invokeLater(() -> {
                // 기존 launcher가 있으면 재사용, 없으면 새로 생성
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
    }
    
    // 게임 보드 패널
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private static final int RADIUS = 20; 
        
        public GameBoardPanel() {
            backgroundImage = null; 
            originalAnswers = new ArrayList<>();
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isGameActive || timeLeft <= 0 || backgroundImage == null || originalDimension == null) {
                        return;
                    }
                    
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
                        if (originalAnswers.get(i).contains(originalX, originalY)) {
                            foundIndex = i;
                            break;
                        }
                    }
                    
                    if (foundIndex != -1) {
                        // 서버로 클릭 전송 (라운드 정보 포함)
                        GamePacket clickPacket = new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex);
                        // 임시로 라운드 정보를 전달하기 위해 수정 필요
                        sendPacket(clickPacket);
                    } else {
                        // 오답 마크 (로컬에서만 표시)
                        marks.add(new GameMark(new Point((int) originalX, (int) originalY), false));
                        repaint();
                    }
                }
            });
        }
        
        public void setRoundData(String path, List<Rectangle> originalAnswers, Dimension originalDimension) {
            this.originalAnswers = originalAnswers;
            this.originalDimension = originalDimension;
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
                SinglePlayerGUI.this.appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
        }
        
        public void clearMarks() {
            marks.clear();
            repaint();
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
            if (backgroundImage != null) {
                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = originalDimension.width;
                int imgH = originalDimension.height;

                double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);
                int offsetX = (panelW - drawW) / 2;
                int offsetY = (panelH - drawH) / 2;

                g2.drawImage(backgroundImage, offsetX, offsetY, drawW, drawH, this);

                for (GameMark m : marks) {
                    int drawX = (int) (offsetX + m.p.x * scale);
                    int drawY = (int) (offsetY + m.p.y * scale);
                    
                    if (m.correct) {
                        g2.setColor(new Color(0, 255, 0, 180));
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
            } else {
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("맑은 고딕", Font.BOLD, 20));
                g2.drawString("게임 로딩 중...", getWidth() / 2 - 80, getHeight() / 2);
            }
        }
        
        class GameMark {
            Point p;
            boolean correct;
            long expiryTime; 
            GameMark(Point centerPoint, boolean correct) {
                this.p = centerPoint;
                this.correct = correct;
                if (correct) {
                    this.expiryTime = -1; 
                } else {
                    this.expiryTime = System.currentTimeMillis() + 5000; 
                }
            }
        }
    }
}