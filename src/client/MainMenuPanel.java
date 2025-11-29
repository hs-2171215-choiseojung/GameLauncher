package client;

import model.UserData;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
public class MainMenuPanel extends JPanel {
    private GameLauncher launcher;
    
    public MainMenuPanel(GameLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(30, 30, 30, 30));
        setBackground(new Color(240, 248, 255));
        
       
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel("숨은 그림 찾기");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        titleLabel.setForeground(new Color(70, 130, 180));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(5, 1, 0, 15));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(50, 100, 50, 100));
        
        JButton singlePlayButton = createMenuButton("1인 플레이");
        JButton multiPlayButton = createMenuButton("멀티 플레이");
        JButton dynamicGameButton = createMenuButton("플래시 플레이");
        JButton myPageButton = createMenuButton("마이페이지");
        JButton exitButton = createMenuButton("종료");
        
        buttonPanel.add(singlePlayButton);
        buttonPanel.add(multiPlayButton);
        buttonPanel.add(dynamicGameButton);
        buttonPanel.add(myPageButton);
        buttonPanel.add(exitButton);
        
        add(buttonPanel, BorderLayout.CENTER);
        
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        UserData userData = UserData.getInstance();
        if (userData == null || userData.getNickname() == null) {
            JLabel userInfoLabel = new JLabel("플레이어: Guest | Lv.1");
            userInfoLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
            infoPanel.add(userInfoLabel);
        } else {
            JLabel userInfoLabel = new JLabel("플레이어: " + userData.getNickname() + 
                                              " | Lv." + userData.getLevel());
            userInfoLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
            infoPanel.add(userInfoLabel);
        }
        add(infoPanel, BorderLayout.SOUTH);
        
        singlePlayButton.addActionListener(e -> {
            launcher.startSinglePlayerGame();
        });
        
        multiPlayButton.addActionListener(e -> {
            launcher.switchToServerInput();
        });
        
        dynamicGameButton.addActionListener(e -> {
            launcher.switchToServerInputForFlashlight(); 
        });

        
        myPageButton.addActionListener(e -> {
            launcher.switchToMyPage();
        });
        
        exitButton.addActionListener(e -> {
            System.exit(0);
        });
    }
    
    private JButton createMenuButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        button.setBackground(Color.WHITE);
        button.setForeground(new Color(70, 130, 180));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 130, 180), 2),
            BorderFactory.createEmptyBorder(15, 30, 15, 30)
        ));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(70, 130, 180));
                button.setForeground(Color.WHITE);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(Color.WHITE);
                button.setForeground(new Color(70, 130, 180));
            }
        });
        
        return button;
    }
    
    // 사용자 정보 업데이트 (마이페이지에서 돌아올 때!)
    public void refreshUserInfo() {
        Component[] components = ((JPanel)getComponent(2)).getComponents();
        if (components.length > 0 && components[0] instanceof JLabel) {
            UserData userData = UserData.getInstance();
            ((JLabel)components[0]).setText("플레이어: " + userData.getNickname() + 
                                            " | Lv." + userData.getLevel());
        }
    }
}