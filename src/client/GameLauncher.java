package client;

import model.GamePacket;
import model.UserData;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;

// CardLayout을 사용하여 여러 화면을 관리
public class GameLauncher extends JFrame {

    // --- 통신 관련 ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;
    private String selectedDifficulty; 
    private String roomNumber;

    // --- UI 관련 ---
    private CardLayout cardLayout;
    private JPanel mainPanel; 
    private NicknameSetupPanel nicknameSetupPanel;
    private MainMenuPanel mainMenuPanel;
    private HomePanel homePanel;
    private WaitingRoom waitingRoom;
    private MyPagePanel myPagePanel;

    // --- 카드 이름 (식별자) ---
    private static final String CARD_NICKNAME_SETUP = "NICKNAME_SETUP";
    private static final String CARD_MAIN_MENU = "MAIN_MENU";
    private static final String CARD_SERVER_INPUT = "SERVER_INPUT";
    private static final String CARD_LOBBY = "LOBBY";
    private static final String CARD_MYPAGE = "MYPAGE";
    
    // --- 1인 플레이 관련 ---
    private boolean isSinglePlayer = false;

    public GameLauncher() {
        setTitle("숨은 그림 찾기");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 600);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 1. 닉네임 설정 패널
        nicknameSetupPanel = new NicknameSetupPanel(this);
        mainPanel.add(nicknameSetupPanel, CARD_NICKNAME_SETUP);

        // 2. 메인 메뉴 패널
        mainMenuPanel = new MainMenuPanel(this);
        mainPanel.add(mainMenuPanel, CARD_MAIN_MENU);

        // 3. 서버 접속 패널
        homePanel = new HomePanel(this);
        mainPanel.add(homePanel, CARD_SERVER_INPUT);

        // 4. 대기방 패널
        waitingRoom = new WaitingRoom(this);
        mainPanel.add(waitingRoom, CARD_LOBBY);

        // 5. 마이페이지 패널
        myPagePanel = new MyPagePanel(this);
        mainPanel.add(myPagePanel, CARD_MYPAGE);

        add(mainPanel);

