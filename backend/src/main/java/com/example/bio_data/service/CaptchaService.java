package com.example.bio_data.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码服务
 */
@Service
public class CaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);
    
    // 验证码字符集（去除容易混淆的字符）
    private static final String CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CAPTCHA_LENGTH = 4;
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;
    private static final int CAPTCHA_EXPIRE_MINUTES = 5; // 验证码5分钟过期
    
    // 存储验证码的Map，key为sessionId，value为验证码信息
    private final ConcurrentHashMap<String, CaptchaInfo> captchaStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public CaptchaService() {
        // 定时清理过期验证码
        scheduler.scheduleAtFixedRate(this::cleanExpiredCaptcha, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 生成验证码
     * @param sessionId 会话ID
     * @return 验证码图片的Base64编码
     */
    public String generateCaptcha(String sessionId) {
        try {
            // 生成随机验证码文本
            String captchaText = generateCaptchaText();
            
            // 创建图片
            BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            // 设置抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 填充背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            
            // 绘制干扰线
            drawNoiseLine(g2d);
            
            // 绘制验证码文本
            drawCaptchaText(g2d, captchaText);
            
            // 添加噪点
            drawNoisePoints(g2d);
            
            g2d.dispose();
            
            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
            
            // 存储验证码信息
            long expireTime = System.currentTimeMillis() + CAPTCHA_EXPIRE_MINUTES * 60 * 1000;
            captchaStore.put(sessionId, new CaptchaInfo(captchaText, expireTime));
            
            logger.info("生成验证码成功，sessionId: {}", sessionId);
            return "data:image/png;base64," + base64Image;
            
        } catch (IOException e) {
            logger.error("生成验证码失败: {}", e.getMessage());
            throw new RuntimeException("验证码生成失败", e);
        }
    }
    
    /**
     * 验证验证码
     * @param sessionId 会话ID
     * @param userInput 用户输入的验证码
     * @return 验证是否成功
     */
    public boolean verifyCaptcha(String sessionId, String userInput) {
        if (sessionId == null || userInput == null) {
            logger.warn("验证码验证失败：参数为空");
            return false;
        }
        
        CaptchaInfo captchaInfo = captchaStore.get(sessionId);
        if (captchaInfo == null) {
            logger.warn("验证码验证失败：找不到对应的验证码，sessionId: {}", sessionId);
            return false;
        }
        
        // 验证码使用后立即删除（一次性使用）
        captchaStore.remove(sessionId);
        
        // 检查是否过期
        if (System.currentTimeMillis() > captchaInfo.expireTime) {
            logger.warn("验证码验证失败：验证码已过期，sessionId: {}", sessionId);
            return false;
        }
        
        // 验证码比对（不区分大小写）
        boolean isValid = captchaInfo.text.equalsIgnoreCase(userInput.trim());
        if (isValid) {
            logger.info("验证码验证成功，sessionId: {}", sessionId);
        } else {
            logger.warn("验证码验证失败：验证码不匹配，sessionId: {}, expected: {}, actual: {}", 
                       sessionId, captchaInfo.text, userInput);
        }
        
        return isValid;
    }
    
    /**
     * 生成随机验证码文本
     */
    private String generateCaptchaText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return sb.toString();
    }
    
    /**
     * 绘制验证码文本
     */
    private void drawCaptchaText(Graphics2D g2d, String text) {
        int x = 10;
        for (int i = 0; i < text.length(); i++) {
            // 随机字体大小
            int fontSize = 20 + random.nextInt(8);
            Font font = new Font("Arial", Font.BOLD, fontSize);
            g2d.setFont(font);
            
            // 随机颜色
            g2d.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            
            // 随机旋转角度
            double angle = (random.nextDouble() - 0.5) * 0.4;
            g2d.rotate(angle);
            
            // 绘制字符
            char c = text.charAt(i);
            int y = 25 + random.nextInt(8);
            g2d.drawString(String.valueOf(c), x, y);
            
            // 恢复旋转
            g2d.rotate(-angle);
            
            x += 25;
        }
    }
    
    /**
     * 绘制干扰线
     */
    private void drawNoiseLine(Graphics2D g2d) {
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(random.nextInt(150) + 100, random.nextInt(150) + 100, random.nextInt(150) + 100));
            int x1 = random.nextInt(IMAGE_WIDTH);
            int y1 = random.nextInt(IMAGE_HEIGHT);
            int x2 = random.nextInt(IMAGE_WIDTH);
            int y2 = random.nextInt(IMAGE_HEIGHT);
            g2d.drawLine(x1, y1, x2, y2);
        }
    }
    
    /**
     * 绘制噪点
     */
    private void drawNoisePoints(Graphics2D g2d) {
        for (int i = 0; i < 50; i++) {
            g2d.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            int x = random.nextInt(IMAGE_WIDTH);
            int y = random.nextInt(IMAGE_HEIGHT);
            g2d.fillOval(x, y, 1, 1);
        }
    }
    
    /**
     * 清理过期验证码
     */
    private void cleanExpiredCaptcha() {
        long currentTime = System.currentTimeMillis();
        
        // 先收集要删除的key
        java.util.List<String> expiredKeys = new java.util.ArrayList<>();
        captchaStore.entrySet().forEach(entry -> {
            if (currentTime > entry.getValue().expireTime) {
                expiredKeys.add(entry.getKey());
            }
        });
        
        // 删除过期的验证码
        for (String key : expiredKeys) {
            captchaStore.remove(key);
        }
        
        if (!expiredKeys.isEmpty()) {
            logger.info("清理过期验证码 {} 个", expiredKeys.size());
        }
    }
    
    /**
     * 验证码信息内部类
     */
    private static class CaptchaInfo {
        final String text;
        final long expireTime;
        
        CaptchaInfo(String text, long expireTime) {
            this.text = text;
            this.expireTime = expireTime;
        }
    }
}
