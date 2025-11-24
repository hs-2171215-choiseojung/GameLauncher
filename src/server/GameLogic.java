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

public class GameLogic {
    private Map<String, List<Rectangle>> roundAnswers = new HashMap<>();
    private Map<String, String> roundImagePaths = new HashMap<>();
    private Map<String, Dimension> originalDimensions = new HashMap<>();
    private Map<String, boolean[]> foundStatus = new HashMap<>();
    
    private Map<String, Integer> maxRounds = new HashMap<>();

    public GameLogic() throws IOException {
        System.out.println("[GameLogic] 게임 정답 목록 로드 중...");
        
        loadAnswersFor_Easy_1();
        loadAnswersFor_Easy_2();
        loadAnswersFor_Easy_3();
        
        loadAnswersFor_Normal_1();
        loadAnswersFor_Normal_2();
        loadAnswersFor_Normal_3();
        
        loadAnswersFor_Hard_1();
        loadAnswersFor_Hard_2();
        
        maxRounds.put("쉬움", 3);
        maxRounds.put("보통", 3);
        maxRounds.put("어려움", 2);
        
        System.out.println("[GameLogic] 전체 정답 목록 로드 완료.");
    }
    // ========== 쉬움 난이도 ==========
    private void loadAnswersFor_Easy_1() {
        String key = "쉬움_1";
        List<Rectangle> answers = new ArrayList<>();
        
        answers.add(new Rectangle(100, 151, 40, 40));
        answers.add(new Rectangle(360, 275, 40, 40));
        answers.add(new Rectangle(464, 71, 40, 40));
        answers.add(new Rectangle(661, 668, 40, 40));
        answers.add(new Rectangle(760, 663, 40, 40));
        answers.add(new Rectangle(588, 831, 40, 40));
        answers.add(new Rectangle(238, 813, 40, 40));
        answers.add(new Rectangle(404, 405, 40, 40));
        answers.add(new Rectangle(736, 576, 40, 40));
        answers.add(new Rectangle(144, 311, 40, 40));
        answers.add(new Rectangle(194, 185, 40, 40));

        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/easy1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Easy_2() {
        String key = "쉬움_2";
        List<Rectangle> answers = new ArrayList<>();
        
        answers.add(new Rectangle(717, 126, 40, 40));
        answers.add(new Rectangle(534, 196, 40, 40));
        answers.add(new Rectangle(243, 160, 40, 40));
        answers.add(new Rectangle(90, 133, 40, 40));
        answers.add(new Rectangle(35, 345, 40, 40));

        answers.add(new Rectangle(100, 481, 40, 40));
        answers.add(new Rectangle(103, 689, 40, 40));
        answers.add(new Rectangle(450, 767, 40, 40));
        answers.add(new Rectangle(243, 479, 40, 40));
        answers.add(new Rectangle(307, 459, 40, 40));

        answers.add(new Rectangle(450, 507, 40, 40));
        answers.add(new Rectangle(671, 614, 40, 40));
        answers.add(new Rectangle(681, 488, 40, 40));
        answers.add(new Rectangle(549, 381, 40, 40));
        answers.add(new Rectangle(690, 374, 40, 40));

        answers.add(new Rectangle(668, 304, 40, 40));
        answers.add(new Rectangle(756, 325, 40, 40));
        answers.add(new Rectangle(768, 581, 40, 40));
        answers.add(new Rectangle(613, 185, 40, 40));
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/easy2.jpg"); 
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Easy_3() {
        String key = "쉬움_3";
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
        roundImagePaths.put(key, "images/easy3.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }

    // ========== 보통 난이도 ==========
    private void loadAnswersFor_Normal_1() {
        String key = "보통_1";
        List<Rectangle> answers = new ArrayList<>();

       
        answers.add(new Rectangle(613, 128, 40, 40));
        answers.add(new Rectangle(724, 187, 40, 40));
        answers.add(new Rectangle(705, 258, 40, 40));
        answers.add(new Rectangle(632, 335, 40, 40));
        answers.add(new Rectangle(496, 377, 40, 40));
        answers.add(new Rectangle(607, 490, 40, 40));
        answers.add(new Rectangle(668, 486, 40, 40));
        answers.add(new Rectangle(765, 553, 40, 40));
        answers.add(new Rectangle(644, 774, 40, 40));
        answers.add(new Rectangle(586, 796, 40, 40));
        answers.add(new Rectangle(328, 685, 40, 40));
        answers.add(new Rectangle(214, 600, 40, 40));
        answers.add(new Rectangle(75, 583, 40, 40));
        answers.add(new Rectangle(139, 381, 40, 40));
        answers.add(new Rectangle(236, 410, 40, 40));
        answers.add(new Rectangle(250, 326, 40, 40));
        answers.add(new Rectangle(323, 211, 40, 40));

        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Normal_2() {
        String key = "보통_2";
        List<Rectangle> answers = new ArrayList<>();

        answers.add(new Rectangle(29, 274, 40, 40));
        answers.add(new Rectangle(63, 391, 40, 40));
        answers.add(new Rectangle(110, 479, 40, 40));
        answers.add(new Rectangle(190, 568, 40, 40));
        answers.add(new Rectangle(384, 515, 40, 40));
        answers.add(new Rectangle(357, 779, 40, 40));
        answers.add(new Rectangle(734, 833, 40, 40));
        answers.add(new Rectangle(449, 411, 40, 40));
        answers.add(new Rectangle(596, 376, 40, 40));
        answers.add(new Rectangle(676, 418, 40, 40));
        answers.add(new Rectangle(761, 377, 40, 40));
        answers.add(new Rectangle(472, 183, 40, 40));
        answers.add(new Rectangle(641, 199, 40, 40));
        answers.add(new Rectangle(544, 92, 40, 40));
        answers.add(new Rectangle(772, 173, 40, 40));

        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal2.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Normal_3() {
        String key = "보통_3";
        List<Rectangle> answers = new ArrayList<>();

        
        
        
        answers.add(new Rectangle(133, 831, 40, 40));
        answers.add(new Rectangle(122, 512, 40, 40));
        answers.add(new Rectangle(375, 316, 40, 40));
        answers.add(new Rectangle(651, 434, 40, 40));
        answers.add(new Rectangle(736, 507, 40, 40));
        answers.add(new Rectangle(466, 556, 40, 40));
        answers.add(new Rectangle(350, 592, 40, 40));
        answers.add(new Rectangle(423, 697, 40, 40));
        answers.add(new Rectangle(678, 862, 40, 40));
        answers.add(new Rectangle(517, 644, 40, 40));
        answers.add(new Rectangle(163, 583, 40, 40));
        
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/normal3.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }

    // ========== 어려움 난이도 ==========
    private void loadAnswersFor_Hard_1() {
        String key = "어려움_1";
        List<Rectangle> answers = new ArrayList<>();
        
      
        answers.add(new Rectangle(634, 61, 40, 40));
        answers.add(new Rectangle(66, 223, 40, 40));
        answers.add(new Rectangle(207, 410, 40, 40));
        answers.add(new Rectangle(97, 672, 40, 40));
        answers.add(new Rectangle(321, 666, 40, 40));
        answers.add(new Rectangle(294, 757, 40, 40));
        answers.add(new Rectangle(105, 811, 40, 40));
        answers.add(new Rectangle(469, 360, 40, 40));
        answers.add(new Rectangle(593, 235, 40, 40));
        answers.add(new Rectangle(641, 430, 40, 40));
        answers.add(new Rectangle(783, 308, 40, 40));
        answers.add(new Rectangle(697, 474, 40, 40));
        answers.add(new Rectangle(778, 495, 40, 40));
        answers.add(new Rectangle(637, 636, 40, 40));
        answers.add(new Rectangle(729, 772, 40, 40));
        answers.add(new Rectangle(642, 833, 40, 40));
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/hard1.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    
    private void loadAnswersFor_Hard_2() {
        String key = "어려움_2";
        List<Rectangle> answers = new ArrayList<>();

        answers.add(new Rectangle(190, 160, 40, 40));
        answers.add(new Rectangle(88, 303, 40, 40));
        answers.add(new Rectangle(299, 255, 40, 40));
        answers.add(new Rectangle(437, 269, 40, 40));
        answers.add(new Rectangle(496, 332, 40, 40));
        answers.add(new Rectangle(620, 207, 40, 40));
        answers.add(new Rectangle(763, 138, 40, 40));
        answers.add(new Rectangle(673, 425, 40, 40));
        answers.add(new Rectangle(739, 520, 40, 40));
        answers.add(new Rectangle(695, 765, 40, 40));
        answers.add(new Rectangle(763, 765, 40, 40));
        answers.add(new Rectangle(709, 823, 40, 40));
        answers.add(new Rectangle(63, 792, 40, 40));
        answers.add(new Rectangle(137, 610, 40, 40));
        answers.add(new Rectangle(273, 656, 40, 40));
        answers.add(new Rectangle(120, 536, 40, 40));
        answers.add(new Rectangle(39, 427, 40, 40));
        answers.add(new Rectangle(477, 474, 40, 40));
        
        roundAnswers.put(key, answers);
        roundImagePaths.put(key, "images/hard2.jpg");
        foundStatus.put(key, new boolean[answers.size()]);
        originalDimensions.put(key, new Dimension(850, 1202));
    }
    // ========== 공통 메서드 ==========
    
    public void loadRound(String difficulty, int round) {
        String key = difficulty + "_" + round;
        
        if (!roundAnswers.containsKey(key)) {
            System.out.println("[GameLogic] 경고: " + key + " 정보가 미리 로드되지 않아 동적 로드 시도...");
            try {
                 loadAnswersFromFile(difficulty, round);
            } catch (IOException e) {
                 System.out.println("[GameLogic] 동적 로드 실패: " + e.getMessage());
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
    
    private void loadAnswersFromFile(String difficulty, int round) throws IOException {
        String key = difficulty + "_" + round;
        String fileName = "answers/" + difficulty + "_" + round + ".txt";
        
        List<Rectangle> answers = new ArrayList<>();
        Dimension dim = new Dimension(800, 600);
        File file = new File(fileName);
        
        if (!file.exists()) throw new IOException(fileName + " 파일이 없습니다.");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            
            if ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                dim = new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            originalDimensions.put(key, dim);

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
    
    // ★ 특정 정답이 찾아졌는지 확인 (힌트용)
    public synchronized boolean isAnswerFound(String difficulty, int round, int answerIndex) {
        String key = difficulty + "_" + round;
        boolean[] found = foundStatus.get(key);
        if (found == null || answerIndex < 0 || answerIndex >= found.length) {
            return true; // 잘못된 인덱스는 "찾은 것"으로 처리
        }
        return found[answerIndex];
    }
    
    public int getMaxRounds(String difficulty) {
        return maxRounds.getOrDefault(difficulty, 1);
    }
    
    public boolean hasNextRound(String difficulty, int currentRound) {
        return currentRound < getMaxRounds(difficulty);
    }
}