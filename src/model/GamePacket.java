package model;

import java.io.Serializable;
import java.awt.Rectangle; 
import java.awt.Dimension; 
import java.util.List; 
import java.util.Map; // (추가)

// 대기방(Lobby) 흐름에 맞게 패킷 구조 변경
public class GamePacket implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        JOIN,         // [C->S] 접속
        CLICK,        // [C->S] 정답 클릭 (인덱스)
        MESSAGE,      // [C<->S] 채팅
        
        // --- (대기방 기능 추가) ---
        READY_STATUS,       // [C->S] 준비/준비해제 상태 변경
        SETTINGS_UPDATE,    // [C->S] (방장) 난이도/모드 변경
        START_GAME_REQUEST, // [C->S] (방장) 게임 시작 요청
        LOBBY_UPDATE,       // [S->C] (대기방) 인원/상태 목록 갱신
        // -----------------------
        
        ROUND_START,  // [S->C] 라운드 시작
        RESULT,       // [S->C] 클릭 결과
        SCORE,        // [S->C] 점수판
        TIMER_END,    // [S->C] 타이머 종료
        GAME_OVER     // [S->C] 게임 종료
    }

    // --- 데이터 필드 ---
    private final Type type;
    private final String sender;
    private String message;
    private int round;
    private int answerIndex; 
    private boolean correct;
    private List<Rectangle> originalAnswers; 
    private Dimension originalDimension; 

    // --- 대기방 기능 필드 ---
    private boolean isReady; // READY_STATUS 용
    private String difficulty; // SETTINGS_UPDATE, START_GAME_REQUEST 용
    private String gameMode;   // SETTINGS_UPDATE, START_GAME_REQUEST 용
    private String roomNumber; // JOIN 시 사용
    
    private String hostName; // LOBBY_UPDATE 용
    private Map<String, Boolean> playerReadyStatus; // LOBBY_UPDATE 용

    // --- 생성자 ---

    // 1. JOIN (접속)
    public GamePacket(Type type, String sender, String roomNumber, boolean isJoin) {
        this.type = type;
        this.sender = sender;
        this.message = roomNumber;
    }

    // 2. CLICK (정답 클릭)
    public GamePacket(Type type, String sender, int answerIndex) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
    }

    // 3. MESSAGE (채팅, 힌트, SCORE, TIMER_END, GAME_OVER 등)
    public GamePacket(Type type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }
    
    // 4. RESULT (클릭 결과)
    public GamePacket(Type type, String sender, int answerIndex, boolean correct, String message) {
        this.type = type;
        this.sender = sender;
        this.answerIndex = answerIndex;
        this.correct = correct;
        this.message = message; 
    }

    // 5. ROUND_START (라운드 시작)
    public GamePacket(Type type, int round, String imagePath, List<Rectangle> originalAnswers, Dimension originalDimension) {
        this.type = type;
        this.sender = "SERVER";
        this.round = round;
        this.message = imagePath; 
        this.originalAnswers = originalAnswers;
        this.originalDimension = originalDimension;
    }
    
    public GamePacket(Type type, String message) {
        this.type = type;
        this.sender = "SERVER";
        this.message = message;
    }
    
    // 6. READY_STATUS (준비 상태)
    public GamePacket(Type type, String sender, boolean isReady) {
        this.type = type;
        this.sender = sender;
        this.isReady = isReady;
    }
    
    // 7. SETTINGS_UPDATE / START_GAME_REQUEST (설정 변경 / 시작)
    public GamePacket(Type type, String sender, String difficulty, String gameMode) {
        this.type = type;
        this.sender = sender;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
    }
    
    // 8. LOBBY_UPDATE (대기방 갱신)
    public GamePacket(Type type, String hostName, Map<String, Boolean> playerReadyStatus, String difficulty, String gameMode) {
        this.type = type;
        this.sender = "SERVER";
        this.hostName = hostName;
        this.playerReadyStatus = playerReadyStatus;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
    }


    // --- Getter 메소드 ---
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public int getRound() { return round; }
    public int getAnswerIndex() { return answerIndex; }
    public boolean isCorrect() { return correct; }
    public List<Rectangle> getOriginalAnswers() { return originalAnswers; }
    public Dimension getOriginalDimension() { return originalDimension; }
    
    // (대기방 Getter 추가)
    public boolean isReady() { return isReady; }
    public String getDifficulty() { return difficulty; }
    public String getGameMode() { return gameMode; }
    public String getHostName() { return hostName; }
    public Map<String, Boolean> getPlayerReadyStatus() { return playerReadyStatus; }
    public String getRoomNumber() { return roomNumber; }
}