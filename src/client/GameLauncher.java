package client;

import model.GamePacket;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameLauncher extends JFrame {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerName;
    private String selectedDifficulty;
    private String roomNumber;

    private boolean isDisconnect = false;

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private NicknameSetupPanel nicknameSetupPanel;
    private MainMenuPanel mainMenuPanel;
    private HomePanel homePanel;
    private WaitingRoom waitingRoomNormal;
    private WaitingRoom waitingRoomFlash;
    private MyPagePanel myPagePanel;

    private static final String CARD_NICKNAME_SETUP = "NICKNAME_SETUP";
    private static final String CARD_MAIN_MENU = "MAIN_MENU";
    private static final String CARD_SERVER_INPUT = "SERVER_INPUT";
    private static final String CARD_LOBBY_NORMAL = "LOBBY_NORMAL";
    private static final String CARD_LOBBY_FLASH = "LOBBY_FLASH";
    private static final String CARD_MYPAGE = "MYPAGE";

    private String gameModeType = "NORMAL";
    private boolean isSinglePlayer = false;

    public GameLauncher() {

        setTitle("숨은 그림 찾기");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if ((waitingRoomNormal != null && waitingRoomNormal.isVisible()) ||
                    (waitingRoomFlash != null && waitingRoomFlash.isVisible())) {
                    if (socket != null && !socket.isClosed()) {
                        int choice = JOptionPane.showConfirmDialog(GameLauncher.this,
                                "대기방을 나가고 메인 메뉴로 돌아가시겠습니까?", "나가기",
                                JOptionPane.YES_NO_OPTION);

                        if (choice == JOptionPane.YES_OPTION) {
                            disconnectAndReturnToMenu();
                        }
                    }
                } else {
                    System.exit(0);
                }
            }
        });

        setSize(500, 600);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        nicknameSetupPanel = new NicknameSetupPanel(this);
        mainPanel.add(nicknameSetupPanel, CARD_NICKNAME_SETUP);

        mainMenuPanel = new MainMenuPanel(this);
        mainPanel.add(mainMenuPanel, CARD_MAIN_MENU);

        homePanel = new HomePanel(this);
        mainPanel.add(homePanel, CARD_SERVER_INPUT);

        waitingRoomNormal = new WaitingRoom(this, "NORMAL");
        mainPanel.add(waitingRoomNormal, CARD_LOBBY_NORMAL);

        waitingRoomFlash = new WaitingRoom(this, "FLASH");
        mainPanel.add(waitingRoomFlash, CARD_LOBBY_FLASH);

        myPagePanel = new MyPagePanel(this);
        mainPanel.add(myPagePanel, CARD_MYPAGE);

        add(mainPanel);

        UserData userData = UserData.getInstance();
        if (userData != null && userData.getNickname() != null) {
            cardLayout.show(mainPanel, CARD_MAIN_MENU);
        } else {
            cardLayout.show(mainPanel, CARD_NICKNAME_SETUP);
        }

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // 기본 설정 & 화면 전환

    public void setGameModeType(String type) { this.gameModeType = type; }
    public String getGameModeType() { return gameModeType; }

    public void switchToMainMenu() {
        mainMenuPanel.refreshUserInfo();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_MAIN_MENU);
        setTitle("숨은 그림 찾기");
    }

    public void switchToNicknameSetup() {
        nicknameSetupPanel.resetUI();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_NICKNAME_SETUP);
        setTitle("숨은 그림 찾기");
    }

    public void switchToServerInput() {
        isSinglePlayer = false;
        homePanel.updateUserInfo();
        homePanel.resetUI();
        this.gameModeType = "NORMAL";
        this.setSize(400, 450);
        cardLayout.show(mainPanel, CARD_SERVER_INPUT);
        setTitle("서버 접속");
    }

    public void switchToServerInputForFlashlight() {
        isSinglePlayer = false;
        homePanel.updateUserInfo();
        homePanel.resetUI();
        this.gameModeType = "FLASH";
        this.setSize(400, 450);
        cardLayout.show(mainPanel, CARD_SERVER_INPUT);
        setTitle("서버 접속 (그림자 모드)");
    }

    public void switchToMyPage() {
        myPagePanel.refreshData();
        this.setSize(500, 600);
        cardLayout.show(mainPanel, CARD_MYPAGE);
        setTitle("마이페이지");
    }

    // 1인 플레이 시작 (싱글 NORMAL)
    public void startSinglePlayerGame() {
        isSinglePlayer = true;
        UserData userData = UserData.getInstance();
        this.playerName = (userData != null) ? userData.getNickname() : "Guest";

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

        if (difficulty == null) return;
        this.selectedDifficulty = difficulty;

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

                    GamePacket joinPacket = new GamePacket(
                            GamePacket.Type.JOIN,
                            playerName,
                            "SINGLE_" + selectedDifficulty,
                            true
                    );
                    out.writeObject(joinPacket);
                    out.flush();

                    Object resp = in.readObject();
                    if (!(resp instanceof GamePacket)) {
                        throw new Exception("서버로부터 올바른 응답을 받지 못했습니다.");
                    }
                    GamePacket roundStartPacket = (GamePacket) resp;

                    if (roundStartPacket.getType() == GamePacket.Type.ROUND_START) {
                        GameLauncher.this.setVisible(false);
                        new SinglePlayerGUI(
                                socket,
                                in,
                                out,
                                playerName,
                                selectedDifficulty,
                                roundStartPacket,
                                GameLauncher.this
                        );
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

   
    // 멀티: 대기방 입장 
    public void switchToLobby(Socket socket,
                              ObjectOutputStream out,
                              ObjectInputStream in,
                              String playerName,
                              String roomNumber,
                              GamePacket firstPacket) {

        this.socket = socket;
        this.out = out;
        this.in = in;
        this.playerName = playerName;
        this.roomNumber = roomNumber;

        WaitingRoom currentRoom;
        String cardName;

        if ("FLASH".equals(gameModeType)) {
            currentRoom = waitingRoomFlash;
            cardName = CARD_LOBBY_FLASH;
        } else {
            currentRoom = waitingRoomNormal;
            cardName = CARD_LOBBY_NORMAL;
        }
        
        currentRoom.resetUI();

        currentRoom.setConnection(out, playerName, roomNumber);

        
        this.setSize(600, 500);
        cardLayout.show(mainPanel, cardName);
        setTitle("대기방 (유저: " + playerName + ")");

        if (firstPacket != null) {
            handlePacket(firstPacket);
        }

        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // 대기방에서 서버 리스너
    private void listenFromServer() {
        isDisconnect = false;

        try {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof GamePacket)) continue;
                GamePacket p = (GamePacket) obj;

                SwingUtilities.invokeLater(() -> handlePacket(p));

                if (p.getType() == GamePacket.Type.ROUND_START) {
                    break;
                }
            }
        } catch (Exception e) {
            if (!isDisconnect) {
                if (this.isVisible()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "서버 연결이 끊어졌습니다: " + e.getMessage());
                        switchToMainMenu();
                        homePanel.resetUI();
                    });
                }
            }
        }
    }

    // 패킷 처리
    private void handlePacket(GamePacket p) {
        switch (p.getType()) {

            case MESSAGE:
                if ("FLASH".equals(gameModeType)) {
                    waitingRoomFlash.appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                } else {
                    waitingRoomNormal.appendChat(p.getSender() + ": " + p.getMessage() + "\n");
                }
                break;

            case LOBBY_UPDATE:
                if ("FLASH".equals(gameModeType)) {
                    waitingRoomFlash.updateLobbyInfo(
                            p.getHostName(),
                            p.getPlayerReadyStatus(),
                            p.getDifficulty(),
                            p.getGameMode()
                    );
                } else {
                    waitingRoomNormal.updateLobbyInfo(
                            p.getHostName(),
                            p.getPlayerReadyStatus(),
                            p.getDifficulty(),
                            p.getGameMode()
                    );
                }
                break;

            case ROUND_START:
                this.setVisible(false);

                String gameType = p.getGameType();

                if ("FLASH".equalsIgnoreCase(gameType)) {
                    new FlashlightGame(
                            socket,
                            in,
                            out,
                            playerName,
                            gameType, p,
                            this
                    );
                } else {
                    new HiddenObjectClientGUI(
                            socket,
                            in,
                            out,
                            playerName,
                            selectedDifficulty,
                            p,
                            this
                    );
                }
                break;

            default:
                break;
        }
    }

   
    // 서버로 시작 요청
    public void requestStartGame(String difficulty, String mode, String gameType) {
        try {
            GamePacket startPacket = new GamePacket(
                    GamePacket.Type.START_GAME_REQUEST,
                    getPlayerName(),
                    difficulty,
                    mode
            );
            startPacket.setGameType(gameType);
            sendPacket(startPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestStartGame(String difficulty, String mode) {
        requestStartGame(difficulty, mode, "NORMAL");
    }

    public void sendPacket(GamePacket packet) {
        if (out != null) {
            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();

                if ("FLASHLIGHT".equals(gameModeType)) {
                    waitingRoomFlash.appendChat("[오류] 메시지 전송 실패: " + e.getMessage() + "\n");
                } else {
                    waitingRoomNormal.appendChat("[오류] 메시지 전송 실패: " + e.getMessage() + "\n");
                }
            }
        }
    }

    public String getPlayerName() {
        return playerName;
    }

    // 연결 해제 & 메인 메뉴 복귀
    private void disconnectAndReturnToMenu() {
        isDisconnect = true;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        socket = null;
        out = null;
        in = null;

        if (waitingRoomNormal != null) {
            waitingRoomNormal.resetUI();
        }
        if (waitingRoomFlash != null) {
            waitingRoomFlash.resetUI();
        }

        switchToMainMenu();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameLauncher::new);
    }
}
