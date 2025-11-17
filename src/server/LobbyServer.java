package server;

import model.GamePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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
    private final Map<String, Integer> singlePlayScores = new ConcurrentHashMap<>();
    
    // 멀티플레이 게임방 관리 (key: roomNumber)
    private final Map<String, RoomManager> activeRooms = new ConcurrentHashMap<>();
    
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
    public class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerName;
        private boolean isSinglePlayer = false;
        
        private RoomManager room = null;  // 멀티 전용
        
        // 1인 전용
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
                    String message = joinPacket.getMessage();  // roomNumber
                    
                    // 1인 플레이 모드 감지
                    isSinglePlayer = joinPacket.getMessage().startsWith("SINGLE_");
                    
                    if (clients.containsKey(this.playerName)) {
                        sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", 
                                     "오류: '" + this.playerName + "' 닉네임이 이미 사용 중입니다."));
                        socket.close();
                        return;
                    }
                    clients.put(this.playerName, this);
                    
                    if (isSinglePlayer) {
                        // 1인 플레이: 즉시 게임 시작
                        String difficulty = joinPacket.getMessage().replace("SINGLE_", "");
                        playerDifficulty = difficulty;
                        playerCurrentRound = 1;
                        System.out.println("[서버] " + this.playerName + " 님이 1인 플레이로 접속 (난이도: " + difficulty + ")");
                        
                        clients.put(this.playerName, this);
                        singlePlayScores.put(this.playerName, 0);
                        
                        // 1라운드 시작
                        startRoundForPlayer(1);
                        
                    } else {
                        // 멀티 플레이: 기존 로직
                        String roomNumber = message;
                        System.out.println("[서버] " + this.playerName + " 님이 " + roomNumber + " 방에 접속했습니다.");
                        
                        RoomManager targetRoom;
                        synchronized(activeRooms){
                        	targetRoom = activeRooms.get(roomNumber);
                        	if(targetRoom == null) {
                        		targetRoom = new RoomManager(roomNumber, gameLogic);
                        		activeRooms.put(roomNumber, targetRoom);
                        	}
                        }
                        
                        if (clients.size() == 1) {
                            hostName = this.playerName;
                            System.out.println("[서버] " + this.playerName + " 님이 방장이 되었습니다.");
                        }
                        
                        boolean success = targetRoom.addPlayer(this); // 입장 시도
                        
                        if(success) {
                        	this.room = targetRoom;  // 속한 방 저장
                        }
                        else { // 게임 중, 닉네임 중복 시
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
        
        // 해당 플레이어에게 라운드 시작(1인)
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
        
        public String getPlayerName() { return playerName; }
        public boolean isSinglePlayer() { return isSinglePlayer; }
        public RoomManager getRoom() { return room; }
        
        private void handleDisconnect() {
            if (playerName != null) {
                clients.remove(playerName);
                
                // 1인 플레이어가 나간 경우
                if (isSinglePlayer) {
                	singlePlayScores.remove(playerName);
                    playerReadyStatus.remove(playerName);
                    System.out.println("[서버] 1인 플레이 세션 종료.");
                } else {
                	if(room != null) {
                		boolean isRoomEmpty = room.removePlayer(this);
                		
                		if(isRoomEmpty) {
                			System.out.println("[서버] [" + room.roomName + "] 번 방이 비어 닫습니다.");
                			activeRooms.remove(room.roomName);
                		}
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
        RoomManager targetRoom = handler.getRoom();
        if (targetRoom != null) {
            targetRoom.handlePacket(handler, packet); // (수정) 내가 속한 방에 패킷 위임
        } else {
            System.out.println("[서버] 오류: " + handler.getPlayerName() + " 님이 속한 방이 없습니다.");
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
            int currentScore = singlePlayScores.getOrDefault(handler.playerName, 0);
            singlePlayScores.put(handler.playerName, currentScore + 10);
        } else {
            resultMsg = "오답 (또는 이미 찾음)!";
            int currentScore = singlePlayScores.getOrDefault(handler.playerName, 0);
            singlePlayScores.put(handler.playerName, currentScore - 5);
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

    // 현재 점수판 텍스트 생성
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