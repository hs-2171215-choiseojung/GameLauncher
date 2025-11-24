package client;

import model.GamePacket;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.awt.RadialGradientPaint;
import javax.swing.Timer;

import java.util.List;
import java.util.ArrayList;

public class FlashlightGame extends JFrame implements KeyListener {

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

  
    private JTextArea chatArea;
    private JTextField chatInput;

    private int timeLeft = 120;
    private Timer swingTimer;
    private boolean isGameActive = false;
    private int score = 0;
    private int foundCount = 0;
    private int totalAnswers = 0;
    private int currentRound = 1;
    private boolean[] keys;

    private static final int PLAYER_SIZE = 40;
    private static final int FLASHLIGHT_RADIUS = 150;
    private static final int MOVE_SPEED = 5;
    private static final int TIP_OFFSET_X = 13;
    private static final int TIP_OFFSET_Y = 0;

   
    private final Map<Integer, String> emotes = new HashMap<>();

    private Point myMousePoint = new Point(300, 300);
    private Timer moveTimer;
    private Image cursorImage;

    private String gameMode = "협동";   

   
    private int cursorIndex = 1;


    private final Map<String, RemoteCursor> remoteCursors = new HashMap<>();

  
    private final Map<Integer, Image> cursorImageCache = new HashMap<>();

    private boolean isFrozen = false;
    private Timer freezeTimer;

    private Map<String, Integer> playerIndexMap = new HashMap<>();
    private int playerIndex = 0; 

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

        if (roundStartPacket != null) {
            if (roundStartPacket.getGameMode() != null) {
                this.gameMode = roundStartPacket.getGameMode();
            }
            if (roundStartPacket.getPlayerIndexMap() != null) {
                this.playerIndexMap = roundStartPacket.getPlayerIndexMap();
                this.playerIndex = playerIndexMap.getOrDefault(playerName, 0);
            }
        }

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
        setLocalCursor();

        setupKeyBindings(); 

        moveTimer = new Timer(16, e -> updatePosition());
        moveTimer.start();

        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();

       
        handlePacket(roundStartPacket);

