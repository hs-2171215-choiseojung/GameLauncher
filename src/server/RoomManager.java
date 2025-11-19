package server;

import model.GamePacket;
import server.LobbyServer.ClientHandler; 

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    public final String roomName;
    private final GameLogic gameLogic;
    private final LobbyServer lobbyServer;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerReadyStatus = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int currentRound = 0;
    private String hostName = null;
    private String gameState = "LOBBY";
    private String currentDifficulty = "쉬움";
    private String currentGameMode = "협동";

    public RoomManager(String roomName, GameLogic gameLogic, LobbyServer lobbyServer) {
        this.roomName = roomName;
        this.gameLogic = gameLogic;
        this.lobbyServer = lobbyServer;
        System.out.println("[RoomManager " + roomName + "] 생성됨.");
    }

    public synchronized boolean addPlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName(); 

        if (!gameState.equals("LOBBY")) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                                 "오류: 이미 게임이 시작되었습니다."));
            return false;
        }

        if (clients.containsKey(playerName)) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                                 "오류: '" + playerName + "' 닉네임이 이미 사용 중입니다."));
            return false;
        }

        clients.put(playerName, handler);

        if (clients.size() == 1) {
            hostName = playerName;
            System.out.println("[RoomManager " + roomName + "] " + playerName + " 님이 방장이 되었습니다.");
        }

        playerReadyStatus.put(playerName, false);

        handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "[서버 " + roomName + "]",
                             playerName + " 님 환영합니다!"));

        broadcast(new GamePacket(GamePacket.Type.MESSAGE, "[서버 " + roomName + "]",
                   playerName + " 님이 들어왔습니다."));

        broadcastLobbyUpdate();
        return true;
    }

    //  플레이어 퇴장 시 방이 비었는지 boolean 반환
    public synchronized boolean removePlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName();
        if (playerName != null) {
            clients.remove(playerName);
            scores.remove(playerName);
            playerReadyStatus.remove(playerName);
            System.out.println("[RoomManager " + roomName + "] " + playerName + " 님이 퇴장했습니다.");

            if (playerName.equals(hostName) && clients.size() > 0) {
                hostName = clients.keySet().iterator().next();
                System.out.println("[RoomManager " + roomName + "] " + hostName + " 님이 새 방장이 되었습니다.");
            }

            if (clients.isEmpty()) {
                System.out.println("[RoomManager " + roomName + "] 모든 유저 퇴장. 대기방으로 리셋합니다.");
                gameState = "LOBBY";
                currentRound = 0;
                hostName = null;
                return true;  // 방이 비었음을 알림
            }

            if (gameState.equals("LOBBY")) {
                broadcastLobbyUpdate();
            } else {
                broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                          playerName + " 님이 퇴장했습니다."));
                broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
            }
        }
        return false; // 아직 방에 사람이 있음을 알림
    }


    public synchronized void handlePacket(ClientHandler handler, GamePacket packet) throws IOException {
        if (!gameState.equals("IN_GAME")) { // 게임 중이 아닐 때
            switch (packet.getType()) {
                case MESSAGE:
                    System.out.println("[대기방 채팅 " + roomName + "] " + packet.getSender() + ": " + packet.getMessage());
                    broadcast(packet);
                    break;
                case READY_STATUS:
                    playerReadyStatus.put(handler.getPlayerName(), packet.isReady());
                    System.out.println("[RoomManager " + roomName + "] " + handler.getPlayerName() + " 준비 상태: " + packet.isReady());
                    broadcastLobbyUpdate();
                    break;
                case SETTINGS_UPDATE:
                    if (handler.getPlayerName().equals(hostName)) {
                        currentDifficulty = packet.getDifficulty();
                        currentGameMode = packet.getGameMode();
                        System.out.println("[RoomManager " + roomName + "] 방장이 설정을 변경: " + currentDifficulty + "/" + currentGameMode);
                        broadcastLobbyUpdate();
                    }
                    break;
                case START_GAME_REQUEST:
                    if (handler.getPlayerName().equals(hostName)) {
                        System.out.println("[RoomManager " + roomName + "] " + handler.getPlayerName() + " 님이 게임 시작 요청.");

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

                        currentDifficulty = packet.getDifficulty();
                        currentGameMode = packet.getGameMode();
                        currentRound = 1;
                        gameLogic.loadRound(currentDifficulty, currentRound);
                        gameState = "IN_GAME";
                        
                        Map<String, Integer> indexMap = new HashMap<>();
                        for (String pName : clients.keySet()) {
                            indexMap.put(pName, lobbyServer.getJoinOrderIndex(pName));
                        }

                        System.out.println("[RoomManager " + roomName + "] " + currentDifficulty + "/" + currentGameMode + " 모드로 게임을 시작합니다.");

                        broadcast(new GamePacket(GamePacket.Type.ROUND_START,
                            currentRound,
                            gameLogic.getImagePath(currentDifficulty, currentRound),
                            gameLogic.getOriginalAnswers(currentDifficulty, currentRound),
                            gameLogic.getOriginalDimension(currentDifficulty, currentRound),
                            indexMap, 
                            currentGameMode
                        ));

                        scores.clear();
                        for (String playerName : clients.keySet()) {
                            scores.put(playerName, 0);
                        }
                        broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
                    }
                    break;
                default:
                    if(packet.getType() != GamePacket.Type.TIMER_END && packet.getType() != GamePacket.Type.CLICK) {
                       System.out.println("[RoomManager " + roomName + "] 대기방 상태에서 잘못된 패킷 수신: " + packet.getType());
                    }
            }
        }

        else { // 게임 중일 때
             switch (packet.getType()) {
                 case CLICK:
                    String difficulty = currentDifficulty;
                    int answerIndex = packet.getAnswerIndex();
                    boolean isCorrect = gameLogic.checkAnswer(
                                            difficulty,
                                            currentRound,
                                            answerIndex
                                            );
                    String resultMsg;
                    if (isCorrect) {
                        resultMsg = "정답!";
                        scores.put(handler.getPlayerName(), scores.get(handler.getPlayerName()) + 10);
                    } else {
                        resultMsg = "오답 (또는 이미 찾음)!";
                        scores.put(handler.getPlayerName(), scores.get(handler.getPlayerName()) - 5);
                    }
                    broadcast(new GamePacket(GamePacket.Type.RESULT,
                                handler.getPlayerName(), answerIndex, isCorrect, resultMsg));
                    broadcast(new GamePacket(GamePacket.Type.SCORE, getScoreboardString()));
                    if (isCorrect && gameLogic.areAllFound(difficulty, currentRound)) {
                        handleRoundComplete(difficulty);
                    }
                    break;
                case TIMER_END:
                    handleRoundComplete(currentDifficulty);
                    break;
                case MESSAGE:
                    System.out.println("[인게임 채팅 " + roomName + "] " + packet.getSender() + ": " + packet.getMessage());
                    broadcast(packet);
                    break;
                case MOUSE_MOVE:
                    broadcast(packet);
                    break;
                 default:
                    System.out.println("[RoomManager " + roomName + "] 인게임 상태에서 잘못된 패킷 수신: " + packet.getType());
             }
        }
    }

    private void handleRoundComplete(String difficulty) {
        if (gameLogic.hasNextRound(difficulty, currentRound)) {
            broadcast(new GamePacket(GamePacket.Type.MESSAGE, "SERVER",
                      "라운드 " + currentRound + " 완료! 3초 후 다음 라운드 시작..."));
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    currentRound++;
                    gameLogic.loadRound(difficulty, currentRound);
                    
                    Map<String, Integer> indexMap = new HashMap<>();
                    for (String pName : clients.keySet()) {
                        indexMap.put(pName, lobbyServer.getJoinOrderIndex(pName));
                    }
                    
                    broadcast(new GamePacket(GamePacket.Type.ROUND_START,
                        currentRound,
                        gameLogic.getImagePath(difficulty, currentRound),
                        gameLogic.getOriginalAnswers(difficulty, currentRound),
                        gameLogic.getOriginalDimension(difficulty, currentRound),
                        indexMap,
                        currentGameMode
                    ));
                    System.out.println("[RoomManager " + roomName + "] 라운드 " + currentRound + " 시작");
                }
            }, 3000);
        } else {
            broadcast(new GamePacket(GamePacket.Type.GAME_OVER,
                      "모든 라운드 클리어! 게임 종료!"));
            gameState = "LOBBY";
            currentRound = 0;
            broadcastLobbyUpdate();
        }
    }

    private synchronized void broadcastLobbyUpdate() {
        broadcast(new GamePacket(
            GamePacket.Type.LOBBY_UPDATE,
            hostName,
            new ConcurrentHashMap<>(playerReadyStatus),
            currentDifficulty,
            currentGameMode
        ));
    }

    private synchronized void broadcast(GamePacket packet) {
        for (ClientHandler handler : clients.values()) {
            handler.sendPacket(packet);
        }
    }

    private synchronized String getScoreboardString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- 점수판 (" + roomName + "번 방) ---\n");
        if (scores.isEmpty()) {
            sb.append("(게임 시작 대기 중)\n");
        }
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append("점\n");
        }
        return sb.toString();
    }
}