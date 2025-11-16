package server;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// 정답(Rectangle)을 서버에 '수동'으로 입력
public class GameLogic {
    private Map<String, List<Rectangle>> roundAnswers = new HashMap<>();
    private Map<String, String> roundImagePaths = new HashMap<>();
    private Map<String, Dimension> originalDimensions = new HashMap<>();
    private Map<String, boolean[]> foundStatus = new HashMap<>();
    
    // 각 난이도별 최대 라운드 수
    private Map<String, Integer> maxRounds = new HashMap<>();

    public GameLogic() throws IOException {
        System.out.println("[GameLogic] 게임 정답 목록 로드 중...");
        
        // 쉬움: 3라운드
        loadAnswersFor_Easy_1();
        loadAnswersFor_Easy_2();
        loadAnswersFor_Easy_3();
        
        // 보통: 3라운드
        loadAnswersFor_Normal_1();
        loadAnswersFor_Normal_2();
        loadAnswersFor_Normal_3();
        
        // 어려움: 2라운드
        loadAnswersFor_Hard_1();
        loadAnswersFor_Hard_2();
        
        // 최대 라운드 설정
        maxRounds.put("쉬움", 3);
        maxRounds.put("보통", 3);
        maxRounds.put("어려움", 2);
        
        System.out.println("[GameLogic] 전체 정답 목록 로드 완료.");
    }

