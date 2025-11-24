package server;

import model.GamePacket;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyServer {

    private static final int PORT = 9999;
    private ServerSocket listener = null;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    
    private final Map<String, Boolean> playerReadyStatus = new ConcurrentHashMap<>();
    
    private final Map<String, Integer> singlePlayScores = new ConcurrentHashMap<>();
    
    private final Map<String, RoomManager> activeRooms = new ConcurrentHashMap<>();
    
    private GameLogic gameLogic;
    private int currentRound = 0;
    private String hostName = null;

    private String gameState = "LOBBY";
    private String currentDifficulty = "쉬움";
    private String currentGameMode = "협동";
    
    private final List<String> joinList = new ArrayList<>();

    public LobbyServer() {
        System.out.println("[서버] 로비 서버가 시작 준비 중입니다...");
        try {
            this.gameLogic = new GameLogic();
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
    
    public int getJoinOrderIndex(String playerName) {
        synchronized (joinList) {
            return joinList.indexOf(playerName);
        }
    }
    
    public class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName;
        private boolean isSinglePlayer = false;
        
        private RoomManager room = null;
        
        // 1인 전용
        private String playerDifficulty = "쉬움";
        private int playerCurrentRound = 1;
        private int playerHintCount = 3; // ★ 힌트 카운트

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
                    String message = joinPacket.getMessage();
                    
                    isSinglePlayer = joinPacket.getMessage().startsWith("SINGLE_");
                    
                    if (clients.containsKey(this.playerName)) {
                        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                     "오류: '" + this.playerName + "' 닉네임이 이미 사용 중입니다."));
                        socket.close();
                        return;
                    }
                    clients.put(this.playerName, this);
                    
                    synchronized (joinList) {
                        if (!joinList.contains(this.playerName)) {
                        	joinList.add(this.playerName);
                        }
                    }
                    
                    if (isSinglePlayer) {
                        String difficulty = joinPacket.getMessage().replace("SINGLE_", "");
                        playerDifficulty = difficulty;
                        playerCurrentRound = 1;
                        playerHintCount = 3; // ★ 힌트 초기화
                        System.out.println("[서버] " + this.playerName + " 님이 1인 플레이로 접속 (난이도: " + difficulty + ")");
                        
                        clients.put(this.playerName, this);
                        singlePlayScores.put(this.playerName, 0);
                        
                        startRoundForPlayer(1);
                        
                    } else {
                        String roomNumber = message;
                        System.out.println("[서버] " + this.playerName + " 님이 " + roomNumber + " 방에 접속했습니다.");
                        
                        RoomManager targetRoom;
                        synchronized(activeRooms){
                        	targetRoom = activeRooms.get(roomNumber);
                        	if(targetRoom == null) {
                        		targetRoom = new RoomManager(roomNumber, gameLogic, LobbyServer.this);
                        		activeRooms.put(roomNumber, targetRoom);
                        	}
                        }
                        
                        if (clients.size() == 1) {
                            hostName = this.playerName;
                            System.out.println("[서버] " + this.playerName + " 님이 방장이 되었습니다.");
                        }
                        
                        boolean success = targetRoom.addPlayer(this);
                        
                        if(success) {
                        	this.room = targetRoom;
                        }
                        else {
                        	System.out.println("[서버] " + this.playerName + "님 입장 거부");
                        	clients.remove(this.playerName);
                        	socket.close();
                        	return;
                        }
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
        
  
        private void startRoundForPlayer(int round) {
            playerCurrentRound = round;
            playerHintCount = 3; 
            gameLogic.loadRound(playerDifficulty, round);
            
            Map<String, Integer> singleIndexMap = new HashMap<>();
            singleIndexMap.put(playerName, 0);
            
            sendPacket(new GamePacket(GamePacket.Type.ROUND_START, 
                round, 
                gameLogic.getImagePath(playerDifficulty, round),
                gameLogic.getOriginalAnswers(playerDifficulty, round),
                gameLogic.getOriginalDimension(playerDifficulty, round),
                singleIndexMap,
                "협동"
            ));
            System.out.println("[서버] [1인 플레이] " + playerName + " - 라운드 " + round + " 시작 (힌트: 3/3)");
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
        
        public String getPlayerName() { return playerName; }
        public boolean isSinglePlayer() { return isSinglePlayer; }
        public RoomManager getRoom() { return room; }

  
        private void handleDisconnect() {
            if (playerName != null) {
                clients.remove(playerName);
                
                if (isSinglePlayer) {
                    singlePlayScores.remove(playerName);
                    playerReadyStatus.remove(playerName);
                    System.out.println("[서버] [1인 플레이] " + playerName + " 세션 종료.");
                } else {
                    if(room != null) {
                        
                        boolean isRoomEmpty = room.removePlayer(this);
                        
                        if(isRoomEmpty) {
                            System.out.println("[서버] [" + room.roomName + "] 번 방이 비어 닫습니다.");
                            activeRooms.remove(room.roomName);
                        }
                    }
                }
                
                synchronized (joinList) {
                    joinList.remove(playerName);
                }
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // ★ 패킷 처리
    private synchronized void handlePacket(ClientHandler handler, GamePacket packet) throws IOException {
        
        System.out.println("[서버] 패킷 수신: " + packet.getType() + " from " + handler.playerName);
        
        if (handler.isSinglePlayer) {
            switch (packet.getType()) {
                case CLICK:
                    handleClickForSinglePlayer(handler, packet);
                    break;
                case TIMER_END:
                    handleTimerEndForSinglePlayer(handler);
                    break;
                // ★ 힌트 요청
                case HINT_REQUEST:
                    System.out.println("[서버] 힌트 요청 감지: " + handler.playerName);
                    handleHintForSinglePlayer(handler);
                    break;
                default:
                    System.out.println("[서버] 처리되지 않은 패킷: " + packet.getType());
                    break;
            }
            return;
        }
        
        RoomManager targetRoom = handler.getRoom();
        if (targetRoom != null) {
            targetRoom.handlePacket(handler, packet);
        } else {
            System.out.println("[서버] 오류: " + handler.getPlayerName() + " 님이 속한 방이 없습니다.");
        }
    }
    
   
    private synchronized void handleHintForSinglePlayer(ClientHandler handler) {
        if (handler.playerHintCount <= 0) {
            handler.sendPacket(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[힌트] 더 이상 힌트를 사용할 수 없습니다. (0/3)"
            ));
            return;
        }
        
        String difficulty = handler.playerDifficulty;
        int round = handler.playerCurrentRound;
        
        List<Rectangle> answers = gameLogic.getOriginalAnswers(difficulty, round);
        if (answers == null || answers.isEmpty()) {
            handler.sendPacket(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[힌트] 정답 정보를 찾을 수 없습니다."
            ));
            return;
        }
        
        List<Integer> unfoundIndices = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            if (!gameLogic.isAnswerFound(difficulty, round, i)) {
                unfoundIndices.add(i);
            }
        }

        if (unfoundIndices.isEmpty()) {
            handler.sendPacket(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[힌트] 모든 정답을 이미 찾았습니다!"
            ));
            return;
        }

        Random random = new Random();
        int randomIdx = unfoundIndices.get(random.nextInt(unfoundIndices.size()));
        Point hintPos = gameLogic.getAnswerCenter(difficulty, round, randomIdx);

        handler.playerHintCount--;
        int currentScore = singlePlayScores.getOrDefault(handler.playerName, 0);
        singlePlayScores.put(handler.playerName, currentScore - 5);

        handler.sendPacket(new GamePacket(
            GamePacket.Type.HINT_RESPONSE,
            hintPos,
            handler.playerHintCount,
            "[힌트] 정답 위치를 표시했습니다! (남은 힌트: " + handler.playerHintCount + "/3, -5점)"
        ));
        
        handler.sendPacket(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));

        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 힌트 사용: " + hintPos + 
                           " (남은 횟수: " + handler.playerHintCount + ")");
    }
    
    private synchronized void handleClickForSinglePlayer(ClientHandler handler, GamePacket packet) {
        String difficulty = handler.playerDifficulty;
        int round = handler.playerCurrentRound;
        int answerIndex = packet.getAnswerIndex();
        
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 라운드 " + round + " 클릭: " + answerIndex + "번");
        
        boolean isCorrect = gameLogic.checkAnswer(difficulty, round, answerIndex);
        
        String resultMsg;
        
        if (isCorrect) {
            resultMsg = "정답!";
            int currentScore = singlePlayScores.getOrDefault(handler.playerName, 0);
            singlePlayScores.put(handler.playerName, currentScore + 10);
        } else {
            resultMsg = "오답 (또는 이미 찾음)!";
            int currentScore = singlePlayScores.getOrDefault(handler.playerName, 0);
            singlePlayScores.put(handler.playerName, currentScore - 5);
        }
        
        handler.sendPacket(new GamePacket(GamePacket.Type.RESULT, 
                    handler.playerName, answerIndex, isCorrect, resultMsg));
        
        handler.sendPacket(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
        
        if (isCorrect && gameLogic.areAllFound(difficulty, round)) {
            handleRoundCompleteForSinglePlayer(handler);
        }
    }
    
    private synchronized void handleTimerEndForSinglePlayer(ClientHandler handler) {
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 타이머 종료");
        handleRoundCompleteForSinglePlayer(handler);
    }
    
    private synchronized void handleRoundCompleteForSinglePlayer(ClientHandler handler) {
        String difficulty = handler.playerDifficulty;
        int completedRound = handler.playerCurrentRound;
        
        System.out.println("[서버] [1인 플레이] " + handler.playerName + " 라운드 " + completedRound + " 완료 체크");
        
        if (gameLogic.hasNextRound(difficulty, completedRound)) {
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
            System.out.println("[서버] [1인 플레이] " + handler.playerName + " 모든 라운드 완료!");
            handler.sendPacket(new GamePacket(GamePacket.Type.GAME_OVER, 
                      "모든 라운드 클리어! 게임 종료!"));
        }
    }

    private synchronized String getScoreboardString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- 점수판 ---\n");
        if (singlePlayScores.isEmpty()) {
            sb.append("(게임 시작 대기 중)\n");
        }
        for (Map.Entry<String, Integer> entry : singlePlayScores.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append("점\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        new LobbyServer().run();
    }
}