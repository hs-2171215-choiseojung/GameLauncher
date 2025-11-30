package client;

import model.GamePacket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SinglePlayerGUI extends BaseGameGUI {

    private final String difficulty;

    public SinglePlayerGUI(Socket socket, ObjectInputStream in, ObjectOutputStream out,
                           String playerName, String difficulty, GamePacket roundStartPacket, GameLauncher launcher) {
        super(socket, in, out, playerName, launcher);
        this.difficulty = difficulty;

        // 싱글 플레이어 전용 커서 설정
        initCustomCursor();

        // 키 바인딩 설정 (Q, H, ESC)
        setupKeyBindings();

        // 첫 라운드 패킷 처리
        handlePacket(roundStartPacket);

        // 창 설정 마무리
        pack();
        setResizable(false);
        setVisible(true);
    }

    private void initCustomCursor() {
        try {
            Toolkit tk = Toolkit.getDefaultToolkit();
            Image singleCursorImage = new ImageIcon("images/singleMouse.png").getImage();
            
            // 이미지가 로드되지 않았을 경우 기본 커서 사용 방지용 체크
            if (singleCursorImage.getWidth(null) != -1) {
                Cursor customCursor = tk.createCustomCursor(singleCursorImage, new Point(0, 0), "SingleCursor");
                this.setCursor(customCursor);
            }
        } catch (Exception e) {
            System.out.println("[SinglePlayerGUI] 커서 설정 실패: " + e.getMessage());
        }
    }

    private void setupKeyBindings() {
        JRootPane root = getRootPane();
        
        // Q - 힌트
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "HINT");
        root.getActionMap().put("HINT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestHint();
            }
        });

        // H - 도움말
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "HELP");
        root.getActionMap().put("HELP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });
    }

    private void requestHint() {
    	if (!isGameActive) {
            appendStatus("[알림] 게임이 진행 중일 때만 힌트를 사용할 수 있습니다.\n");
            return;
        }
    	
    	if (hintsRemaining <= 0) {
            appendStatus("[알림] 남은 힌트가 없습니다. (0/3)\n");
            return;
        }
        
        // 힌트 사용 시 즉시 5점 감점
        myScore -= 5;
        hintsRemaining--; 
        
        updateScoreDisplay();
        
        // 서버로 힌트 요청 패킷 전송
        sendPacket(new GamePacket(GamePacket.Type.HINT_REQUEST, playerName, "HINT"));
        appendStatus("[요청] 힌트 사용 (-5점)\n");
    }
    
    private void showHelpDialog() {
        JOptionPane.showMessageDialog(this,
            "🎮 게임 조작법\n\n" +
            "🖱 마우스 왼쪽 클릭: 정답 찾기\n" +
            "⌨ Q 키: 힌트 사용 (최대 3회)\n" +
            "⌨ H 키: 도움말 확인\n" +
            "⌨ ESC 키: 게임 종료", 
            "도움말", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    protected String getGameTitle() {
        return "숨은 그림 찾기 - 1인 플레이 (" + playerName + ")";
    }

    @Override
    protected JPanel createBottomPanel() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(230, 230, 230));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JLabel hintLabel = new JLabel("Q: 힌트 (3회)    H: 도움말     ESC: 종료");
        hintLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        bottomBar.add(hintLabel, BorderLayout.WEST);
        
        return bottomBar;
    }

    @Override
    protected BaseGameBoardPanel createGameBoardPanel() {
        BaseGameBoardPanel panel = new BaseGameBoardPanel();
        
        // 싱글 플레이어 전용 클릭 리스너
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isGameActive) return;

                // 좌표 변환
                Point2D.Double gamePos = panel.toGameCoords(e.getPoint());
                int foundIndex = panel.checkHit(gamePos.x, gamePos.y);

                if (foundIndex != -1) {
                    // 정답 패킷 전송
                    sendPacket(new GamePacket(GamePacket.Type.CLICK, playerName, foundIndex));
                } else {
                    // 오답 처리
                    panel.addMark(new Point((int)gamePos.x, (int)gamePos.y), false, null); 
                    
                    // 감점 처리
                    myScore = Math.max(0, myScore - 5);
                    updateScoreDisplay();
                    appendStatus("[오답] -5점\n");
                }
            }
        });
        return panel;
    }

    @Override
    protected void onRoundStart(GamePacket p) {
        super.onRoundStart(p);
        
        // 찾은 개수 초기화
        this.myFoundCount = 0; 
        
        // 힌트 개수 리셋
        this.hintsRemaining = 3; 
        
        // 점수판 업데이트
        updateScoreDisplay();
    }
    
    @Override
    protected void handleCommonResult(GamePacket p) {
        boolean correct = p.isCorrect();
        
        if (correct) {
            // 마크 표시
            int idx = p.getAnswerIndex();
            Point center = new Point(0,0);
            if (gameBoardPanel.originalAnswers != null && idx < gameBoardPanel.originalAnswers.size()) {
                Rectangle r = gameBoardPanel.originalAnswers.get(idx);
                center = new Point(r.x + r.width/2, r.y + r.height/2);
                gameBoardPanel.foundStatus[idx] = true;
            }
            gameBoardPanel.addMark(center, true, Color.GREEN);
            
            // 점수 및 개수 증가
            myScore += 10;
            myFoundCount++;
            
            appendStatus("[정답] +10점\n");
        } else {

        }
        updateScoreDisplay();
    }

    @Override
    protected void onPacketReceived(GamePacket p) {
        switch (p.getType()) {
            case HINT_RESPONSE:
                Point hintPos = p.getHintPosition();
                hintsRemaining = p.getRemainingHints(); // 남은 힌트 개수 동기화
                
                if (hintPos != null) {
                    gameBoardPanel.addHint(hintPos);
                    appendStatus("[힌트] 정답 위치가 별(★)로 표시되었습니다.\n");
                } else {
                    appendStatus("[힌트] " + p.getMessage() + "\n");
                }
                updateScoreDisplay();
                break;
                
            default:
                break;
        }
    }

    @Override
    protected void updateScoreDisplay() {
        scoreArea.setText(
            "점수: " + myScore + "점\n" +
            "찾은 개수: " + myFoundCount + "/" + totalAnswers + "\n" +
            "힌트: " + hintsRemaining + "/3\n" +
            "남은 시간: " + timeLeft + "초"
        );
    }
}