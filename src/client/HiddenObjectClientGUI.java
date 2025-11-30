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
import java.util.HashMap;
import java.util.Map;

public class HiddenObjectClientGUI extends BaseGameGUI {
    private JTextArea chatArea;
    private JTextField inputField;

    private final Map<Integer, String> emotes = new HashMap<>();
    
    private Image[] cursorImages;
    private Image singleCursorImage;
    private final Map<String, Point2D.Double> otherPlayerCursors = new HashMap<>();

    public HiddenObjectClientGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                                 String playerName, GamePacket roundStartPacket,
                                 GameLauncher launcher) {
        super(socket, in, out, playerName, launcher);

        this.gameMode = roundStartPacket.getGameMode();
        this.playerIndexMap = roundStartPacket.getPlayerIndexMap();

        loadCursorImages();
        initEmotes();

        handlePacket(roundStartPacket);

        pack();
        setResizable(false);
        setTitle("숨은 그림 찾기 (" + gameMode + ") - " + playerName);
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
        
        // 내 로컬 커서 숨기기
        setLocalCursorInvisible();
    }
    
    private void setLocalCursorInvisible() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Image transparent = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Cursor invisible = tk.createCustomCursor(transparent, new Point(0, 0), "Invisible");
        gameBoardPanel.setCursor(invisible);
    }

    private void initEmotes() {
        emotes.put(1, "화이팅!");
        emotes.put(2, "좋아요!");
        emotes.put(3, "힘내요!");
        emotes.put(4, "GG!");
    }

    @Override
    protected String getGameTitle() {
        return "숨은 그림 찾기 - " + playerName;
    }

    @Override
    protected JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0));

        // 상단 - 상태창
        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);

        // 하단 - 채팅창
        chatArea = new JTextArea("[채팅창]\n");
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusScroll, chatScroll);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(200);
        splitPane.setEnabled(false);
        splitPane.setDividerLocation(0.5);
        rightPanel.add(splitPane, BorderLayout.CENTER);

        // 최하단 - 점수판
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

    @Override
    protected JPanel createBottomPanel() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel hintLabel = new JLabel("/Q: 힌트  /H: 도움말  /1~4: 감정표현  ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomBar.add(hintLabel, BorderLayout.WEST);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        JButton sendButton = new JButton("전송");
        
        // 엔터키 및 버튼 이벤트 연결
        ActionListener sendAction = e -> sendChat();
        inputField.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);

        return bottomBar;
    }

    @Override
    protected BaseGameBoardPanel createGameBoardPanel() {
        BaseGameBoardPanel panel = new BaseGameBoardPanel() {
            private Point myLocalMouse = new Point(-100, -100);
            
            private final Color[] PLAYER_COLORS = { 
                    Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE 
                };
            
            @Override
            public void setRoundData(String path, java.util.List<Rectangle> answers, Dimension dim) {
                this.originalAnswers = answers;
                this.originalDimension = dim;
                this.foundStatus = new boolean[answers.size()];
                try {
                    backgroundImage = new ImageIcon(path).getImage();
                    if(backgroundImage.getWidth(null) == -1) throw new IOException("로드 실패");
                    
                    int baseWidth = 500;
                    double ratio = (double) dim.height / dim.width;
                    int newHeight = (int)(baseWidth * ratio);
                    setPreferredSize(new Dimension(baseWidth, newHeight));
                } catch(Exception e) {
                    appendStatus("[에러] 이미지 로드 실패\n");
                }
                clearMarks();
            }
            
            @Override
            protected void drawHints(Graphics2D g2) {
                if (!blinkState) return;
                double scale = getScale();
                Point offset = getOffset();
                
                for (HintMark hint : hints) {
                    int hx = (int)(offset.x + hint.position.x * scale);
                    int hy = (int)(offset.y + hint.position.y * scale);
                    
                    // 노란색 원 + 굵은 테두리
                    g2.setColor(new Color(255, 255, 0, 200));
                    g2.setStroke(new BasicStroke(4));
                    g2.drawOval(hx - 25, hy - 25, 50, 50);
                    
                    // 별 텍스트
                    g2.setColor(Color.YELLOW);
                    g2.setFont(new Font("Dialog", Font.BOLD, 30));
                    g2.drawString("★", hx - 15, hy + 10);
                }
            }

            @Override
            protected void drawMarks(Graphics2D g2) {
                double scale = getScale();
                Point offset = getOffset();
                
                for (GameMark m : marks) {
                    int mx = (int)(offset.x + m.p.x * scale);
                    int my = (int)(offset.y + m.p.y * scale);
                    
                    if (m.correct) {
                        // 정답 원
                        if ("경쟁".equals(gameMode)) {
                            g2.setColor(m.color != null ? m.color : Color.GREEN);
                        } else {
                            g2.setColor(new Color(0, 255, 0, 200));
                        }
                        g2.setStroke(new BasicStroke(3));
                        g2.drawOval(mx - 20, my - 20, 40, 40); // 반지름 20, 지름 40
                    } else {
                        // 오답 빨간색 X
                        g2.setColor(Color.RED);
                        g2.setFont(new Font("Dialog", Font.BOLD, 28));
                        g2.drawString("X", mx - 10, my + 10);
                    }
                }
            }

            @Override
            protected void paintComponent(Graphics g) {
                // 배경, 힌트, 마크 그리기
                super.paintComponent(g); 

                Graphics2D g2 = (Graphics2D) g;
                double scale = getScale();
                Point offset = getOffset();

                // 다른 플레이어 커서 그리기
                if ("협동".equals(gameMode) && playerIndexMap.size() > 1) {
                    for (Map.Entry<String, Point2D.Double> entry : otherPlayerCursors.entrySet()) {
                        String name = entry.getKey();
                        Point2D.Double p = entry.getValue();

                        int drawX = (int) (offset.x + p.x * scale);
                        int drawY = (int) (offset.y + p.y * scale);

                        int idx = playerIndexMap.getOrDefault(name, 0);
                        if (idx >= 0 && idx < 5 && cursorImages[idx] != null) {
                            g2.drawImage(cursorImages[idx], drawX, drawY, 30, 30, HiddenObjectClientGUI.this);
                            g2.setColor(Color.WHITE);
                            g2.setFont(new Font("Dialog", Font.BOLD, 10));
                            g2.drawString(name, drawX, drawY);
                        }
                    }
                }

                // 내 커서 그리기
                if (isGameActive && myLocalMouse.x > -50) {
                    int myIdx = playerIndexMap.getOrDefault(playerName, 0);
                    Image myImg = (playerIndexMap.size() <= 1) ? singleCursorImage : 
                                  cursorImages[Math.max(0, Math.min(myIdx, 4))];
                    
                    if (myImg != null) {
                        g2.drawImage(myImg, myLocalMouse.x, myLocalMouse.y, 30, 30, HiddenObjectClientGUI.this);
                    }
                }
            }

            {
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        myLocalMouse = e.getPoint();
                        repaint();
                        if (!isGameActive) return;

                        Point2D.Double gamePos = toGameCoords(e.getPoint());
                        int myIdx = playerIndexMap != null ? playerIndexMap.getOrDefault(playerName, 0) : 0;
                        sendPacket(new GamePacket(GamePacket.Type.MOUSE_MOVE, playerName, myIdx, gamePos.x, gamePos.y));
                    }
                });

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (!isGameActive) return;

                        Point2D.Double gamePos = toGameCoords(e.getPoint());
                        int foundIndex = checkHit(gamePos.x, gamePos.y);

                        if (foundIndex != -1) {
                            sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                        } else {
                            GamePacket miss = new GamePacket(GamePacket.Type.CLICK, playerName, -1);
                            miss.setX(gamePos.x);
                            miss.setY(gamePos.y);
                            sendPacket(miss);
                        }
                    }
                });
            }
        };
        
        return panel;
    }

    @Override
    protected void onRoundStart(GamePacket p) {
        otherPlayerCursors.clear();
        super.onRoundStart(p);
    }

    @Override
    protected void onPacketReceived(GamePacket p) {
        switch (p.getType()) {
            case MOUSE_MOVE:
                if (!p.getSender().equals(playerName)) {
                    otherPlayerCursors.put(p.getSender(), new Point2D.Double(p.getX(), p.getY()));
                    gameBoardPanel.repaint();
                }
                break;

            case HINT_RESPONSE:
            	this.hintsRemaining = p.getRemainingHints();
                Point hintPos = p.getHintPosition();
                if (hintPos != null) {
                    gameBoardPanel.addHint(hintPos);
                    appendStatus(p.getMessage() + "\n");
                } else {
                    appendStatus("[힌트] " + p.getMessage() + "\n");
                }
                updateScoreDisplay();
                break;
                
            case LOBBY_UPDATE:
                 appendStatus("방장: " + p.getHostName() + ", 설정 변경됨\n");
                 break;
                 
            default:
                break;
        }
    }

    private void handleResult(GamePacket p) {
        boolean isCorrect = p.isCorrect();
        String sender = p.getSender();

        if (isCorrect) {
            // 정답 처리
            int idx = p.getAnswerIndex();
            Point center = new Point(0,0);
            if (gameBoardPanel.originalAnswers != null && idx < gameBoardPanel.originalAnswers.size()) {
                Rectangle r = gameBoardPanel.originalAnswers.get(idx);
                center = new Point(r.x + r.width/2, r.y + r.height/2);
                gameBoardPanel.foundStatus[idx] = true;
            }

            // 경쟁 모드 색상 설정
            Color markColor = Color.GREEN;
            if ("경쟁".equals(gameMode)) {
                int pIdx = playerIndexMap.getOrDefault(sender, 0);
                Color[] colors = {Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE};
                markColor = colors[Math.max(0, Math.min(pIdx, 4))];
            }

            gameBoardPanel.addMark(center, true, markColor);
            globalFoundCount++;

            if (playerName.equals(sender)) {
                myFoundCount++;
            }
            
            if (p.getMessage() != null) appendStatus(p.getMessage() + "\n");

        } else {
            // 오답 처리 - 협동이거나 내가 틀렸을 때만 화면에 표시
            if ("협동".equals(gameMode) || playerName.equals(sender)) {
                gameBoardPanel.addMark(new Point((int)p.getX(), (int)p.getY()), false, null);
            }
            if (p.getMessage() != null && !p.getMessage().isEmpty()) {
                appendStatus(p.getMessage() + "\n");
            }
        }
        updateScoreDisplay();
    }

    private void handleScoreUpdate(GamePacket p) {
        String msg = p.getMessage();
        if (msg == null) return;

        if ("협동".equals(gameMode) && msg.startsWith("SCORE_COOP:")) {
            try {
                String num = msg.substring(11).trim();
                if (!num.isEmpty()) {
                    currentTeamScore = Integer.parseInt(num);
                }
            } catch (Exception e) {}
        }
        
        else if ("경쟁".equals(gameMode) && msg.startsWith("[점수]")) {
            try {
                String[] lines = msg.split("\n");
                for (String line : lines) {
                    if (line.contains(playerName)) {
                        String scoreStr = line.replaceAll("[^0-9-]", ""); 
                        if (!scoreStr.isEmpty()) {
                            myScore = Integer.parseInt(scoreStr);
                        }
                        break; 
                    }
                }
            } catch (Exception e) {}
        }
        updateScoreDisplay();
    }

    private void sendChat() {
        String raw = inputField.getText().trim();
        if (raw.isEmpty()) return;

        String text = raw;

        // 빠른 채팅 (/1 ~ /4)
        if (raw.startsWith("/") && raw.length() > 1) {
            try {
                int num = Integer.parseInt(raw.substring(1));
                if (emotes.containsKey(num)) {
                    text = emotes.get(num);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 힌트 명령
        if (raw.equalsIgnoreCase("/Q")) {
        	if (hintsRemaining <= 0) {
                appendStatus("[힌트] 이번 라운드의 모든 힌트를 사용했습니다.\n");
                inputField.setText("");
                return;
            }
            sendPacket(new GamePacket(GamePacket.Type.HINT_REQUEST, playerName, "HINT"));
            inputField.setText("");
            return;
        }

        // 도움말
        if (raw.equalsIgnoreCase("/H")) {
            showHelpDialog();
            inputField.setText("");
            return;
        }

        // 일반 메시지
        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, text));
        inputField.setText("");
    }
    
    @Override
    protected void onChatMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void showHelpDialog() {
        JOptionPane.showMessageDialog(this,
            "🎮 멀티플레이 조작법\n" +
            " - 마우스: 커서 이동 및 클릭\n" +
            " - /Q: 힌트 요청 (공유 횟수 차감)\n" +
            " - /1~/4: 빠른 감정표현\n" +
            " - ESC: 나가기", 
            "도움말", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    protected void updateScoreDisplay() {
        int displayScore = "협동".equals(gameMode) ? currentTeamScore : myScore;
        String hintText = "힌트: " + hintsRemaining + "/3 (" + ("경쟁".equals(gameMode) ? "개인" : "공유") + ")";
        
        String countText;
        if ("협동".equals(gameMode)) {
            countText = "찾은 개수: " + globalFoundCount + "/" + totalAnswers;
        } else {
            int remaining = Math.max(0, totalAnswers - globalFoundCount);
            countText = "내 개수: " + myFoundCount + " (남은 정답: " + remaining + ")";
        }

        scoreArea.setText(
            "점수: " + displayScore + "점\n" +
            countText + "\n" +
            hintText + "\n" +
            "남은 시간: " + timeLeft + "초"
        );
    }
    
    @Override
    protected void addExperience() {
        // 게임 종료 후 경험치 정산 로직
        UserData userData = UserData.getInstance();
        if (userData != null) {
            int calcScore;
            if ("협동".equals(gameMode)) {
                calcScore = myFoundCount * 10;  // 협동 - 기여도 기반
            } else {
                calcScore = myScore;  // 경쟁 - 내 점수
            }
            
            int expGain = 50 + (calcScore / 2);
            if (expGain < 0) expGain = 0;

            userData.addExperience(expGain);
            appendStatus("[경험치 획득: " + expGain + " EXP]\n");
        }
    }
}