        // 시작 화면: 자동 로그인 확인
        UserData userData = UserData.getInstance();
        if (userData != null && userData.getNickname() != null) {
            cardLayout.show(mainPanel, CARD_MAIN_MENU);
        } else {
            cardLayout.show(mainPanel, CARD_NICKNAME_SETUP);
        }

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // 메인 메뉴로 전환
    public void switchToMainMenu() {
        mainMenuPanel.refreshUserInfo();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_MAIN_MENU);
        setTitle("숨은 그림 찾기");
    }
    
    // 닉네임 설정으로 전환 (로그아웃 시)
    public void switchToNicknameSetup() {
        nicknameSetupPanel.resetUI();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_NICKNAME_SETUP);
        setTitle("숨은 그림 찾기");
    }

    // 서버 접속 화면으로 전환
    public void switchToServerInput() {
        isSinglePlayer = false;
        homePanel.updateUserInfo();
        this.setSize(400, 450);
        cardLayout.show(mainPanel, CARD_SERVER_INPUT);
        setTitle("서버 접속");
    }

    // 마이페이지로 전환
    public void switchToMyPage() {
        myPagePanel.refreshData();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_MYPAGE);
        setTitle("마이페이지");
    }
    
    // 1인 플레이 게임 시작
    public void startSinglePlayerGame() {
        isSinglePlayer = true;
        UserData userData = UserData.getInstance();
        this.playerName = userData.getNickname();
        
        // 난이도 선택 다이얼로그
        String[] options = {"쉬움", "보통", "어려움"};
        String difficulty = (String) JOptionPane.showInputDialog(
            this,
            "난이도를 선택하세요:",
            "1인 플레이",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        
        if (difficulty != null) {
            this.selectedDifficulty = difficulty;
            
            // 서버 연결
            String host = "127.0.0.1";
            int port = 9999;
            
            SwingWorker<Socket, Void> worker = new SwingWorker<Socket, Void>() {
                @Override
                protected Socket doInBackground() throws Exception {
                    return new Socket(host, port);
                }
                
                @Override
                protected void done() {
                    try {
                        Socket socket = get();
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        
                        // JOIN 패킷 전송 (1인 플레이 표시)
                        GamePacket joinPacket = new GamePacket(
                            GamePacket.Type.JOIN,
                            playerName,
                            "SINGLE_" + difficulty,
                            true
                        );
                        out.writeObject(joinPacket);
                        out.flush();
                        
                        // 서버로부터 ROUND_START 수신 대기
                        GamePacket roundStartPacket = (GamePacket) in.readObject();
                        
                        if (roundStartPacket.getType() == GamePacket.Type.ROUND_START) {
                            GameLauncher.this.setVisible(false);
                            new SinglePlayerGUI(socket, in, out, playerName, difficulty, roundStartPacket, GameLauncher.this);
                        } else {
                            throw new Exception("서버로부터 올바른 응답을 받지 못했습니다.");
                        }
                        
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(GameLauncher.this,
                            "서버 연결 실패: " + ex.getMessage() + "\n서버가 실행 중인지 확인하세요.",
                            "오류",
                            JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                }
            };
            worker.execute();
        }
    }

    // 대기방(Lobby) 카드로 전환
    public void switchToLobby(Socket socket, ObjectOutputStream out, ObjectInputStream in, String playerName, String roomNumber) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.playerName = playerName;
        this.roomNumber = roomNumber;
        
        waitingRoom.setConnection(out, playerName, roomNumber);
        
        // 서버 리스너 스레드 시작
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();

        this.setSize(600, 500);
        cardLayout.show(mainPanel, CARD_LOBBY);
        setTitle("대기방 (유저: " + playerName + ")");
    }
    
    // 서버로부터 패킷 수신 (대기방 전용)
    private void listenFromServer() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;
                
                // 패킷을 처리하도록 EDT에 넘김
                SwingUtilities.invokeLater(() -> handlePacket(p));
                
                if (p.getType() == GamePacket.Type.ROUND_START) {
                    System.out.println("[GameLauncher] ROUND_START 감지. 대기방 리스너를 종료합니다.");
                    break;
                }
            }
        } catch (Exception e) {
            if (this.isVisible()) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다: " + e.getMessage());
                    switchToMainMenu();
                    homePanel.resetUI();
                });
            } else {
                System.out.println("[GameLauncher] 리스너가 게임 시작으로 인해 정상 종료되었습니다.");
            }
        }
    }
    
    // 대기방 상태에서의 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {
            case MESSAGE:
                waitingRoom.appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                break;
            case LOBBY_UPDATE:
                waitingRoom.updateLobbyInfo(
                    p.getHostName(), 
                    p.getPlayerReadyStatus(),
                    p.getDifficulty(),
                    p.getGameMode()
                );
                break;
            case ROUND_START:
                System.out.println("GameLauncher: ROUND_START 수신! 게임 창을 엽니다.");
                
                this.setVisible(false);
                
                new HiddenObjectClientGUI(
                    socket, in, out, 
                    playerName, 
                    selectedDifficulty,
                    p,
                    this
                );
                break;
            default:
                break;
        }
    }

    // 게임 시작 요청
    public void requestStartGame(String difficulty, String gameMode) {
        this.selectedDifficulty = difficulty;
        
        GamePacket requestPacket = new GamePacket(
            GamePacket.Type.START_GAME_REQUEST,
            playerName,
            difficulty,
            gameMode
        );
        sendPacket(requestPacket);
    }
    
    // 채팅/메시지 전송
    public void sendPacket(GamePacket packet) {
        if (out != null) {
            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                waitingRoom.appendChat("[오류] 메시지 전송 실패: " + e.getMessage() + "\n");
            }
        }
    }
    
    // 현재 플레이어 이름 반환
    public String getPlayerName() {
        return playerName;
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameLauncher());
    }
}
    