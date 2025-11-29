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

public class SinglePlayerGUI extends JFrame {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String playerName;
    private final String difficulty;
    private GameLauncher launcher;

    private JLabel timerLabel;
    private JLabel roundLabel;
    private JTextArea statusArea;
    private JTextArea scoreArea;
    private GameBoardPanel gameBoardPanel;

    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;
    private int score = 0;
    private int foundCount = 0;
    private int totalAnswers = 0;
    private int currentRound = 1;
    
    private int hintsRemaining = 3;
    
    private Image singleCursorImage;

    public SinglePlayerGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                          String playerName, String difficulty, GamePacket roundStartPacket, GameLauncher launcher) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.playerName = playerName;
        this.difficulty = difficulty;
        this.launcher = launcher;
        
        initCustomCursor();

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
        
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        handlePacket(roundStartPacket);

        pack(); 
        setResizable(false);
        setVisible(true);
    }
    
    private void initCustomCursor() {
        try {
            singleCursorImage = new ImageIcon("images/singleMouse.png").getImage();
            Toolkit tk = Toolkit.getDefaultToolkit();
            Cursor customCursor = tk.createCustomCursor(singleCursorImage, new Point(0, 0), "SingleCursor");
            this.setCursor(customCursor);
            
        } catch (Exception e) {
            System.out.println("[SinglePlayerGUI] 커서 이미지 로드 실패: " + e.getMessage());
        }
    }

    private void buildUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(220, 220, 220));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("숨은 그림 찾기 (1인 플레이)");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        timerLabel = new JLabel("타이머: 120초", SwingConstants.CENTER);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
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
        
        rightPanel.add(statusScroll, BorderLayout.CENTER);
        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setText("점수: 0점\n찾은 개수: 0/0\n힌트: 3/3\n");
        scoreArea.setRows(4); 
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel hintLabel = new JLabel("Q: 힌트 (3회)     H: 도움말     ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomBar.add(hintLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);
    }
    
    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        
        // Q키 - 힌트
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("[SinglePlayerGUI] Q키 눌림 - 힌트 요청");
                if (!isGameActive) {
                    System.out.println("[SinglePlayerGUI] 게임이 활성화되지 않음");
                    return;
                }
                requestHint();
            }
        });
        
        // H키 - 도움말
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(
                        SinglePlayerGUI.this,
                        "Q: 힌트 요청 (최대 3회, -5점)\nH: 도움말 보기\nESC: 게임 종료\n\n" +
                        "화면을 클릭하여 숨은 그림을 찾으세요!"
                );
            }
        });
        
        // ESC키 - 종료
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "EXIT");
        root.getActionMap().put("EXIT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                endGame(false);
            }
        });
    }

 
    private void requestHint() {
        if (!isGameActive) {
            appendStatus("[힌트] 게임이 진행 중일 때만 사용할 수 있습니다.\n");
            return;
        }
        
        if (hintsRemaining <= 0) {
            appendStatus("[힌트] 더 이상 힌트를 사용할 수 없습니다. (0/3)\n");
            return;
        }
        
        System.out.println("[SinglePlayerGUI] 힌트 요청 전송: " + playerName);
        GamePacket hintPacket = new GamePacket(GamePacket.Type.HINT_REQUEST, playerName, "HINT");
        sendPacket(hintPacket);
        appendStatus("[힌트] 힌트 요청 중...\n");
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
                if (isVisible()) {
                    appendStatus("[시스템] 서버 연결이 끊어졌습니다.\n");
                    endGame(false);
                }
            });
        }
    }

    private void handlePacket(GamePacket p) {
        System.out.println("[SinglePlayerGUI] 패킷 수신: " + p.getType());
        
        switch (p.getType()) {
            case ROUND_START:
                currentRound = p.getRound();
                roundLabel.setText("라운드 " + currentRound);
                
                hintsRemaining = 3;
                
                String imagePath = p.getMessage(); 
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();
                
                if (imagePath != null && !imagePath.isEmpty() && originalAnswers != null && originalDimension != null) {
                    totalAnswers = originalAnswers.size();
                    foundCount = 0;
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
                    }
                }
                break;
                
            case HINT_RESPONSE:
                System.out.println("[SinglePlayerGUI] 힌트 응답 수신");
                Point hintPos = p.getHintPosition();
                hintsRemaining = p.getRemainingHints();
                
                System.out.println("[SinglePlayerGUI] 힌트 위치: " + hintPos + ", 남은 횟수: " + hintsRemaining);
                
                if (hintPos != null) {
                    gameBoardPanel.addHint(hintPos);
                    appendStatus(p.getMessage() + "\n");
                } else {
                    appendStatus("[힌트] " + p.getMessage() + "\n");
                }
                
                updateScoreDisplay();
                break;
                
            case MESSAGE:
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
                System.out.println("[SinglePlayerGUI] 처리되지 않은 패킷: " + p.getType());
                break;
        }
    }
    
    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                System.out.println("[SinglePlayerGUI] 패킷 전송: " + packet.getType());
                out.writeObject(packet);
                out.flush();
            } else {
                System.out.println("[SinglePlayerGUI] 오류: out이 null입니다!");
            }
        } catch (Exception e) {
            System.out.println("[SinglePlayerGUI] 패킷 전송 실패: " + e.getMessage());
            e.printStackTrace();
            appendStatus("[에러] 서버 통신 실패\n");
        }
    }
    
    private void updateScoreDisplay() {
        scoreArea.setText(
            "점수: " + score + "점\n" +
            "찾은 개수: " + foundCount + "/" + totalAnswers + "\n" +
            "힌트: " + hintsRemaining + "/3\n" + 
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
        if (swingTimer != null) {
            swingTimer.stop();
            swingTimer = null;
        }
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
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            System.out.println("[클라이언트] 소켓 종료 중 오류: " + e.getMessage());
        }
        
        Timer exitTimer = new Timer(3000, e -> {
            this.dispose();
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
    }
    
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private final List<HintMark> hints = new ArrayList<>(); 
        private Timer blinkTimer;
        private boolean blinkState = true;
        private static final int RADIUS = 20; 
        
        public GameBoardPanel() {
            backgroundImage = null; 
            originalAnswers = new ArrayList<>();
           
            blinkTimer = new Timer(500, e -> {
                blinkState = !blinkState;
                repaint();
            });
            blinkTimer.start();
            
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
                        GamePacket clickPacket = new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex);
                        sendPacket(clickPacket);
                    } else {
                        marks.add(new GameMark(new Point((int) originalX, (int) originalY), false));
                        repaint();
                    }
                }
            });
        }
        
        public void addHint(Point hintPos) {
            hints.add(new HintMark(hintPos));
            repaint();
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
            hints.clear();
            repaint();
        }
        
        public void addMark(int answerIndex, boolean correct) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) {
                return;
            }
            
            Rectangle originalRect = originalAnswers.get(answerIndex);
            Point center = new Point(originalRect.x + originalRect.width / 2, originalRect.y + originalRect.height / 2);
            
            marks.add(new GameMark(center, correct));
            
          
            if (correct) {
                hints.removeIf(h -> h.position.distance(center) < 30);
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
 
        class HintMark {
            Point position;
            HintMark(Point p) {
                this.position = p;
            }
        }
    }
}