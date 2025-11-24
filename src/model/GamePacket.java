package model;

import java.io.Serializable;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.Map;

public class GamePacket implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        JOIN,
        CLICK,
        MESSAGE,
        READY_STATUS,
        SETTINGS_UPDATE,
        START_GAME_REQUEST,
        LOBBY_UPDATE,
        ROUND_START,
        RESULT,
        SCORE,
        TIMER_END,
        GAME_OVER,
        MOUSE_MOVE,
        ITEM_SPAWN,
        ITEM_REMOVED,
        ITEM_PICKUP,
        PLAYER_FREEZE,
        TIME_BONUS,
        HINT_REQUEST,    // ★ 추가
        HINT_RESPONSE    // ★ 추가
    }

    private final Type type;
    private final String sender;

    // 2. 메세지/라운드/정답
    private String message;
    private int round;
    private int answerIndex;
    private boolean correct;

    // 3. 그림/정답 원본
    private List<Rectangle> originalAnswers;
    private Dimension originalDimension;

    // 4. 게임 설정
    private String gameType;
    private String difficulty;
    private String gameMode;
    private String roomNumber;
    private String hostName;

    // 5. 상태 맵들
    private Map<String, Boolean> playerReadyStatus;
    private Map<String, Integer> playerIndexMap;

    // 6. 마우스 관련
    private int playerIndex;
    private double x;
    private double y;

    // 7. 커서 인덱스
    private int cursorIndex;

    // 8. 아이템
    private int itemId;
    private Point itemPosition;
    private String itemType;

    // 9. 얼리기
    private int freezeDuration;
    private boolean freeze;

    // 10. 기타 (ready)
    private boolean isReady;

    // ★ 11. 힌트 관련
    private Point hintPosition;
    private int remainingHints;

    // --- 생성자들 ---

    // JOIN
    public GamePacket(Type type, String sender, String roomNumber, boolean isJoin) {
        this.type = type;
        this.sender = sender;
        this.message = roomNumber;
        this.roomNumber = roomNumber;
    }

    // CLICK
    public GamePacket(Type type, String sender, int answerIndex) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
    }

    // MESSAGE
    public GamePacket(Type type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }

    // RESULT
    public GamePacket(Type type, String sender, int answerIndex, boolean correct, String message) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
        this.correct = correct;
        this.message = message;
    }

    // ROUND_START (기존 NORMAL)
    public GamePacket(Type type, int round, String imagePath,
                      List<Rectangle> originalAnswers, Dimension originalDimension,
                      Map<String, Integer> playerIndexMap, String gameMode) {
        this.type = type;
        this.sender = "SERVER";
        this.round = round;
        this.message = imagePath;
        this.originalAnswers = originalAnswers;
        this.originalDimension = originalDimension;
        this.playerIndexMap = playerIndexMap;
        this.gameMode = gameMode;
        this.gameType = "NORMAL";
    }

    // ROUND_START (FLASH/NORMAL 모두 지원)
    public GamePacket(Type type, int round, String imagePath,
                      List<Rectangle> originalAnswers, Dimension originalDimension,
                      Map<String, Integer> playerIndexMap, String gameMode, String gameType) {
        this.type = type;
        this.sender = "SERVER";
        this.round = round;
        this.message = imagePath;
        this.originalAnswers = originalAnswers;
        this.originalDimension = originalDimension;
        this.playerIndexMap = playerIndexMap;
        this.gameMode = gameMode;
        this.gameType = gameType;
    }

    // 메시지 타입
    public GamePacket(Type type, String message) {
        this.type = type;
        this.sender = "SERVER";
        this.message = message;
    }

    // READY
    public GamePacket(Type type, String sender, boolean isReady) {
        this.type = type;
        this.sender = sender;
        this.isReady = isReady;
    }

    // SETTINGS_UPDATE / 기존 START_GAME_REQUEST
    public GamePacket(Type type, String sender, String difficulty, String gameMode) {
        this.type = type;
        this.sender = sender;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
    }

    // START_GAME_REQUEST (NORMAL/FLASH 구분 버전)
    public GamePacket(Type type, String sender, String difficulty, String gameMode, String gameType) {
        this.type = type;
        this.sender = sender;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.gameType = gameType;
    }

    // LOBBY_UPDATE
    public GamePacket(Type type, String hostName,
                      Map<String, Boolean> playerReadyStatus,
                      String difficulty, String gameMode) {
        this.type = type;
        this.sender = "SERVER";
        this.hostName = hostName;
        this.playerReadyStatus = playerReadyStatus;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
    }

    // MOUSE_MOVE (cursorIndex 포함)
    public GamePacket(Type type, String sender, int playerIndex, double x, double y, int cursorIndex) {
        this.type = type;
        this.sender = sender;
        this.playerIndex = playerIndex;
        this.x = x;
        this.y = y;
        this.cursorIndex = cursorIndex;
    }

    // MOUSE_MOVE (기존 - 하위 호환성)
    public GamePacket(Type type, String sender, int playerIndex, double x, double y) {
        this(type, sender, playerIndex, x, y, 1);
    }

    // ITEM_SPAWN
    public GamePacket(Type type, int itemId, Point itemPosition, String itemType) {
        this.type = type;
        this.sender = "SERVER";
        this.itemId = itemId;
        this.itemPosition = itemPosition;
        this.itemType = itemType;
    }

    // ITEM_REMOVED
    public GamePacket(Type type, int itemId) {
        this.type = type;
        this.sender = "SERVER";
        this.itemId = itemId;
    }

    // ITEM_PICKUP
    public GamePacket(Type type, String sender, int itemId, Object unused) {
        this.type = type;
        this.sender = sender;
        this.itemId = itemId;
    }

    // PLAYER_FREEZE
    public GamePacket(Type type, String sender, int freezeDuration, boolean freeze) {
        this.type = type;
        this.sender = sender;
        this.message = sender;
        this.freezeDuration = freezeDuration;
        this.freeze = freeze;
    }

    // ★ HINT_RESPONSE (힌트 응답 - 위치와 남은 횟수)
    public GamePacket(Type type, Point hintPosition, int remainingHints, String message) {
        this.type = type;
        this.sender = "SERVER";
        this.hintPosition = hintPosition;
        this.remainingHints = remainingHints;
        this.message = message;
    }
    
 // setRemainingHints() 메서드 추가
    public void setRemainingHints(int hints) {
        this.remainingHints = hints;
    }

    // --- Getter ---
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getRound() { return round; }
    public int getAnswerIndex() { return answerIndex; }
    public boolean isCorrect() { return correct; }
    public List<Rectangle> getOriginalAnswers() { return originalAnswers; }
    public Dimension getOriginalDimension() { return originalDimension; }

    public boolean isReady() { return isReady; }
    public String getDifficulty() { return difficulty; }
    public String getGameMode() { return gameMode; }
    public String getHostName() { return hostName; }
    public Map<String, Boolean> getPlayerReadyStatus() { return playerReadyStatus; }
    public String getRoomNumber() {
        return (roomNumber != null) ? roomNumber : message;
    }

    public int getPlayerIndex() { return playerIndex; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Map<String, Integer> getPlayerIndexMap() { return playerIndexMap; }
    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getGameType() { return gameType; }
    
    public int getCursorIndex() { return cursorIndex; }
    public void setCursorIndex(int cursorIndex) { this.cursorIndex = cursorIndex; }

    public int getItemId() { return itemId; }
    public Point getItemPosition() { return itemPosition; }
    public String getItemType() { return itemType; }
    
    public int getFreezeDuration() { return freezeDuration; }
    public boolean isFreeze() { return freeze; }
    
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    // ★ 힌트 관련 getter
    public Point getHintPosition() { return hintPosition; }
    public int getRemainingHints() { return remainingHints; }
}