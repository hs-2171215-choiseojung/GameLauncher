package server;

import model.GamePacket;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

//대기방(Lobby) 기능을 지원하는 게임 서버 
public class LobbyServer {

    private static final int PORT = 9999;
    private ServerSocket listener = null;

    // 접속한 모든 클라이언트 (key: playerName)
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    
    // 플레이어 준비 상태 (key: playerName, value: isReady)
    private final Map<String, Boolean> playerReadyStatus = new ConcurrentHashMap<>();
    
    // 점수 관리
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    
    private GameLogic gameLogic;
    private int currentRound = 0;
    private String hostName = null; // 방장 닉네임

    private String gameState = "LOBBY";
    private String currentDifficulty = "쉬움";
    private String currentGameMode = "협동";
    

    public LobbyServer() {
        System.out.println("[서버] 로비 서버가 시작 준비 중입니다...");
        try {
            this.gameLogic = new GameLogic(); // GameLogic 초기화
            System.out.println("[서버] 게임 로직 초기화 완료.");
            
        } catch (IOException e) {
            System.out.println("[서버] 치명적 오류: 게임 정답 이미지 로드 실패! 'images' 폴더를 확인하세요.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() {
        try {
            listener = new ServerSocket(PORT);
            System.out.println("[서버] 대기방 서버가 " + PORT + " 포트에서 대기 중입니다...");

            while (true) {
                Socket socket = listener.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandler.start();
            }

        } catch (IOException e) {
            System.out.println("[서버] 오류: " + e.getMessage());
        } finally {
            closeServer();
        }
    }
    
    private void closeServer() {
        try {
            if (listener != null && !listener.isClosed()) listener.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // --- 클라이언트 핸들러 (내부 클래스) ---
    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName;
        private boolean isSinglePlayer = false;
        private String playerDifficulty = "쉬움"; // 해당 플레이어의 난이도
        private int playerCurrentRound = 1; // 해당 플레이어의 현재 라운드

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new ObjectInputStream(socket.getInputStream());
                out = new ObjectOutputStream(socket.getOutputStream());

                GamePacket joinPacket = (GamePacket) in.readObject();
                if (joinPacket.getType() == GamePacket.Type.JOIN) {
                    this.playerName = joinPacket.getSender();
                    
                    // 1인 플레이 모드 감지
                    isSinglePlayer = joinPacket.getMessage().startsWith("SINGLE_");
                    
                    if (isSinglePlayer) {
                        // 1인 플레이: 즉시 게임 시작
                        String difficulty = joinPacket.getMessage().replace("SINGLE_", "");
                        playerDifficulty = difficulty;
                        playerCurrentRound = 1;
                        System.out.println("[서버] " + this.playerName + " 님이 1인 플레이로 접속 (난이도: " + difficulty + ")");
                        
                        clients.put(this.playerName, this);
                        scores.put(this.playerName, 0);
                        
                        // 1라운드 시작
                        startRoundForPlayer(1);
                        
                    } else {
                        // 멀티 플레이: 기존 로직
                        if (!gameState.equals("LOBBY")) {
                            sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                         "오류: 이미 게임이 시작되었습니다."));
                            socket.close();
                            return;
                        }
                        
                        if (clients.containsKey(this.playerName)) {
                            sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                         "오류: '" + this.playerName + "' 닉네임이 이미 사용 중입니다."));
                            socket.close();
                            return;
                        }
                        
                        // 접속 성공
                        System.out.println("[서버] " + this.playerName + " 님이 대기방에 접속했습니다.");
                        clients.put(this.playerName, this);
                        
                        if (clients.size() == 1) {
                            hostName = this.playerName;
                            System.out.println("[서버] " + this.playerName + " 님이 방장이 되었습니다.");
                        }
                        
                        playerReadyStatus.put(this.playerName, false);
                        
                        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "[서버]", 
                                     this.playerName + " 님 환영합니다!"));
                        
                        broadcast(new GamePacket(GamePacket.Type.MESSAGE, "[서버]", 
                                     this.playerName + " 님이 들어왔습니다."));
                        
