package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 令牌清理定时任务服务
 */
@Service
public class TokenCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(TokenCleanupService.class);

    @Autowired
    private AuthService authService;

    /**
     * 清理过期的激活令牌
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void cleanupExpiredActivationTokens() {
        try {
            logger.info("开始清理过期的激活令牌");
            authService.cleanExpiredActivationTokens();
            logger.info("清理过期激活令牌任务完成");
        } catch (Exception e) {
            logger.error("清理过期激活令牌任务失败: {}", e.getMessage());
        }
    }
}
