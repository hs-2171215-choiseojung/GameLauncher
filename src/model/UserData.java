package model;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

// 사용자 데이터 (닉네임, 레벨, 경험치) 관리 클래스
public class UserData implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ACCOUNTS_FILE = "accounts.dat";
    private static final String LAST_LOGIN_FILE = "lastlogin.dat";
    
    private String nickname;
    private String password;
    private int level;
    private int experience;
    
    // 모든 계정 정보 (닉네임 -> UserData)
    private static Map<String, UserData> allAccounts = new HashMap<>();
    
    // 현재 로그인한 사용자
    private static UserData instance = null;
    
    private UserData(String nickname, String password) {
        this.nickname = nickname;
        this.password = password;
        this.level = 1;
        this.experience = 0;
    }
    
    // 저장된 데이터 로드 또는 새로 생성
    public static UserData getInstance() {
        if (instance == null) {
            loadAllAccounts();
            // 마지막 로그인 정보 확인
            String lastLoginNickname = loadLastLogin();
            if (lastLoginNickname != null && allAccounts.containsKey(lastLoginNickname)) {
                instance = allAccounts.get(lastLoginNickname);
                System.out.println("[UserData] 자동 로그인: " + lastLoginNickname);
            }
        }
        return instance;
    }
    
    // 회원가입 (새 계정 생성)
    public static boolean register(String nickname, String password) {
        loadAllAccounts();
        
        if (allAccounts.containsKey(nickname)) {
            return false; // 이미 존재하는 닉네임
        }
        
        UserData newUser = new UserData(nickname, password);
        allAccounts.put(nickname, newUser);
        saveAllAccounts();
        return true;
    }
    
    // 로그인
    public static boolean login(String nickname, String password) {
        loadAllAccounts();
        
        UserData user = allAccounts.get(nickname);
        if (user != null && user.password.equals(password)) {
            instance = user;
            saveLastLogin(nickname); // 마지막 로그인 정보 저장
            return true;
        }
        return false;
    }
    
    // 닉네임 존재 여부 확인
    public static boolean nicknameExists(String nickname) {
        loadAllAccounts();
        return allAccounts.containsKey(nickname);
    }
    
    // 모든 계정 정보 로드
    private static void loadAllAccounts() {
        if (!allAccounts.isEmpty()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ACCOUNTS_FILE))) {
            allAccounts = (Map<String, UserData>) ois.readObject();
        } catch (FileNotFoundException e) {
            allAccounts = new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            allAccounts = new HashMap<>();
        }
    }
    
    // 모든 계정 정보 저장
    private static void saveAllAccounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ACCOUNTS_FILE))) {
            oos.writeObject(allAccounts);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // 마지막 로그인 정보 저장
    private static void saveLastLogin(String nickname) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LAST_LOGIN_FILE))) {
            oos.writeObject(nickname);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // 마지막 로그인 정보 로드
    private static String loadLastLogin() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(LAST_LOGIN_FILE))) {
            return (String) ois.readObject();
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // 마지막 로그인 정보 삭제
    private static void clearLastLogin() {
        File file = new File(LAST_LOGIN_FILE);
        if (file.exists()) {
            file.delete();
        }
    }
    
    // 경험치 추가 (레벨업 자동 계산)
    public void addExperience(int exp) {
        this.experience += exp;
        
        while (this.experience >= getExpForNextLevel()) {
            this.experience -= getExpForNextLevel();
            this.level++;
        }
        saveAllAccounts();
    }
    
    // 다음 레벨까지 필요한 경험치
    public int getExpForNextLevel() {
        return 100 * level;
    }
    
    // Getter
    public String getNickname() { return nickname; }
    public int getLevel() { return level; }
    public int getExperience() { return experience; }
    
    // 비밀번호 변경
    public void changePassword(String oldPassword, String newPassword) throws Exception {
        if (!this.password.equals(oldPassword)) {
            throw new Exception("현재 비밀번호가 일치하지 않습니다.");
        }
        this.password = newPassword;
        saveAllAccounts();
    }
    
    // 로그아웃 (인스턴스 초기화 + 마지막 로그인 정보 삭제)
    public static void logout() {
        if (instance != null) {
            saveAllAccounts();
            instance = null;
            clearLastLogin(); // 로그아웃 시 자동 로그인 정보 삭제
        }
    }
}