        pack();
        setVisible(true);

       
        emotes.put(1, "화이팅!");
        emotes.put(2, "좋아요!");
        emotes.put(3, "힘내요!");
        emotes.put(4, "GG!");

        
        SwingUtilities.invokeLater(() -> {
            this.requestFocusInWindow();
        });
    }

   
    private void loadCursorImage() {
        cursorImage = getCursorImageByIndex(cursorIndex);
    }

    private Image getCursorImageByIndex(int idx) {
        if (idx < 1 || idx > 5) {
            System.out.println("[FlashlightGame] 잘못된 커서 인덱스: " + idx + " -> 1로 변경");
            idx = 1;
        }

        if (cursorImageCache.containsKey(idx)) {
            System.out.println("[FlashlightGame] 캐시에서 커서" + idx + " 이미지 로드");
            return cursorImageCache.get(idx);
        }

        try {
            String path = "images/cursor" + idx + ".png";
            System.out.println("[FlashlightGame] 커서 이미지 로드 시도: " + path);

            Image img = new ImageIcon(path).getImage();

            if (img.getWidth(null) == -1) {
                System.out.println("[FlashlightGame] 경고: " + path + " 로드 실패, cursor1.png 사용");
                img = new ImageIcon("images/cursor1.png").getImage();
            } else {
                System.out.println("[FlashlightGame] 성공: " + path + " 로드 완료!");
            }

            cursorImageCache.put(idx, img);
            return img;
        } catch (Exception e) {
            System.out.println("[FlashlightGame] 커서 이미지 로드 실패: " + e.getMessage());
            return null;
        }
    }

    private void setLocalCursor() {
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
        JLabel titleLabel = new JLabel("숨은 그림 찾기 (동적 모드)");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        timerLabel = new JLabel("타이머: 120초", SwingConstants.CENTER);
        timerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        roundLabel = new JLabel("라운드 1", SwingConstants.RIGHT);
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.CENTER);
        topBar.add(roundLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JButton helpButton = new JButton("도움말");
        helpButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        helpButton.addActionListener(e -> showHelpDialog());

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
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));

        if (!"경쟁".equals(gameMode)) {
            scoreArea.setText("점수: 0점\n찾은 개수: 0/0\n");
        }

        scoreArea.setRows(3);
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel hintLabel = new JLabel("방향키: 이동     스페이스바: 선택     /H: 도움말");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomBar.add(hintLabel, BorderLayout.WEST);

        
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        chatInput = new JTextField();
        JButton sendButton = new JButton("전송");
        sendButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);

        sendButton.addActionListener(e -> sendChat());

        add(bottomBar, BorderLayout.SOUTH);
    }

   
    private void setupKeyBindings() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

       
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "UP_PRESSED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "DOWN_PRESSED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "LEFT_PRESSED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "RIGHT_PRESSED");

        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "UP_RELEASED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "DOWN_RELEASED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "LEFT_RELEASED");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "RIGHT_RELEASED");

    
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "SPACE_ACTION");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "HINT_ACTION");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "EXIT_ACTION");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER_ACTION");

        
        actionMap.put("UP_PRESSED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus()) keys[KeyEvent.VK_UP] = true;
            }
        });
        actionMap.put("DOWN_PRESSED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus()) keys[KeyEvent.VK_DOWN] = true;
            }
        });
        actionMap.put("LEFT_PRESSED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus()) keys[KeyEvent.VK_LEFT] = true;
            }
        });
        actionMap.put("RIGHT_PRESSED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus()) keys[KeyEvent.VK_RIGHT] = true;
            }
        });

        
        actionMap.put("UP_RELEASED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keys[KeyEvent.VK_UP] = false;
            }
        });
        actionMap.put("DOWN_RELEASED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keys[KeyEvent.VK_DOWN] = false;
            }
        });
        actionMap.put("LEFT_RELEASED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keys[KeyEvent.VK_LEFT] = false;
            }
        });
        actionMap.put("RIGHT_RELEASED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keys[KeyEvent.VK_RIGHT] = false;
            }
        });

       
        actionMap.put("SPACE_ACTION", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus() && !isFrozen && gameBoardPanel != null) {
                    gameBoardPanel.processClick(myMousePoint);
                }
            }
        });

        actionMap.put("HINT_ACTION", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appendStatus("[힌트 실패] 플래시 모드에서는 힌트를 직접 사용할 수 없습니다. (아이템만 가능)\n");
            }
        });

        actionMap.put("EXIT_ACTION", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus()) {
                    handleGameExit();
                }
            }
        });

        actionMap.put("ENTER_ACTION", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (chatInput.hasFocus()) {
                    sendChat();
                } else {
                    chatInput.requestFocusInWindow();
                }
            }
        });

        
        inputMap.put(KeyStroke.getKeyStroke('H'), "HELP");
        actionMap.put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });
    }

    private void showHelpDialog() {
        JOptionPane.showMessageDialog(
                this,
                "🔦 숨은 그림 찾기 - 동적(플래시) 모드 도움말\n\n"
                        + "✔ 손전등(커서)으로 어두운 화면을 비추며 그림을 찾으세요.\n"
                        + "✔ 방향키로 손전등을 이동합니다.\n"
                        + "✔ 스페이스바로 클릭 판정.\n"
                        + "✔ /1~4 : 빠른 채팅\n"
                        + "✔ /H : 도움말\n"
                        + "✔ ESC : 게임 종료\n\n"
                        + "🎯 아이템\n"
                        + "- 타이머 아이템: 타이머 5초 증가(협동)\n"
                        + " - ❄ 얼음: 다른 플레이어를 멈춤(경쟁)\n"
                        + " - ★ 힌트 아이템: 정답 위치 표시\n",
                "도움말",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

 
    private void updatePosition() {
        if (!isGameActive || isFrozen) return;

        boolean moved = false;

        if (keys[KeyEvent.VK_UP] && myMousePoint.y > 0) {
            myMousePoint.y -= MOVE_SPEED;
            moved = true;
        }
        if (keys[KeyEvent.VK_DOWN] && myMousePoint.y < gameBoardPanel.getHeight()) {
            myMousePoint.y += MOVE_SPEED;
            moved = true;
        }
        if (keys[KeyEvent.VK_LEFT] && myMousePoint.x > 0) {
            myMousePoint.x -= MOVE_SPEED;
            moved = true;
        }
        if (keys[KeyEvent.VK_RIGHT] && myMousePoint.x < gameBoardPanel.getWidth()) {
            myMousePoint.x += MOVE_SPEED;
            moved = true;
        }

        if (moved) {
            sendCursorMove();
        }

        gameBoardPanel.removeExpiredMarks();
        gameBoardPanel.repaint();
    }

    private void sendCursorMove() {
        if (gameBoardPanel == null || gameBoardPanel.originalDimension == null) return;

        int panelW = gameBoardPanel.getWidth();
        int panelH = gameBoardPanel.getHeight();
        double imgW = gameBoardPanel.originalDimension.width;
        double imgH = gameBoardPanel.originalDimension.height;

        double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
        int offsetX = (int) ((panelW - (imgW * scale)) / 2);
        int offsetY = (int) ((panelH - (imgH * scale)) / 2);

        double originalX = (myMousePoint.x - offsetX) / scale;
        double originalY = (myMousePoint.y - offsetY) / scale;

        GamePacket packet = new GamePacket(
                GamePacket.Type.MOUSE_MOVE,
                playerName,
                0,
                originalX,
                originalY,
                cursorIndex
        );
        packet.setCursorIndex(cursorIndex);
        sendPacket(packet);
    }

   
    @Override
    public void keyPressed(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

  
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
        if (p == null) return;

        switch (p.getType()) {
            case ROUND_START:

                System.out.println("[FlashlightGame] 게임모드 = " + gameMode);

                currentRound = p.getRound();
                roundLabel.setText("라운드 " + currentRound);

                this.gameMode = p.getGameMode();

                // ★ 멀티처럼 playerIndexMap 업데이트
                if (p.getPlayerIndexMap() != null) {
                    this.playerIndexMap = p.getPlayerIndexMap();
                    this.playerIndex = playerIndexMap.getOrDefault(playerName, 0);
                }

                int idx = p.getCursorIndex();
                if (idx >= 1 && idx <= 5) {
                    this.cursorIndex = idx;
                } else {
                    this.cursorIndex = 1;
                }
                loadCursorImage();

                System.out.println("[FlashlightGame] " + playerName + "의 커서 인덱스: " + cursorIndex);

                String imagePath = p.getMessage();
                List<Rectangle> originalAnswers = p.getOriginalAnswers();
                Dimension originalDimension = p.getOriginalDimension();

                if (imagePath != null && originalAnswers != null && originalDimension != null) {
                    totalAnswers = originalAnswers.size();
                    foundCount = 0;
                    gameBoardPanel.setRoundData(imagePath, originalAnswers, originalDimension);
                    appendStatus("\n=== 라운드 " + currentRound + " 시작 ===\n");
                    appendStatus("[목표] 어둠 속에 숨겨진 " + totalAnswers + "개의 그림을 찾으세요!\n");
                    isGameActive = true;

                    if (gameBoardPanel.getWidth() > 0) {
                        myMousePoint.setLocation(gameBoardPanel.getWidth() / 2, gameBoardPanel.getHeight() / 2);
                    }
                }

                gameBoardPanel.clearMarks();
                remoteCursors.clear();

                startCountdownTimer(120);
                break;

            case RESULT:
                boolean isCorrect = p.isCorrect();
                int answerIndex = p.getAnswerIndex();

                if (isCorrect) {
                    gameBoardPanel.addMark(
                            answerIndex,
                            true,
                            p.getSender()
                    );

                    foundCount++;
                    appendStatus("[정답] 찾았습니다! (" + foundCount + "/" + totalAnswers + ")\n");
                } else {
                    gameBoardPanel.addMarkAtPosition(p.getX(), p.getY(), false);
                    appendStatus("[오답] 해당 위치에는 숨은 그림이 없습니다.\n");
                }
                break;

            case SCORE:
                if (p.getMessage() == null) break;

                if ("경쟁".equals(gameMode)) {
                    scoreArea.setText(p.getMessage());
                } else {
                    parseScore(p.getMessage());
                }
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

            case ITEM_SPAWN:
                gameBoardPanel.addItem(p.getItemId(), p.getItemPosition(), p.getItemType());

                String itemTypeText = "TIME";
                if ("FREEZE".equals(p.getItemType())) {
                    itemTypeText = "얼음";
                } else if ("HINT".equals(p.getItemType())) {
                    itemTypeText = "힌트★";
                }

                appendStatus("[아이템] " + itemTypeText + " 아이템이 생성되었습니다!\n");
                break;

            case ITEM_REMOVED:
                gameBoardPanel.removeItem(p.getItemId());
                appendStatus("[아이템] 아이템이 획득되었습니다!\n");
                break;

            case HINT_RESPONSE:
                Point hintPos = p.getHintPosition();

                if (hintPos != null) {
                    gameBoardPanel.addHint(hintPos);
                    appendStatus("[힌트★] 정답 위치를 표시했습니다!\n");
                } else {
                    appendStatus("[힌트] " + p.getMessage() + "\n");
                }
                break;

            case PLAYER_FREEZE:
                if (p.getMessage().equals(playerName)) {
                    freezePlayer(p.getFreezeDuration());
                }
                break;

            case TIME_BONUS:
                timeLeft += 5;
                timerLabel.setText("타이머: " + timeLeft + "초");
                appendStatus("[보너스] 타이머 +5초!\n");
                break;

            case MOUSE_MOVE:
                if (!p.getSender().equals(playerName)) {
                    int rx = (int) p.getX();
                    int ry = (int) p.getY();
                    int rIdx = p.getCursorIndex();

                    if (rIdx < 1 || rIdx > 5) {
                        System.out.println("[FlashlightGame] 경고: " + p.getSender() + "의 잘못된 커서 인덱스: " + rIdx + " -> 1로 변경");
                        rIdx = 1;
                    }

                    remoteCursors.put(
                            p.getSender(),
                            new RemoteCursor(new Point(rx, ry), rIdx)
                    );
                    gameBoardPanel.repaint();
                }
                break;

            case GAME_OVER:
                isGameActive = false;
                if (swingTimer != null) swingTimer.stop();
                if (moveTimer != null) moveTimer.stop();
                appendStatus("\n[게임 종료]\n" + p.getMessage() + "\n");
                UserData userData = UserData.getInstance();
                if (userData != null) userData.addExperience(50);
                Timer exitTimer = new Timer(3000, e -> handleGameExit());
                exitTimer.setRepeats(false);
                exitTimer.start();
                break;
        }
    }

   
    private void freezePlayer(int duration) {
        isFrozen = true;
        appendStatus("[경고] " + duration + "초 동안 멈춥니다!\n");

        if (freezeTimer != null) {
            freezeTimer.stop();
        }

        freezeTimer = new Timer(duration * 1000, e -> {
            isFrozen = false;
            appendStatus("[해제] 이동 가능합니다!\n");
        });
        freezeTimer.setRepeats(false);
        freezeTimer.start();
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
        } catch (Exception e) {
        }
    }

    private void sendPacket(GamePacket packet) {
        try {
            if (out != null) {
                out.writeObject(packet);
                out.flush();
            }
        } catch (Exception e) {
        }
    }

    private void showHint() {
        appendStatus("[힌트 실패] 플래시 모드에서는 힌트를 직접 사용할 수 없습니다. (힌트 아이템만 사용 가능)\n");
    }

    private void sendChat() {
        String raw = chatInput.getText().trim();
        if (raw.isEmpty()) {
            this.requestFocusInWindow();
            return;
        }

        String text = raw;

        // 1) 빠른 채팅 "/1 /2 /3 /4"
        if (raw.startsWith("/") && raw.length() > 1) {
            try {
                int num = Integer.parseInt(raw.substring(1));
                if (emotes.containsKey(num)) {
                    text = emotes.get(num);
                }
            } catch (Exception ignored) {
            }
        }

        // 2) 힌트 요청 "/Q"
        if (raw.equalsIgnoreCase("/Q")) {
            appendStatus("[힌트 실패] 플래시 모드에서는 힌트를 직접 사용할 수 없습니다.(아이템만 가능)\n");
            chatInput.setText("");
            return;
        }

        // 3) 도움말 "/H"
        if (raw.equalsIgnoreCase("/H")) {
            showHelpDialog();
            chatInput.setText("");
            this.requestFocusInWindow();
            return;
        }

        // 4) 일반 채팅
        GamePacket chatPacket = new GamePacket(
                GamePacket.Type.MESSAGE,
                playerName,
                text
        );
        sendPacket(chatPacket);

        chatInput.setText("");
        this.requestFocusInWindow();
    }

    private void appendChat(String msg) {
        chatArea.append(msg);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void handleGameExit() {
        isGameActive = false;
        if (swingTimer != null) {
            swingTimer.stop();
            swingTimer = null;
        }
        if (moveTimer != null) {
            moveTimer.stop();
            moveTimer = null;
        }
        if (freezeTimer != null) {
            freezeTimer.stop();
            freezeTimer = null;
        }

        this.dispose();
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(() -> {
            if (launcher != null) {
                launcher.setVisible(true);
                launcher.switchToMainMenu();
            }
        });
    }

    private void updateScoreDisplay() {

    	if ("경쟁".equals(gameMode)) return;

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
                else if (timeLeft <= 30) timerLabel.setForeground(Color.YELLOW);
                else timerLabel.setForeground(Color.BLACK);

                if (!"경쟁".equals(gameMode)) {
                    updateScoreDisplay();
                }
                if (timeLeft <= 0) {
                    isGameActive = false;
                    sendPacket(new GamePacket(GamePacket.Type.TIMER_END, "TIME_OVER"));
                    ((Timer) e.getSource()).stop();
                }
            }
        });
        swingTimer.start();
    }

   
    class GameBoardPanel extends JPanel {
        private Image backgroundImage;
        private List<Rectangle> originalAnswers;
        private boolean[] foundStatus;
        Dimension originalDimension;
        private final List<GameMark> marks = new ArrayList<>();
        private final List<HintMark> hints = new ArrayList<>();
        private Timer blinkTimer;

        private final Map<Integer, ItemData> items = new HashMap<>();

        private static final int ITEM_SIZE = 30;
        private boolean blinkState = true;

    
        private final Color[] PLAYER_COLORS = {
                Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE
        };

        public GameBoardPanel() {
            setBackground(Color.BLACK);

          
            blinkTimer = new Timer(500, e -> {
                blinkState = !blinkState;
                repaint();
            });
            blinkTimer.start();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                }
            });
        }

        public void addMarkAtPosition(double ox, double oy, boolean correct) {
            marks.add(new GameMark(new Point((int) ox, (int) oy), false));
            repaint();
        }

        public void addHint(Point hintPos) {
            hints.add(new HintMark(hintPos));
            repaint();
        }

        public void addMark(int answerIndex, boolean isCorrect, String senderName) {
            if (answerIndex < 0 || answerIndex >= originalAnswers.size()) return;

            Rectangle originalRect = originalAnswers.get(answerIndex);
            Point center = new Point(originalRect.x + originalRect.width / 2,
                    originalRect.y + originalRect.height / 2);

            marks.add(new GameMark(center, isCorrect, senderName));

            if (isCorrect && answerIndex < foundStatus.length) {
                foundStatus[answerIndex] = true;
                hints.removeIf(h -> h.position.distance(center) < 30);
            }
            repaint();
        }

        public void addItem(int itemId, Point position, String itemType) {
            items.put(itemId, new ItemData(position, itemType));
            repaint();
        }

        public void removeItem(int itemId) {
            items.remove(itemId);
            repaint();
        }

        public void processClick(Point p) {
            if (!isGameActive || isFrozen || backgroundImage == null || originalDimension == null
                    || originalAnswers == null || foundStatus == null) {
                return;
            }

            int panelW = getWidth();
            int panelH = getHeight();
            double imgW = originalDimension.width;
            double imgH = originalDimension.height;

            double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
            int offsetX = (int) ((panelW - (imgW * scale)) / 2);
            int offsetY = (int) ((panelH - (imgH * scale)) / 2);

            int fingerX = p.x + TIP_OFFSET_X;
            int fingerY = p.y + TIP_OFFSET_Y;

            double originalX = (fingerX - offsetX) / scale;
            double originalY = (fingerY - offsetY) / scale;

           
            for (Map.Entry<Integer, ItemData> entry : items.entrySet()) {
                ItemData item = entry.getValue();
                double dx = originalX - item.position.x;
                double dy = originalY - item.position.y;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= ITEM_SIZE) {
                    int itemId = entry.getKey();
                    sendPacket(new GamePacket(GamePacket.Type.ITEM_PICKUP, playerName, itemId, null));
                    removeItem(itemId);
                    return;
                }
            }

            int foundIndex = -1;
            for (int i = 0; i < originalAnswers.size(); i++) {
                if (i < foundStatus.length && foundStatus[i]) continue;
                if (originalAnswers.get(i).contains(originalX, originalY)) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1) {
                GamePacket clickPacket = new GamePacket(
                        GamePacket.Type.CLICK,
                        playerName,
                        foundIndex
                );
                clickPacket.setX(originalX);
                clickPacket.setY(originalY);
                sendPacket(clickPacket);

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
                FlashlightGame.this.appendStatus("[에러] 이미지 로드 실패: " + path + "\n");
            }
            clearMarks();
            items.clear();
        }

        public void clearMarks() {
            marks.clear();
            hints.clear();
            repaint();
        }

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

            if (backgroundImage != null && originalDimension != null) {
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
                        int hintX = (int) (hint.position.x * scale) + offsetX;
                        int hintY = (int) (hint.position.y * scale) + offsetY;

                        g2.setColor(new Color(255, 255, 0, 200));
                        g2.setStroke(new BasicStroke(4));
                        g2.draw(new Ellipse2D.Double(
                                hintX - 25, hintY - 25,
                                50, 50
                        ));

                        g2.setColor(Color.YELLOW);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 30));
                        g2.drawString("★", hintX - 15, hintY + 10);
                    }
                }

               
                for (GameMark m : marks) {
                    int drawX = (int) (m.p.x * scale) + offsetX;
                    int drawY = (int) (m.p.y * scale) + offsetY;
                    int r = 20;

                    if (m.correct) {
                        if ("경쟁".equals(gameMode) && FlashlightGame.this.playerIndexMap != null) {

                        	int idx = 0;
                            if (m.finderName != null) {
                                idx = FlashlightGame.this.playerIndexMap
                                        .getOrDefault(m.finderName, 0);
                            }
                            if (idx < 0 || idx >= PLAYER_COLORS.length) idx = 0;
                            g2.setColor(PLAYER_COLORS[idx]);
                        } else {
                            g2.setColor(new Color(0, 255, 0, 200)); 
                        }

                        g2.setStroke(new BasicStroke(3));
                        g2.draw(new Ellipse2D.Double(drawX - r, drawY - r, r * 2, r * 2));
                    } else {
                        g2.setColor(Color.RED);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 28));
                        g2.drawString("X", drawX - 10, drawY + 10);
                    }
                }

                
                for (ItemData item : items.values()) {
                    int itemX = (int) (item.position.x * scale) + offsetX;
                    int itemY = (int) (item.position.y * scale) + offsetY;

                    if ("FREEZE".equals(item.type)) {
                        g2.setColor(new Color(100, 150, 255, 220));
                    } else {
                        g2.setColor(new Color(255, 215, 0, 220));
                    }

                    g2.fillOval(itemX - ITEM_SIZE / 2, itemY - ITEM_SIZE / 2, ITEM_SIZE, ITEM_SIZE);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(itemX - ITEM_SIZE / 2, itemY - ITEM_SIZE / 2, ITEM_SIZE, ITEM_SIZE);

                    g2.setFont(new Font("맑은 고딕", Font.BOLD, 16));
                    String icon = "FREEZE".equals(item.type) ? "❄" : "⏱";
                    g2.drawString(icon, itemX - 8, itemY + 5);
                }

               
                if (cursorImage != null && myMousePoint.x > -50) {
                    g2.drawImage(cursorImage, myMousePoint.x, myMousePoint.y, PLAYER_SIZE, PLAYER_SIZE, this);
                }

               
                for (Map.Entry<String, RemoteCursor> entry : remoteCursors.entrySet()) {
                    RemoteCursor rc = entry.getValue();

                    int drawX = (int) (rc.position.x * scale) + offsetX;
                    int drawY = (int) (rc.position.y * scale) + offsetY;

                    Image img = getCursorImageByIndex(rc.cursorIndex);

                    if (img != null) {
                        g2.drawImage(img, drawX, drawY, PLAYER_SIZE, PLAYER_SIZE, this);
                        g2.setColor(Color.YELLOW);
                        g2.setFont(new Font("맑은 고딕", Font.BOLD, 12));
                        g2.drawString(entry.getKey() + "(C" + rc.cursorIndex + ")", drawX, drawY - 5);
                    }
                }

               
                if (isGameActive) {
                    Point center = new Point(myMousePoint.x + TIP_OFFSET_X, myMousePoint.y + TIP_OFFSET_Y);
                    float[] dist = {0.0f, 1.0f};
                    Color[] colors = {new Color(0, 0, 0, 0), new Color(0, 0, 0, 250)};

                    if (myMousePoint.x > -100) {
                        RadialGradientPaint p = new RadialGradientPaint(center, FLASHLIGHT_RADIUS, dist, colors);
                        g2.setPaint(p);
                        g2.fillRect(0, 0, panelW, panelH);
                    }
                }

             
                if (isFrozen) {
                    g2.setColor(new Color(100, 150, 255, 100));
                    g2.fillRect(0, 0, panelW, panelH);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("맑은 고딕", Font.BOLD, 24));
                    String freezeText = "FROZEN!";
                    int textWidth = g2.getFontMetrics().stringWidth(freezeText);
                    g2.drawString(freezeText, (panelW - textWidth) / 2, panelH / 2);
                }

            } else {
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, panelW, panelH);
                g2.setColor(Color.WHITE);
                g2.drawString("Loading...", panelW / 2 - 30, panelH / 2);
            }
        }

        class ItemData {
            Point position;
            String type;

            ItemData(Point p, String t) {
                this.position = p;
                this.type = t;
            }
        }

        class GameMark {
            Point p;
            boolean correct;
            long expiryTime;
            String finderName; 

            // 오답용
            GameMark(Point p, boolean correct) {
                this.p = p;
                this.correct = false;   
                this.finderName = null;
                this.expiryTime = System.currentTimeMillis() + 1000; 
            }

            // 정답용
            GameMark(Point p, boolean correct, String finderName) {
                this.p = p;
                this.correct = correct;
                this.finderName = finderName;
                this.expiryTime = correct ? -1 : System.currentTimeMillis() + 1000;
            }
        }

        class HintMark {
            Point position;

            HintMark(Point p) {
                this.position = p;
            }
        }
    }

    
    static class RemoteCursor {
        Point position;
        int cursorIndex;

        RemoteCursor(Point p, int idx) {
            this.position = p;
            this.cursorIndex = idx;
        }
    }
}
