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

    private int currentRound = 0;
    private String hostName = null;
    private String gameState = "LOBBY";
    private String currentDifficulty = "쉽음";
    private String currentGameMode = "협동";
    private String gameType = "NORMAL";

    private final Map<String, Integer> cursorIndexMap = new ConcurrentHashMap<>();

    // 라운드당 힌트 3회
    private int roundHintCount = 3;

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

        // 비사용 커서 인덱스 배정 (1~5 중 남은 번호)
        boolean[] used = new boolean[6];
        for (int idx : cursorIndexMap.values()) {
            if (idx >= 1 && idx <= 5) used[idx] = true;
        }

        int assignedCursorIndex = 1;
        for (int i = 1; i <= 5; i++) {
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
        return true;
    }

    // 플레이어 제거
    public synchronized boolean removePlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName();

        if (!clients.containsKey(playerName)) return false;

        clients.remove(playerName);
        playerReadyStatus.remove(playerName);
        scores.remove(playerName);
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

        // 호스트가 나간 경우 새로운 호스트 지정
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

        // 게임 시작 요청
        if (packet.getType() == GamePacket.Type.START_GAME_REQUEST) {

            if (!handler.getPlayerName().equals(hostName)) return;

            boolean allReady = true;
            for (String name : playerReadyStatus.keySet()) {
                if (!playerReadyStatus.get(name) && !name.equals(hostName)) {
                    allReady = false;
                    break;
                }
            }
            if (!allReady) return;

            this.gameType = packet.getGameType();
            this.currentDifficulty = packet.getDifficulty();
            this.currentGameMode = packet.getGameMode();

            currentRound = 1;
            gameLogic.loadRound(currentDifficulty, currentRound);

            gameState = "IN_GAME";

            scores.clear();
            clients.keySet().forEach(p -> scores.put(p, 0));

            roundHintCount = 3;

            for (String p : clients.keySet()) {
                GamePacket start = new GamePacket(
                    GamePacket.Type.ROUND_START,
                    currentRound,
                    gameLogic.getImagePath(currentDifficulty, currentRound),
                    gameLogic.getOriginalAnswers(currentDifficulty, currentRound),
                    gameLogic.getOriginalDimension(currentDifficulty, currentRound),
                    new HashMap<>(cursorIndexMap),
                    currentGameMode,
                    gameType
                );

                start.setCursorIndex(cursorIndexMap.getOrDefault(p, 1));
                start.setRemainingHints(roundHintCount);

                clients.get(p).sendPacket(start);
            }

            if ("경쟁".equals(currentGameMode)) {
                StringBuilder sb = new StringBuilder();
                sb.append("[점수]\n");
                for (String pName : scores.keySet()) {
                    sb.append(pName).append(" : ").append(scores.get(pName)).append("점\n");
                }
                broadcast(new GamePacket(GamePacket.Type.SCORE, "SERVER", sb.toString()));
            }

            if ("FLASH".equalsIgnoreCase(gameType)) startItemSpawner();

            return;
        }

        // IN_GAME
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
                    processClick(handler, packet);
                    break;

                case ITEM_PICKUP:
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
                    playerReadyStatus.put(handler.getPlayerName(), packet.isReady());
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

    // 힌트 처리
    private void handleHintRequest(ClientHandler handler) {
        String playerName = handler.getPlayerName();

        if (roundHintCount <= 0) {
            handler.sendPacket(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[힌트] 이번 라운드 힌트를 모두 사용했습니다! (0/3)"
            ));
            return;
        }

        List<Rectangle> answers = gameLogic.getOriginalAnswers(currentDifficulty, currentRound);
        List<Integer> unfound = new ArrayList<>();

        for (int i = 0; i < answers.size(); i++) {
            if (!gameLogic.isAnswerFound(currentDifficulty, currentRound, i)) {
                unfound.add(i);
            }
        }

        if (unfound.isEmpty()) {
            handler.sendPacket(new GamePacket(
                GamePacket.Type.MESSAGE,
                "SERVER",
                "[힌트] 이미 모든 정답을 찾았습니다!"
            ));
            return;
        }

        int idx = unfound.get(random.nextInt(unfound.size()));
        Point hintPos = gameLogic.getAnswerCenter(currentDifficulty, currentRound, idx);

        roundHintCount--;

        if ("경쟁".equals(currentGameMode)) {
            scores.put(playerName, scores.get(playerName) - 5);
        }

        broadcast(new GamePacket(
            GamePacket.Type.HINT_RESPONSE,
            hintPos,
            roundHintCount,
            "[힌트] " + playerName + "님이 힌트를 사용했습니다! (남은 힌트: " + roundHintCount + "/3)"
        ));
    }

    // 클릭 처리
    private void processClick(ClientHandler handler, GamePacket packet) {
        String name = handler.getPlayerName();
        int index = packet.getAnswerIndex();
        boolean correct = false;

        if (index >= 0) {
            correct = gameLogic.checkAnswer(currentDifficulty, currentRound, index);
        }

        if (correct)
            scores.put(name, scores.get(name) + 10);
        else
            scores.put(name, scores.get(name) - 5);

        if ("경쟁".equals(currentGameMode)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[점수]\n");
            for (String pName : scores.keySet()) {
                sb.append(pName).append(" : ").append(scores.get(pName)).append("점\n");
            }
            broadcast(new GamePacket(GamePacket.Type.SCORE, "SERVER", sb.toString()));
        }

        String msg = correct ?
            "[정답] 숨은 그림을 찾았습니다!" :
            "[오답] 해당 위치에는 숨은 그림이 없습니다.";

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

        if (correct && gameLogic.areAllFound(currentDifficulty, currentRound)) {
            handleRoundComplete();
        }
    }

    // 라운드 종료
    private void handleRoundComplete() {

        if (gameLogic.hasNextRound(currentDifficulty, currentRound)) {

            currentRound++;
            gameLogic.loadRound(currentDifficulty, currentRound);
            roundHintCount = 3;

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

                clients.get(p).sendPacket(next);
            }

        } else {
            broadcast(new GamePacket(GamePacket.Type.GAME_OVER, "모든 라운드 종료!"));
            currentRound = 0;
            gameState = "LOBBY";
            roundHintCount = 3;

            stopItemSpawner();
            broadcastLobbyUpdate();
        }
    }

    // 아이템 스폰
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

    // 아이템 획득 처리
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

    // 로비 업데이트
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