    // ========== 쉬움 난이도 ==========
    private void loadAnswersFor_Easy_1() {
        String key = "쉬움_1";
        List<Rectangle> answers = new ArrayList<>();

        answers.add(new Rectangle(688, 154, 40, 40));
        answers.add(new Rectangle(159, 184, 40, 40));
        answers.add(new Rectangle(278, 115, 40, 40));
        answers.add(new Rectangle(415, 240, 40, 40));
        answers.add(new Rectangle(511, 121, 40, 40));
        
        answers.add(new Rectangle(683, 479, 40, 40));
        answers.add(new Rectangle(735, 302, 40, 40));
        answers.add(new Rectangle(716, 366, 40, 40));
        answers.add(new Rectangle(465, 380, 40, 40));
        answers.add(new Rectangle(490, 487, 40, 40));
        
        answers.add(new Rectangle(328, 669, 40, 40));
        answers.add(new Rectangle(666, 863, 40, 40));
        answers.add(new Rectangle(793, 738, 40, 40));
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/easy1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Easy_2() {
        String key = "쉬움_2";
        List<Rectangle> answers = new ArrayList<>();

        //쉬움 2라운드 정답 좌표 입력 예정 !! 이 부분부터 좌표 추가해야해요!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/easy2.jpg"); 
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Easy_3() {
        String key = "쉬움_3";
        List<Rectangle> answers = new ArrayList<>();

        //쉬움 3라운드 정답 좌표 입력 예정!!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/easy3.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }

    // ========== 보통 난이도 ==========
    private void loadAnswersFor_Normal_1() {
        String key = "보통_1";
        List<Rectangle> answers = new ArrayList<>();

        //보통 1라운드 정답 좌표 입력 예정!!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Normal_2() {
        String key = "보통_2";
        List<Rectangle> answers = new ArrayList<>();

        //보통 2라운드 정답 좌표 입력 예정!!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal2.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Normal_3() {
        String key = "보통_3";
        List<Rectangle> answers = new ArrayList<>();

        //보통 3라운드 정답 좌표 입력 예정!!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal3.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }

    // ========== 어려움 난이도 ==========
    private void loadAnswersFor_Hard_1() {
        String key = "어려움_1";
        List<Rectangle> answers = new ArrayList<>();

        // 어려움 1라운드 정답 좌표 입력 예정!!
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/hard1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Hard_2() {
        String key = "어려움_2";
        List<Rectangle> answers = new ArrayList<>();

        //어려움 2라운드 정답 좌표 입력 예정!!
        
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/hard2.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }

    // ========== 공통 메서드 ==========
    
    public void loadRound(String difficulty, int round) {
        String key = difficulty + "_" + round;
        
        // 미리 로드된 정답이 없으면 파일에서 로드 시도
        if (!roundAnswers.containsKey(key)) {
            System.out.println("[GameLogic] 경고: " + key + " 정보가 미리 로드되지 않아 동적 로드 시도...");
            try {
                 loadAnswersFromFile(difficulty, round);
            } catch (IOException e) {
                 System.out.println("[GameLogic] 동적 로드 실패: " + e.getMessage());
                 // 기본값으로 쉬움_1 사용
                 key = "쉬움_1"; 
            }
        }
        
        if (roundAnswers.containsKey(key)) {
            int count = roundAnswers.get(key).size();
            foundStatus.put(key, new boolean[count]);
            System.out.println("[GameLogic] " + key + " 라운드 정답 " + count + "개 상태 초기화.");
        } else {
            System.out.println("[GameLogic] 오류: " + key + " 정답 목록을 찾을 수 없습니다.");
        }
    }
    
    // 텍스트 파일에서 정답 로드 (선택사항)
    private void loadAnswersFromFile(String difficulty, int round) throws IOException {
        String key = difficulty + "_" + round;
        String fileName = "answers/" + difficulty + "_" + round + ".txt";
        
        List<Rectangle> answers = new ArrayList<>();
        Dimension dim = new Dimension(800, 600); // 기본값
        File file = new File(fileName);
        
        if (!file.exists()) throw new IOException(fileName + " 파일이 없습니다.");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            
            // 1. 첫 줄: 원본 크기
            if ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                dim = new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            originalDimensions.put(key, dim);

            // 2. 나머지: 정답 좌표
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                answers.add(new Rectangle(
                    Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())
                ));
            }
        } catch (Exception e) {
            throw new IOException(fileName + " 파일 형식 오류", e);
        }

        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/" + difficulty + round + ".jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        System.out.println("[GameLogic] " + key + " 정답 " + answers.size() + "개 (파일) 로드 완료.");
    }
    
    public String getImagePath(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return roundImagePaths.getOrDefault(key, "images/easy1.jpg");
    }
    
    public List<Rectangle> getOriginalAnswers(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return roundAnswers.get(key);
    }
    
    public Dimension getOriginalDimension(String difficulty, int round) {
        String key = difficulty + "_" + round;
        return originalDimensions.get(key);
    }

    public synchronized boolean checkAnswer(String difficulty, int round, int answerIndex) {
        String key = difficulty + "_" + round;
        boolean[] found = foundStatus.get(key);
        
        if (found == null || answerIndex < 0 || answerIndex >= found.length) {
            System.out.println("[GameLogic] 판정 오류: 잘못된 인덱스 " + answerIndex);
            return false; 
        }
        
        if (found[answerIndex]) {
            System.out.println("[GameLogic] " + key + " " + answerIndex + "번은 이미 찾음.");
            return false; 
        }
        
        found[answerIndex] = true; 
        System.out.println("[GameLogic] " + key + " 정답 " + answerIndex + "번 찾음!");
        return true;
    }
    
    public Point getAnswerCenter(String difficulty, int round, int answerIndex) {
        String key = difficulty + "_" + round;
        List<Rectangle> answers = roundAnswers.get(key);
        if (answers == null || answerIndex < 0 || answerIndex >= answers.size()) {
            return null;
        }
        Rectangle r = answers.get(answerIndex);
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        return new Point(cx, cy);
    }

    public boolean areAllFound(String difficulty, int round) {
        String key = difficulty + "_" + round; 
        boolean[] found = foundStatus.get(key);
        if (found == null) return false;
        for (boolean f : found) {
            if (!f) return false; 
        }
        return true;
    }
    
    // 해당 난이도의 최대 라운드 수 반환
    public int getMaxRounds(String difficulty) {
        return maxRounds.getOrDefault(difficulty, 1);
    }
    
    // 다음 라운드가 있는지 확인
    public boolean hasNextRound(String difficulty, int currentRound) {
        return currentRound < getMaxRounds(difficulty);
    }
}