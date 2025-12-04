package client;

import model.GamePacket;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashlightGame extends BaseGameGUI {
    private static final int PLAYER_SIZE = 40;
    private static final int FLASHLIGHT_RADIUS = 150;
    private static final int MOVE_SPEED = 5;

    private static final int TIP_OFFSET_X = 13;
    private static final int TIP_OFFSET_Y = 0;
    private static final int ITEM_SIZE = 30;
    
    private int cursorIndex = 1;
    private Point myCursorPos = new Point(250, 200); // 내 커서(손전등) 위치
    
    private final boolean[] keys = new boolean[256];
    private Timer moveTimer;
    private long lastSendTime = 0;
    
    private final Map<Integer, ItemData> items = new HashMap<>();
    private final Map<String, RemoteCursor> remoteCursors = new HashMap<>();
    
    private boolean isFrozen = false;
    private Timer freezeTimer;
    
    private Image myCursorImage;
    private final Map<Integer, Image> cursorImageCache = new HashMap<>();

    private JTextField chatInput;
    private JTextArea chatArea;
    private final Map<Integer, String> emotes = new HashMap<>();

    public FlashlightGame(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                          String playerName, String difficulty, GamePacket startPacket,
                          GameLauncher launcher) {
        super(socket, in, out, playerName, launcher);
        
        // 초기 데이터 설정
        if (startPacket != null) {
            this.gameMode = startPacket.getGameMode();
            this.cursorIndex = startPacket.getCursorIndex();
            if (startPacket.getPlayerIndexMap() != null) {
                this.playerIndexMap.putAll(startPacket.getPlayerIndexMap());
            }
        }
        
        initResources();
        setLocalCursorInvisible(); // 시스템 커서 숨김
        setupKeyBindings();

        moveTimer = new Timer(16, e -> updatePosition());
        moveTimer.start();

        handlePacket(startPacket); // 첫 라운드 시작
        
        pack();
        setResizable(false);

        setTitle("숨은 그림 찾기 (" + gameMode + ") - " + playerName);
        setVisible(true);
        
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void initResources() {
        // 커서 이미지 로드
        myCursorImage = getCursorImageByIndex(cursorIndex);
        
        // 이모티콘 초기화
        emotes.put(1, "화이팅!");
        emotes.put(2, "좋아요!");
        emotes.put(3, "힘내요!");
        emotes.put(4, "GG!");
    }

    private Image getCursorImageByIndex(int idx) {
        if (cursorImageCache.containsKey(idx)) return cursorImageCache.get(idx);
        try {
            String path = "images/cursor" + (idx + 1) + ".png";
            Image img = new ImageIcon(path).getImage();
            if (img.getWidth(null) == -1) img = new ImageIcon("images/cursor1.png").getImage();
            cursorImageCache.put(idx, img);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private void setLocalCursorInvisible() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Image transparent = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Cursor invisible = tk.createCustomCursor(transparent, new Point(0, 0), "Invisible");
        gameBoardPanel.setCursor(invisible);
    }

    @Override
    protected String getGameTitle() {
        return "플래시 모드 - " + playerName;
    }

    @Override
    protected JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(200, 0)); // 너비 200 고정

        // 상단 - 상태창
        statusArea = new JTextArea("[상태창]\n");
        statusArea.setEditable(false);
        statusArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);

        // 중간 - 채팅창
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

        // 하단 - 점수창
        scoreArea = new JTextArea();
        scoreArea.setEditable(false);
        scoreArea.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        scoreArea.setBackground(Color.BLACK);
        scoreArea.setForeground(Color.GREEN);
        scoreArea.setMargin(new Insets(5, 5, 5, 5));
        scoreArea.setRows(4); // 4줄 확보
        
        rightPanel.add(scoreArea, BorderLayout.SOUTH);
        
        return rightPanel;
    }

    @Override
    protected JPanel createBottomPanel() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JLabel hintLabel = new JLabel("방향키: 이동  스페이스바: 선택  /H: 도움말  /1~4: 감정표현  ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomBar.add(hintLabel, BorderLayout.WEST);
        
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        chatInput = new JTextField();
        JButton sendButton = new JButton("전송");
        sendButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        
        ActionListener sendAction = e -> sendChat();
        chatInput.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);
        
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        bottomBar.add(inputPanel, BorderLayout.CENTER);
        
        return bottomBar;
    }

    @Override
    protected BaseGameBoardPanel createGameBoardPanel() {
        return new BaseGameBoardPanel() {
        	@Override
            public void setRoundData(String path, List<Rectangle> answers, Dimension dim) {
                this.originalAnswers = answers;
                this.originalDimension = dim;
                this.foundStatus = new boolean[answers.size()];

                try {
                    backgroundImage = new ImageIcon(path).getImage();
                    if (backgroundImage.getWidth(null) == -1) {
                    	throw new Exception("이미지 파일 로드 실패: " + path);
                    }

                    int baseWidth = 500;
                    double ratio = (double) dim.height / dim.width;
                    int newHeight = (int) (baseWidth * ratio);
                    
                    setPreferredSize(new Dimension(baseWidth, newHeight));

                } catch (Exception e) {
                    e.printStackTrace();
                    appendStatus("[에러] 이미지 로드 실패\n");
                }
                clearMarks();
                items.clear(); 
            }
        	
        	@Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int panelW = getWidth();
                int panelH = getHeight();
                
                // 배경
                if (backgroundImage == null || originalDimension == null) {
                    g2.setColor(Color.BLACK);
                    g2.fillRect(0, 0, panelW, panelH);
                    g2.setColor(Color.WHITE);
                    g2.drawString("Loading...", panelW/2 - 30, panelH/2);
                    return;
                }

                // 좌표 계산
                double scale = getScale();
                Point offset = getOffset();
                
                // 배경 그리기
                drawBackground(g2); 

                // 힌트
                if (blinkState) {
                    for (HintMark hint : hints) {
                        int hx = (int)(offset.x + hint.position.x * scale);
                        int hy = (int)(offset.y + hint.position.y * scale);
                        
                        g2.setColor(new Color(255, 255, 0, 200));
                        g2.setStroke(new BasicStroke(4));
                        g2.drawOval(hx - 25, hy - 25, 50, 50);
                        
                        g2.setColor(Color.YELLOW);
                        g2.setFont(new Font("Dialog", Font.BOLD, 30));
                        g2.drawString("★", hx - 15, hy + 10);
                    }
                }

                // 마크
                for (GameMark m : marks) {
                    int mx = (int)(offset.x + m.p.x * scale);
                    int my = (int)(offset.y + m.p.y * scale);
                    
                    if (m.correct) {
                        g2.setColor(m.color != null ? m.color : new Color(0, 255, 0, 200));
                        g2.setStroke(new BasicStroke(3));
                        g2.drawOval(mx - 20, my - 20, 40, 40);
                    } else {
                        g2.setColor(Color.RED);
                        g2.setFont(new Font("Dialog", Font.BOLD, 28));
                        g2.drawString("X", mx - 10, my + 10);
                    }
                }

                // 아이템
                for (ItemData item : items.values()) {
                    int ix = (int)(offset.x + item.pos.x * scale);
                    int iy = (int)(offset.y + item.pos.y * scale);
                    
                    if ("FREEZE".equals(item.type)) {
                        g2.setColor(new Color(100, 150, 255, 220));
                    } else {
                        g2.setColor(new Color(255, 215, 0, 220)); // HINT or TIME
                    }
                    
                    g2.fillOval(ix - 15, iy - 15, 30, 30);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(ix - 15, iy - 15, ITEM_SIZE, ITEM_SIZE);
                    
                    g2.setFont(new Font("Dialog", Font.BOLD, 16));
                    String icon = "FREEZE".equals(item.type) ? "❄" : "⏱"; // 시계 아이콘
                    if ("HINT".equals(item.type)) icon = "★";
                    g2.drawString(icon, ix - 8, iy + 5);
                }

                // 다른 플레이어 커서
                for (Map.Entry<String, RemoteCursor> entry : remoteCursors.entrySet()) {
                    RemoteCursor rc = entry.getValue();
                    int rx = (int)(offset.x + rc.pos.x * scale);
                    int ry = (int)(offset.y + rc.pos.y * scale);
                    
                    Image rImg = getCursorImageByIndex(rc.cursorIndex);
                    if (rImg != null) {
                        g2.drawImage(rImg, rx, ry, PLAYER_SIZE, PLAYER_SIZE, FlashlightGame.this);
                        g2.setColor(Color.YELLOW);
                        g2.setFont(new Font("Dialog", Font.BOLD, 12));
                        g2.drawString(entry.getKey(), rx, ry - 5);
                    }
                }

                // 내 커서
                if (myCursorImage != null && myCursorPos.x > -50) {
                    g2.drawImage(myCursorImage, myCursorPos.x, myCursorPos.y, PLAYER_SIZE, PLAYER_SIZE, FlashlightGame.this);
                }

                // 어둠 효과
                if (isGameActive && myCursorPos.x > -100) {
                    Point center = new Point(myCursorPos.x + TIP_OFFSET_X, myCursorPos.y + TIP_OFFSET_Y);
                    
                    float[] dist = {0.0f, 1.0f};
                    Color[] colors = {new Color(0,0,0,0), new Color(0,0,0,250)};
                    RadialGradientPaint p = new RadialGradientPaint(center, FLASHLIGHT_RADIUS, dist, colors);
                    
                    g2.setPaint(p);
                    g2.fillRect(0, 0, panelW, panelH);
                }
                else if (!isGameActive && isCountdownActive) {
                	g2.setColor(Color.BLACK); 
                	g2.fillRect(0, 0, panelW, panelH);
                }

                // 얼음 효과
                if (isFrozen) {
                    g2.setColor(new Color(100, 150, 255, 100)); // 반투명 파랑
                    g2.fillRect(0, 0, panelW, panelH);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Dialog", Font.BOLD, 30));
                    String freezeText = "FROZEN!";
                    int textWidth = g2.getFontMetrics().stringWidth(freezeText);
                    g2.drawString(freezeText, (panelW - textWidth)/2, panelH/2);
                }
                drawCountdown(g2, panelW, panelH);
            }
        };
    }
    
    @Override
    protected void onRoundStart(GamePacket p) {
        super.onRoundStart(p); 
        
        items.clear();
        remoteCursors.clear();
        
        // 화면 중앙 배치
        if (gameBoardPanel.getWidth() > 0) {
            myCursorPos = new Point(gameBoardPanel.getWidth()/2, gameBoardPanel.getHeight()/2);
        }
    }

    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        
        // 방향키 Press/Release 바인딩
        int[] keyCodes = {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT};
        String[] keyNames = {"UP", "DOWN", "LEFT", "RIGHT"};
        
        for (int i = 0; i < keyCodes.length; i++) {
            int code = keyCodes[i];
            String name = keyNames[i];
            
            im.put(KeyStroke.getKeyStroke(code, 0, false), name + "_P");
            im.put(KeyStroke.getKeyStroke(code, 0, true), name + "_R");
            
            int finalCode = code;
            am.put(name + "_P", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { if(!chatInput.hasFocus()) keys[finalCode] = true; }
            });
            am.put(name + "_R", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { keys[finalCode] = false; }
            });
        }
        
        // Space - 선택
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "SPACE");
        am.put("SPACE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!chatInput.hasFocus() && !isFrozen) checkClick();
            }
        });
    }

    private void updatePosition() {
        if (!isGameActive || isFrozen) return;
        
        boolean moved = false;
        if (keys[KeyEvent.VK_UP] && myCursorPos.y > 0) { myCursorPos.y -= MOVE_SPEED; moved = true; }
        if (keys[KeyEvent.VK_DOWN] && myCursorPos.y < gameBoardPanel.getHeight() - PLAYER_SIZE) { myCursorPos.y += MOVE_SPEED; moved = true; }
        if (keys[KeyEvent.VK_LEFT] && myCursorPos.x > 0) { myCursorPos.x -= MOVE_SPEED; moved = true; }
        if (keys[KeyEvent.VK_RIGHT] && myCursorPos.x < gameBoardPanel.getWidth() - PLAYER_SIZE) { myCursorPos.x += MOVE_SPEED; moved = true; }
        
        if (moved) {
            gameBoardPanel.repaint();
            long now = System.currentTimeMillis();
            if (now - lastSendTime > 50) {
                sendCursorPosition();
                lastSendTime = now;
            }
        }
    }
    
    private void sendCursorPosition() {
        if (gameBoardPanel.originalDimension == null) return;
        
        Point2D.Double gamePos = gameBoardPanel.toGameCoords(myCursorPos);
        
        GamePacket p = new GamePacket(GamePacket.Type.MOUSE_MOVE, playerName, 0, gamePos.x, gamePos.y);
        p.setCursorIndex(cursorIndex);
        sendPacket(p);
    }
    
    private void checkClick() {
        if (!isGameActive || isFrozen || gameBoardPanel.originalDimension == null) return;

        // 손전등 끝 위치 계산
        Point tipPos = new Point(myCursorPos.x + TIP_OFFSET_X, myCursorPos.y + TIP_OFFSET_Y);
        
        Point2D.Double gamePos = gameBoardPanel.toGameCoords(tipPos);
        double originalX = gamePos.x;
        double originalY = gamePos.y;
        
        // 아이템 획득 체크
        for (Map.Entry<Integer, ItemData> entry : items.entrySet()) {
            ItemData item = entry.getValue();
            double dx = originalX - item.pos.x;
            double dy = originalY - item.pos.y;
            if (Math.sqrt(dx*dx + dy*dy) <= ITEM_SIZE) {
                sendPacket(new GamePacket(GamePacket.Type.ITEM_PICKUP, playerName, entry.getKey(), null));
                items.remove(entry.getKey());
                gameBoardPanel.repaint();
                return; 
            }
        }
        
        int foundIndex = gameBoardPanel.checkHit(originalX, originalY);
        
        if (foundIndex != -1) {
            GamePacket p = new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex);
            p.setX(originalX); p.setY(originalY);
            sendPacket(p);
        } else {
            GamePacket p = new GamePacket(GamePacket.Type.CLICK, playerName, -1);
            p.setX(originalX); p.setY(originalY);
            sendPacket(p);
            // 오답 마크 추가
            gameBoardPanel.addMark(new Point((int)originalX, (int)originalY), false, null);
        }
    }

    @Override
    protected void onPacketReceived(GamePacket p) {
        switch (p.getType()) {
            case MOUSE_MOVE:
                if (!p.getSender().equals(playerName) && !"경쟁".equals(gameMode)) {
                    remoteCursors.put(p.getSender(), new RemoteCursor(
                        new Point((int)p.getX(), (int)p.getY()), p.getCursorIndex()));
                    gameBoardPanel.repaint();
                }
                break;
                
            case ITEM_SPAWN:
                items.put(p.getItemId(), new ItemData(p.getItemPosition(), p.getItemType()));
                appendStatus("[아이템] " + p.getItemType() + " 등장!\n");
                gameBoardPanel.repaint();
                break;
                
            case ITEM_REMOVED:
                items.remove(p.getItemId());
                gameBoardPanel.repaint();
                break;
                
            case PLAYER_FREEZE:
                if (playerName.equals(p.getMessage())) {
                    freezePlayer(p.getFreezeDuration());
                }
                break;
                
            case TIME_BONUS:
                timeLeft += 5;
                timerLabel.setText("타이머: " + timeLeft + "초");
                appendStatus("[보너스] 시간 +5초!\n");
                break;
                
            case HINT_RESPONSE:
            	this.hintsRemaining = p.getRemainingHints(); // 힌트 동기화
                if (p.getHintPosition() != null) {
                    gameBoardPanel.addHint(p.getHintPosition());
                    appendStatus("[아이템 효과] 정답 위치가 표시됩니다!\n");
                }
                updateScoreDisplay();
                break;
                
            default: break;
        }
    }

    private void freezePlayer(int duration) {
        isFrozen = true;
        appendStatus("[경고] 얼음 공격을 받았습니다! (" + duration + "초)\n");
        if (freezeTimer != null) freezeTimer.stop();
        freezeTimer = new Timer(duration * 1000, e -> {
            isFrozen = false;
            appendStatus("[해제] 다시 움직일 수 있습니다!\n");
            gameBoardPanel.repaint();
        });
        freezeTimer.setRepeats(false);
        freezeTimer.start();
        gameBoardPanel.repaint();
    }
    
    private void sendChat() {
        String txt = chatInput.getText().trim();
        if (txt.isEmpty()) { requestFocusInWindow(); return; }
        
        // 빠른 채팅 변환
        if (txt.startsWith("/") && txt.length() > 1) {
            try {
                int id = Integer.parseInt(txt.substring(1));
                if (emotes.containsKey(id)) txt = emotes.get(id);
            } catch (Exception e) {}
        }
        
        if (txt.equalsIgnoreCase("/Q")) {
            appendStatus("[알림] 플래시 모드에서는 '/Q'로 힌트를 사용할 수 없습니다. 맵에 있는 힌트 아이템(★)을 획득하세요.\n");
            chatInput.setText("");
            requestFocusInWindow();
            return;
        }
        
        if (txt.equalsIgnoreCase("/H")) {
        	showHelpDialog();
        } else {
            sendPacket(new GamePacket(GamePacket.Type.MESSAGE, playerName, txt));
        }
        chatInput.setText("");
        requestFocusInWindow(); // 채팅 후 포커스 유지
    }

    @Override
    protected void onChatMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
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

    @Override
    protected void updateScoreDisplay() {
        int displayScore = "협동".equals(gameMode) ? currentTeamScore : myScore;
        String countText;
        if ("협동".equals(gameMode)) {
            countText = "전체 찾은 개수: " + globalFoundCount + "/" + totalAnswers;
        } else {
        	int remaining = Math.max(0, totalAnswers - globalFoundCount);
            countText = "내 개수: " + myFoundCount + " (남은 정답: " + remaining + ")";
        }
        scoreArea.setText("점수: " + displayScore + "\n" + countText + "\n남은 시간: " + timeLeft);
    }
    
    @Override
    public void dispose() {
        if(moveTimer != null) moveTimer.stop();
        if(freezeTimer != null) freezeTimer.stop();
        super.dispose();
    }
    
    static class ItemData {
        Point pos;
        String type;
        ItemData(Point p, String t) { pos = p; type = t; }
    }
    
    static class RemoteCursor {
        Point pos;
        int cursorIndex;
        RemoteCursor(Point p, int idx) { pos = p; cursorIndex = idx; }
    }
}