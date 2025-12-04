package server;

import model.GamePacket;
import server.LobbyServer.ClientHandler;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    public final String roomName;
    private final GameLogic gameLogic;
    private final LobbyServer lobbyServer;

    private static final int MAX_PLAYERS = 5;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerReadyStatus = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalFoundCounts = new ConcurrentHashMap<>();

    private int currentRound = 0;
    private String hostName = null;
    private String gameState = "LOBBY";
    private String currentDifficulty = "쉬움";
    private String currentGameMode = "협동";
    private String gameType = "NORMAL";
    private String fixedGameType = "NORMAL";

    private final Map<String, Integer> cursorIndexMap = new ConcurrentHashMap<>();

    private boolean[] foundStatus;
    
    private int roundHintCount = 3;
    
    private boolean isRoundChanging = false;

    // 아이템 관련
    private Timer itemSpawnTimer;
    private int nextItemId = 0;
    private final Map<Integer, Point> activeItems = new ConcurrentHashMap<>();
    private final Map<Integer, String> itemTypes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public RoomManager(String roomName, GameLogic gameLogic, LobbyServer lobbyServer) {
        this.roomName = roomName;
        this.gameLogic = gameLogic;
        this.lobbyServer = lobbyServer;
        System.out.println("[RoomManager] [" + roomName + "] 생성됨.");
    }

    // 플레이어 추가
    public synchronized boolean addPlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName();

        if (!gameState.equals("LOBBY")) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "오류: 이미 게임 시작됨"));
            return false;
        }

        if (clients.containsKey(playerName)) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "오류: 닉네임 중복"));
            return false;
        }

        if (clients.size() >= MAX_PLAYERS) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "오류: 방 인원 초과"));
            return false;
        }

        // 비사용 커서 인덱스 배정
        boolean[] used = new boolean[5];
        for (int idx : cursorIndexMap.values()) {
            if (idx >= 0 && idx < 5) used[idx] = true;
        }

        int assignedCursorIndex = 0;
        for (int i = 0; i < 5; i++) {
            if (!used[i]) {
                assignedCursorIndex = i;
                break;
            }
        }

        cursorIndexMap.put(playerName, assignedCursorIndex);

        clients.put(playerName, handler);
        playerReadyStatus.put(playerName, false);

        if (clients.size() == 1) hostName = playerName;

        broadcastLobbyUpdate();
        
        broadcast(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER", 
                playerName + "님이 입장하셨습니다."
            ));
        
        return true;
    }

    // 플레이어 제거
    public synchronized boolean removePlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName();

        if (!clients.containsKey(playerName)) return false;

        clients.remove(playerName);
        playerReadyStatus.remove(playerName);
        cursorIndexMap.remove(playerName);

        if (!clients.isEmpty()) {
            broadcast(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[알림] " + playerName + "님이 방을 나갔습니다."
            ));
        }

        if (clients.isEmpty()) {
            stopItemSpawner();
            return true;
        }
        
        if (gameState.equals("IN_GAME") && clients.size() == 1) {
            String survivorName = clients.keySet().iterator().next();
            ClientHandler survivor = clients.get(survivorName);
            
            String rankingMsg = getRankingString();

            survivor.sendPacket(new GamePacket(
                GamePacket.Type.GAME_OVER,
                rankingMsg + "\n\n(다른 플레이어가 모두 나가 게임이 종료되었습니다.)"
            ));
            
            stopItemSpawner();
            gameState = "LOBBY";
            currentRound = 0;
            isRoundChanging = false; 
            
            playerReadyStatus.put(survivorName, false);
            broadcastLobbyUpdate();
            
            return false; 
        }

        if (playerName.equals(hostName)) {
            hostName = clients.keySet().iterator().next();
            playerReadyStatus.put(hostName, false);

            broadcast(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[알림] " + hostName + "님이 새로운 방장이 되었습니다."
            ));
        }

        broadcastLobbyUpdate();
        return false;
    }

    // 패킷 처리
    public synchronized void handlePacket(ClientHandler handler, GamePacket packet) throws IOException {

        if (packet.getType() == GamePacket.Type.START_GAME_REQUEST) {

            if (!handler.getPlayerName().equals(hostName)) return;
            
            for (ClientHandler h : clients.values()) {
                h.resetHintCount(); 
            }

            boolean allReady = true;
            for (String name : playerReadyStatus.keySet()) {
                if (!playerReadyStatus.get(name) && !name.equals(hostName)) {
                    allReady = false;
                    break;
                }
            }
            if (!allReady) return;
            
            this.gameType = this.fixedGameType;
            this.currentDifficulty = packet.getDifficulty();
            this.currentGameMode = packet.getGameMode();

            currentRound = 1;
            gameLogic.loadRound(currentDifficulty, currentRound);
            
            int total = gameLogic.getOriginalAnswers(currentDifficulty, currentRound).size();
            foundStatus = new boolean[total];

            gameState = "IN_GAME";
            isRoundChanging = false; 

            scores.clear();
            totalFoundCounts.clear();
            clients.keySet().forEach(p -> {
                scores.put(p, 0);
                totalFoundCounts.put(p, 0);
            });

            roundHintCount = 3;

            proceedToNextRoundDataSend(); 
            
            if ("FLASH".equalsIgnoreCase(gameType)) startItemSpawner();

            return;
        }

        // IN GAME
        if (gameState.equals("IN_GAME")) {

            switch (packet.getType()) {

                case MOUSE_MOVE:
                    String sender = handler.getPlayerName();
                    int cursorIdx = cursorIndexMap.getOrDefault(sender, 1);

                    broadcast(new GamePacket(
                        GamePacket.Type.MOUSE_MOVE,
                        sender,
                        0,
                        packet.getX(),
                        packet.getY(),
                        cursorIdx
                    ));
                    break;

                case CLICK:

                	if (isRoundChanging) return;
                    processClick(handler, packet);
                    break;

                case ITEM_PICKUP:
                    if (isRoundChanging) return;
                    handleItemPickup(handler, packet.getItemId());
                    break;

                case TIMER_END:
                    handleRoundComplete();
                    break;

                case MESSAGE:
                    if ("/Q".equalsIgnoreCase(packet.getMessage().trim())) {
                        handleHintRequest(handler);
                    } else {
                        broadcast(packet);
                    }
                    break;

                case HINT_REQUEST:
                    if (isRoundChanging) return;
                    handleHintRequest(handler);
                    break;
            }

            return;
        }

        // LOBBY
        if (gameState.equals("LOBBY")) {
            switch (packet.getType()) {

                case MESSAGE:
                    broadcast(packet);
                    break;

                case READY_STATUS:
                	boolean isReady = packet.isReady();
                    playerReadyStatus.put(handler.getPlayerName(), isReady);
                    
                    String statusMsg = isReady ? "준비 완료" : "준비 취소";
                    broadcast(new GamePacket(
                        GamePacket.Type.MESSAGE, 
                        "SERVER", 
                        handler.getPlayerName() + "님 " + statusMsg
                    ));
                    
                    broadcastLobbyUpdate();
                    break;

                case SETTINGS_UPDATE:
                    if (handler.getPlayerName().equals(hostName)) {
                        currentDifficulty = packet.getDifficulty();
                        currentGameMode = packet.getGameMode();
                        broadcastLobbyUpdate();
                    }
                    break;
            }
        }
    }
    
    public void setFixedGameType(String type) {
        this.fixedGameType = type;
        this.gameType = type;
    }
    
    public String getFixedGameType() {
        return fixedGameType;
    }

    private void handleHintRequest(ClientHandler handler) {
        String playerName = handler.getPlayerName();
        boolean isCompetitive = "경쟁".equals(currentGameMode);

        int currentHints = isCompetitive ? handler.getPlayerHintCount() : roundHintCount;

        if (currentHints <= 0) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[힌트] 힌트를 모두 사용했습니다! (0/3)"));
            return;
        }

        List<Rectangle> answers = gameLogic.getOriginalAnswers(currentDifficulty, currentRound);
        List<Integer> unfound = new ArrayList<>();
        if (foundStatus != null) {
            for (int i = 0; i < answers.size(); i++) {
                if (!foundStatus[i]) unfound.add(i);
            }
        }

        if (unfound.isEmpty()) {
            handler.sendPacket(new GamePacket(GamePacket.Type.MESSAGE, "SERVER", "[힌트] 이미 모든 정답을 찾았습니다!"));
            return;
        }

        int idx = unfound.get(random.nextInt(unfound.size()));
        Point hintPos = gameLogic.getAnswerCenter(currentDifficulty, currentRound, idx);

        if (isCompetitive) {
            handler.decrementHintCount(); 
            currentHints = handler.getPlayerHintCount();
            int currentScore = scores.getOrDefault(playerName, 0);
            scores.put(playerName, Math.max(0, currentScore - 5));
        } else {
            roundHintCount--;
            currentHints = roundHintCount;
        }
       
        GamePacket hintPacket = new GamePacket(
            GamePacket.Type.HINT_RESPONSE,
            hintPos,
            currentHints,
            "[힌트] 정답 위치를 표시했습니다! (남은 힌트: " + currentHints + "/3)"
        );

        if (isCompetitive) {
            handler.sendPacket(hintPacket); 
        } else {
            broadcast(hintPacket); 
        }
    }

    private void processClick(ClientHandler handler, GamePacket packet) {
        String name = handler.getPlayerName();
        int index = packet.getAnswerIndex();
        
        boolean correct = false;
        boolean alreadyFound = false; 

        if (index >= 0 && gameLogic.isValidIndex(currentDifficulty, currentRound, index)) {
            if (foundStatus != null) {
                if (!foundStatus[index]) {
                    foundStatus[index] = true;
                    correct = true;
                    System.out.println("[RoomManager] " + roomName + ": " + index + "번 정답 찾음!");
                } else {
                    alreadyFound = true;
                }
            }
        }

        if (correct) {
            if ("협동".equals(currentGameMode)) {
                for (String p : scores.keySet()) {
                    scores.put(p, scores.get(p) + 10);
                }
            } else {
                scores.put(name, scores.get(name) + 10);
            }
            totalFoundCounts.put(name, totalFoundCounts.getOrDefault(name, 0) + 1);
            
        } else if (!alreadyFound) {
        	if ("협동".equals(currentGameMode)) {
                for (String p : scores.keySet()) {
                	int current = scores.getOrDefault(p, 0);
                	scores.put(p, Math.max(0, current - 5));
                }
            } else {
            	int current = scores.getOrDefault(name, 0);
            	scores.put(name, Math.max(0, current - 5));
            }
        }

        StringBuilder sb = new StringBuilder();
        if ("협동".equals(currentGameMode)) {
            int teamScore = scores.isEmpty() ? 0 : scores.values().iterator().next();
            sb.append("SCORE_COOP:").append(teamScore);
        } else {
            sb.append("[점수]\n");
            for (String pName : scores.keySet()) {
                sb.append(pName).append(" : ").append(scores.get(pName)).append("점\n");
            }
        }
        broadcast(new GamePacket(GamePacket.Type.SCORE, "SERVER", sb.toString()));

        String msg = null;
        if (correct) {
            msg = "[정답] " + name + "님이 숨은 그림을 찾았습니다!";
        } else if (alreadyFound) {
            msg = "[알림] 이미 찾은 그림입니다.";
        }
      
        GamePacket resultPacket = new GamePacket(
            GamePacket.Type.RESULT,
            name,
            index,
            correct,
            msg
        );

        resultPacket.setX(packet.getX());
        resultPacket.setY(packet.getY());

        broadcast(resultPacket);

        if (correct && areAllFound()) {
            handleRoundComplete();
        }
    }
    
    private boolean areAllFound() {
        if (foundStatus == null) return false;
        for (boolean f : foundStatus) {
            if (!f) return false;
        }
        return true;
    }

    private void handleRoundComplete() {
    	
        if (isRoundChanging) return;

        if (gameLogic.hasNextRound(currentDifficulty, currentRound)) {
        	
            isRoundChanging = true;
            broadcast(new GamePacket(
                GamePacket.Type.MESSAGE, 
                "SERVER", 
                "[서버] 라운드 " + currentRound + " 완료! 3초 후 다음 라운드 시작..."
            ));
            
            System.out.println("[RoomManager] 라운드 " + currentRound + " 종료. 3초 대기 시작.");

            Timer nextRoundTimer = new Timer();
            nextRoundTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    proceedToNextRound();
                }
            }, 3000); 

        } else {
            String rankingMsg = getRankingString();
            broadcast(new GamePacket(GamePacket.Type.GAME_OVER, rankingMsg));
            
            currentRound = 0;
            gameState = "LOBBY";
            roundHintCount = 3;
            isRoundChanging = false;

            stopItemSpawner();
            
            for (String pName : playerReadyStatus.keySet()) {
                playerReadyStatus.put(pName, false);
            }
            broadcastLobbyUpdate();
        }
    }

    private synchronized void proceedToNextRound() {
        currentRound++;
        System.out.println("[RoomManager] 라운드 " + currentRound + " 로드 및 전송.");
        
        gameLogic.loadRound(currentDifficulty, currentRound);
        
        int totalAnswers = gameLogic.getOriginalAnswers(currentDifficulty, currentRound).size();
        foundStatus = new boolean[totalAnswers];
        
        roundHintCount = 3;
        
        for (ClientHandler h : clients.values()) {
            h.resetHintCount();
        }

        proceedToNextRoundDataSend();
        
        if ("FLASH".equalsIgnoreCase(gameType)) {
             stopItemSpawner();
             startItemSpawner();
        }
        
        isRoundChanging = false;
    }
    
    // 실제 클라이언트에 패킷을 보내는 부분
    private void proceedToNextRoundDataSend() {
        for (String p : clients.keySet()) {
            GamePacket next = new GamePacket(
                GamePacket.Type.ROUND_START,
                currentRound,
                gameLogic.getImagePath(currentDifficulty, currentRound),
                gameLogic.getOriginalAnswers(currentDifficulty, currentRound),
                gameLogic.getOriginalDimension(currentDifficulty, currentRound),
                new HashMap<>(cursorIndexMap),
                currentGameMode,
                gameType
            );

            next.setCursorIndex(cursorIndexMap.getOrDefault(p, 1));
            next.setRemainingHints(roundHintCount);

            ClientHandler ch = clients.get(p);
            if (ch != null) {
                ch.sendPacket(next);
            }
        }
        
        // 점수판 한번 더 동기화
        StringBuilder sb = new StringBuilder();
        if ("협동".equals(currentGameMode)) {
            int teamScore = scores.isEmpty() ? 0 : scores.values().iterator().next();
            sb.append("SCORE_COOP:").append(teamScore);
        } else {
            sb.append("[점수]\n");
            for (String pName : scores.keySet()) {
                sb.append(pName).append(" : ").append(scores.get(pName)).append("점\n");
            }
        }
        broadcast(new GamePacket(GamePacket.Type.SCORE, "SERVER", sb.toString()));
    }
    
    private String getRankingString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RANKING:");
        
        List<String> players = new ArrayList<>(scores.keySet());
        players.sort((p1, p2) -> {
            int s1 = scores.getOrDefault(p1, 0);
            int s2 = scores.getOrDefault(p2, 0);
            if (s1 != s2) return s2 - s1;
            int c1 = totalFoundCounts.getOrDefault(p1, 0);
            int c2 = totalFoundCounts.getOrDefault(p2, 0);
            return c2 - c1;
        });

        int rank = 1;
        for (String pName : players) {
            int score = scores.getOrDefault(pName, 0);
            int count = totalFoundCounts.getOrDefault(pName, 0);
            
            String displayName = pName;
            if (!clients.containsKey(pName)) {
                displayName = pName;
            }
            
            sb.append(rank++).append(",")
              .append(displayName).append(",")
              .append(count).append(",")
              .append(score).append("\n");
        }
        return sb.toString();
    }

    private void startItemSpawner() {
        if (itemSpawnTimer != null) itemSpawnTimer.cancel();

        itemSpawnTimer = new Timer();
        itemSpawnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                spawnItem();
            }
        }, 8000, 15000);
    }

    private void stopItemSpawner() {
        if (itemSpawnTimer != null) itemSpawnTimer.cancel();
        itemSpawnTimer = null;

        activeItems.clear();
        itemTypes.clear();
    }
    
    private void spawnItem() {
        int id = nextItemId++;

        int x = random.nextInt(800) + 20;
        int y = random.nextInt(1100) + 20;

        Point pos = new Point(x, y);
        activeItems.put(id, pos);

        String type;
        int r = random.nextInt(100);

        if ("협동".equals(currentGameMode)) {
            type = (r < 15) ? "HINT" : "TIME";
        } else {
            type = (r < 15) ? "HINT" : "FREEZE";
        }

        itemTypes.put(id, type);

        broadcast(new GamePacket(GamePacket.Type.ITEM_SPAWN, id, pos, type));
    }

    private void handleItemPickup(ClientHandler handler, int itemId) {
        if (!activeItems.containsKey(itemId)) return;

        String type = itemTypes.get(itemId);

        activeItems.remove(itemId);
        itemTypes.remove(itemId);

        broadcast(new GamePacket(GamePacket.Type.ITEM_REMOVED, itemId));

        String picker = handler.getPlayerName();

        if ("HINT".equals(type)) {
            handleHintRequest(handler);
            return;
        }

        if ("TIME".equals(type)) {
            broadcast(new GamePacket(GamePacket.Type.TIME_BONUS, "타이머 +5초"));
            return;
        }

        if ("FREEZE".equals(type)) {
            for (String p : clients.keySet()) {
                if (!p.equals(picker)) {
                    broadcast(new GamePacket(
                        GamePacket.Type.PLAYER_FREEZE,
                        p,
                        5,
                        true
                    ));
                }
            }
        }
    }

    private void broadcastLobbyUpdate() {
        broadcast(new GamePacket(
            GamePacket.Type.LOBBY_UPDATE,
            hostName,
            new ConcurrentHashMap<>(playerReadyStatus),
            currentDifficulty,
            currentGameMode
        ));
    }

    private void broadcast(GamePacket packet) {
        for (ClientHandler h : clients.values()) {
            h.sendPacket(packet);
        }
    }
}