                        broadcastLobbyUpdate();
                    }

                } else {
                    socket.close();
                    return;
                }

                while (true) {
                    GamePacket packet = (GamePacket) in.readObject();
                    handlePacket(this, packet);
                }

            } catch (Exception e) {
                if (isSinglePlayer) {
                    System.out.println("[서버] [1인 플레이] " + playerName + " 연결 끊김.");
                } else {
                    System.out.println("[서버] " + playerName + " 연결 끊김.");
                }
            } finally {
                handleDisconnect();
            }
        }
        
        // 해당 플레이어에게 라운드 시작
        private void startRoundForPlayer(int round) {
            playerCurrentRound = round;
            gameLogic.loadRound(playerDifficulty, round);
            sendPacket(new GamePacket(GamePacket.Type.ROUND_START, 
                round, 
                gameLogic.getImagePath(playerDifficulty, round),
                gameLogic.getOriginalAnswers(playerDifficulty, round),
                gameLogic.getOriginalDimension(playerDifficulty, round)
            ));
            System.out.println("[서버] [1인 플레이] " + playerName + " - 라운드 " + round + " 시작");
        }
        
        public void sendPacket(GamePacket packet) {
            try {
                if (out != null) {
                    out.writeObject(packet);
                    out.flush();
                }
            } catch (IOException e) {
                System.out.println("[서버] " + playerName + " 패킷 전송 실패: " + e.getMessage());
            }
        }
        
        private void handleDisconnect() {
            if (playerName != null) {
                clients.remove(playerName);
                scores.remove(playerName);
                playerReadyStatus.remove(playerName); 
                System.out.println("[서버] " + playerName + " 님이 퇴장했습니다.");
                
                // 1인 플레이어가 나간 경우
                if (isSinglePlayer) {
                    System.out.println("[서버] 1인 플레이 세션 종료.");
                } else {
                    // 방장이 나갔을 때 가장 먼저 접근 가능한 닉네임을 방장으로 설정
                    if (playerName.equals(hostName) && clients.size() > 0) {
                        hostName = clients.keySet().iterator().next();
                        System.out.println("[서버] " + hostName + " 님이 새 방장이 되었습니다.");
                    }
                    
                    // 모든 클라이언트가 퇴장하면 방장 이름은 null로 설정
                    if (clients.isEmpty()) {
                        System.out.println("[서버] 모든 유저 퇴장. 대기방으로 리셋합니다.");
                        gameState = "LOBBY";
                        currentRound = 0;
                        hostName = null;
                    }
                    
                    if (gameState.equals("LOBBY")) {
                        broadcastLobbyUpdate();
                    } else {
                        broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                  playerName + " 님이 퇴장했습니다."));
                        broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
                    }
                }
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // --- 서버 메인 로직 (synchronized) ---

    // 패킷 처리 로직
    private synchronized void handlePacket(ClientHandler handler, GamePacket packet) throws IOException {
        
        // 1인 플레이 모드 처리
        if (handler.isSinglePlayer) {
            switch (packet.getType()) {
                case CLICK:
                    handleClickForSinglePlayer(handler, packet);
                    break;
                case TIMER_END:
                    // 타이머 종료 시 다음 라운드로
                    handleTimerEndForSinglePlayer(handler);
                    break;
                default:
                    // 다른 패킷은 무시
                    break;
            }
            return;
        }
        
        // 멀티 플레이 모드 처리
        if (!gameState.equals("IN_GAME")) { // 게임 중이 아닐 때
            switch (packet.getType()) {
                case MESSAGE:
                    System.out.println("[대기방 채팅] " + packet.getSender() + ": " + packet.getMessage());
                    broadcast(packet);
                    break;
                case READY_STATUS:
                    playerReadyStatus.put(handler.playerName, packet.isReady());
                    System.out.println("[서버] " + handler.playerName + " 준비 상태: " + packet.isReady());
                    broadcastLobbyUpdate(); 
                    break;
                case SETTINGS_UPDATE:
                    if (handler.playerName.equals(hostName)) {
                        currentDifficulty = packet.getDifficulty();
                        currentGameMode = packet.getGameMode();
                        System.out.println("[서버] 방장이 설정을 변경: " + currentDifficulty + "/" + currentGameMode);
                        broadcastLobbyUpdate(); 
                    }
                    break;
                case START_GAME_REQUEST:
                    if (handler.playerName.equals(hostName)) {
                        System.out.println("[서버] " + handler.playerName + " 님이 게임 시작 요청.");
                        
                        boolean allReady = true;
                        for (Map.Entry<String, Boolean> entry : playerReadyStatus.entrySet()) {
                            if (!entry.getKey().equals(hostName) && !entry.getValue()) {
                                allReady = false; 
                                break;
                            }
                        }

                        if (!allReady) {
                             handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                                "오류: 모든 참여자가 '준비 완료' 상태여야 합니다."));
                             return;
                        }
                        
                        if (clients.size() < 1) {
                             handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                                "오류: 최소 2명 이상이어야 시작할 수 있습니다."));
                             return;
                        }
                        
                        // --- 게임 시작 ---
                        currentDifficulty = packet.getDifficulty();
                        currentGameMode = packet.getGameMode();

                        currentRound = 1;
                        gameLogic.loadRound(currentDifficulty, currentRound); 
                        gameState = "IN_GAME"; 
                        
                        System.out.println("[서버] " + currentDifficulty + "/" + currentGameMode + " 모드로 게임을 시작합니다.");
                        
                        broadcast(new GamePacket(GamePacket.Type.ROUND_START, 
                            currentRound, 
                            gameLogic.getImagePath(currentDifficulty, currentRound),
                            gameLogic.getOriginalAnswers(currentDifficulty, currentRound),
                            gameLogic.getOriginalDimension(currentDifficulty, currentRound)
                        ));
                        
                        scores.clear();
                        for (String playerName : clients.keySet()) {
                            scores.put(playerName, 0);
                        }
                        broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
                    }
                    break;
                default:
                    System.out.println("[서버] 대기방 상태에서 잘못된 패킷 수신: " + packet.getType());
            }
        }
        
        else { // 게임 중일 때
             switch (packet.getType()) {
                 case CLICK:
                    String difficulty = currentDifficulty;
                    
                    int answerIndex = packet.getAnswerIndex();
                    System.out.println("[서버] " + handler.playerName + " 클릭: " + answerIndex + "번");
                    
                    boolean isCorrect = gameLogic.checkAnswer(
                                            difficulty, 
                                            currentRound, 
                                            answerIndex
                                            );
                    
                    String resultMsg;
                    
                    if (isCorrect) {
                        resultMsg = "정답!";
                        scores.put(handler.playerName, scores.get(handler.playerName) + 10);
                    } else {
                        resultMsg = "오답 (또는 이미 찾음)!";
                        scores.put(handler.playerName, scores.get(handler.playerName) - 5);
                    }
                    
                    // 1. 클릭 결과 전송
                    broadcast(new GamePacket(GamePacket.Type.RESULT, 
                                handler.playerName, answerIndex, isCorrect, resultMsg));
                    
                    // 2. 점수판 갱신
                    broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));

                    // 3. 모든 정답 찾았는지 확인
                    if (isCorrect && gameLogic.areAllFound(difficulty, currentRound)) {
                        handleRoundComplete(difficulty);
                    }
                    break;
                    
                case TIMER_END:
                    // 타이머 종료 시 다음 라운드로
                    handleRoundComplete(currentDifficulty);
                    break;
                    
                case MESSAGE:
                    System.out.println("[인게임 채팅] " + packet.getSender() + ": " + packet.getMessage());
                    broadcast(packet);
                    break;
                 default:
                    System.out.println("[서버] 인게임 상태에서 잘못된 패킷 수신: " + packet.getType());
             }
        }
    }
    
    // 멀티플레이 라운드 완료 처리
    private void handleRoundComplete(String difficulty) {
        if (gameLogic.hasNextRound(difficulty, currentRound)) {
            // 다음 라운드로
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                      "라운드 " + currentRound + " 완료! 3초 후 다음 라운드 시작..."));
            
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    currentRound++;
                    gameLogic.loadRound(difficulty, currentRound);
                    broadcast(new GamePacket(GamePacket.Type.ROUND_START, 
                        currentRound, 
                        gameLogic.getImagePath(difficulty, currentRound),
                        gameLogic.getOriginalAnswers(difficulty, currentRound),
                        gameLogic.getOriginalDimension(difficulty, currentRound)
                    ));
                    System.out.println("[서버] 라운드 " + currentRound + " 시작");
                }
            }, 3000);
        } else {
            // 모든 라운드 완료
            broadcast(new GamePacket(GamePacket.Type.GAME_OVER, 
                      "모든 라운드 클리어! 게임 종료!"));
            gameState = "LOBBY"; 
            currentRound = 0;
            broadcastLobbyUpdate(); 
        }
    }
    
    // 1인 플레이 클릭 처리
    private synchronized void handleClickForSinglePlayer(ClientHandler handler, GamePacket packet) {
        String difficulty = handler.playerDifficulty;
        int round = handler.playerCurrentRound;
        
        int answerIndex = packet.getAnswerIndex();
        
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 라운드 " + round + " 클릭: " + answerIndex + "번");
        
        boolean isCorrect = gameLogic.checkAnswer(difficulty, round, answerIndex);
        
        String resultMsg;
        
        if (isCorrect) {
            resultMsg = "정답!";
            int currentScore = scores.getOrDefault(handler.playerName, 0);
            scores.put(handler.playerName, currentScore + 10);
        } else {
            resultMsg = "오답 (또는 이미 찾음)!";
            int currentScore = scores.getOrDefault(handler.playerName, 0);
            scores.put(handler.playerName, currentScore - 5);
        }
        
        // 1. 클릭 결과 전송
        handler.sendPacket(new GamePacket(GamePacket.Type.RESULT, 
                    handler.playerName, answerIndex, isCorrect, resultMsg));
        
        // 2. 점수판 갱신
        handler.sendPacket(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
        
        // 3. 모든 정답 찾았는지 확인
        if (isCorrect && gameLogic.areAllFound(difficulty, round)) {
            handleRoundCompleteForSinglePlayer(handler);
        }
    }
    
    // 1인 플레이 타이머 종료 처리
    private synchronized void handleTimerEndForSinglePlayer(ClientHandler handler) {
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 타이머 종료");
        handleRoundCompleteForSinglePlayer(handler);
    }
    
    // 1인 플레이 라운드 완료 처리
    private synchronized void handleRoundCompleteForSinglePlayer(ClientHandler handler) {
        String difficulty = handler.playerDifficulty;
        int completedRound = handler.playerCurrentRound;
        
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 라운드 " + completedRound + " 완료 체크");
        
        if (gameLogic.hasNextRound(difficulty, completedRound)) {
            // 다음 라운드로
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                      "라운드 " + completedRound + " 완료! 3초 후 다음 라운드 시작..."));
            
            System.out.println("[서버] [1인 플레이] " + handler.playerName + " 다음 라운드 준비 중...");
            
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    int nextRound = completedRound + 1;
                    System.out.println("[서버] [1인 플레이] " + handler.playerName + " 라운드 " + nextRound + " 시작 시도");
                    try {
                        handler.startRoundForPlayer(nextRound);
                    } catch (Exception e) {
                        System.out.println("[서버] [1인 플레이] 라운드 시작 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, 3000);
        } else {
            // 모든 라운드 완료
            System.out.println("[서버] [1인 플레이] " + handler.playerName + " 모든 라운드 완료!");
            handler.sendPacket(new GamePacket(GamePacket.Type.GAME_OVER, 
                      "모든 라운드 클리어! 게임 종료!"));
        }
    }
    
    // 모든 대기방 클라이언트에게 현재 유저 목록/설정 전송
    private synchronized void broadcastLobbyUpdate() {
        broadcast(new GamePacket(
            GamePacket.Type.LOBBY_UPDATE,
            hostName,
            new ConcurrentHashMap<>(playerReadyStatus),
            currentDifficulty,
            currentGameMode
        ));
    }

    // 접속한 '모든' 클라이언트에게 패킷 전송
    private synchronized void broadcast(GamePacket packet) {
        for (ClientHandler handler : clients.values()) {
            if (!handler.isSinglePlayer) {
                handler.sendPacket(packet);
            }
        }
    }

    // 현재 점수판 텍스트 생성
    private synchronized String getScoreboardString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- 점수판 ---\n");
        if (scores.isEmpty()) {
            sb.append("(게임 시작 대기 중)\n");
        }
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append("점\n");
        }
        return sb.toString();
    }


    public static void main(String[] args) {
        new LobbyServer().run();
    }
}