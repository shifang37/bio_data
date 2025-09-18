package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件发送服务
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired(required = false)
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.username:}")
    private String fromEmail;
    
    @Value("${mail.from.name:Bio Data System}")
    private String fromName;
    
    @Value("${server.domain:http://localhost:8080}")
    private String serverDomain;
    
    /**
     * 发送账户激活邮件
     * @param toEmail 接收邮箱
     * @param username 用户名
     * @param activationToken 激活令牌
     * @return 发送是否成功
     */
    public boolean sendActivationEmail(String toEmail, String username, String activationToken) {
        try {
            String subject = "激活您的Bio Data账户";
            String activationUrl = serverDomain + "/api/auth/activate?token=" + activationToken;
            
            String htmlContent = buildActivationEmailContent(username, activationUrl);
            
            return sendEmail(toEmail, subject, htmlContent);
            
        } catch (Exception e) {
            logger.error("发送激活邮件失败，邮箱: {}, 错误: {}", toEmail, e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送邮件
     * @param toEmail 接收邮箱
     * @param subject 邮件主题
     * @param content 邮件内容（HTML格式）
     * @return 发送是否成功
     */
    private boolean sendEmail(String toEmail, String subject, String content) {
        // 如果没有配置邮件发送器，则模拟发送成功（开发环境）
        if (mailSender == null || fromEmail == null || fromEmail.trim().isEmpty()) {
            logger.warn("邮件发送器未配置，模拟发送邮件成功。收件人: {}, 主题: {}", toEmail, subject);
            logger.info("邮件内容:\n{}", content);
            return true;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true); // true表示HTML格式
            
            mailSender.send(message);
            
            logger.info("邮件发送成功，收件人: {}, 主题: {}", toEmail, subject);
            return true;
            
        } catch (Exception e) {
            logger.error("邮件发送失败，收件人: {}, 主题: {}, 错误: {}", toEmail, subject, e.getMessage());
            return false;
        }
    }
    
    /**
     * 构建激活邮件内容
     */
    private String buildActivationEmailContent(String username, String activationUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>账户激活</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #007bff; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 5px 5px; }
                    .button { display: inline-block; padding: 12px 30px; background: #28a745; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .button:hover { background: #218838; }
                    .warning { color: #dc3545; font-size: 14px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #6c757d; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>欢迎使用 Bio Data System</h1>
                    </div>
                    <div class="content">
                        <p>亲爱的 <strong>%s</strong>，</p>
                        <p>感谢您注册Bio Data System！为了确保账户安全，请点击下方按钮激活您的账户：</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">激活账户</a>
                        </div>
                        
                        <p>如果按钮无法点击，请复制以下链接到浏览器地址栏：</p>
                        <p style="word-break: break-all; background: #e9ecef; padding: 10px; border-radius: 3px;">
                            %s
                        </p>
                        
                        <div class="warning">
                            <p><strong>重要提醒：</strong></p>
                            <ul>
                                <li>此激活链接将在24小时后失效</li>
                                <li>激活链接只能使用一次</li>
                                <li>如果您没有注册此账户，请忽略此邮件</li>
                            </ul>
                        </div>
                    </div>
                    <div class="footer">
                        <p>此邮件由系统自动发送，请勿回复</p>
                        <p>&copy; 2024 Bio Data System. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username, activationUrl, activationUrl);
    }
    
    /**
     * 发送密码重置邮件（预留接口）
     */
    public boolean sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        try {
            String subject = "重置您的Bio Data账户密码";
            String resetUrl = serverDomain + "/api/auth/reset-password?token=" + resetToken;
            
            String htmlContent = buildPasswordResetEmailContent(username, resetUrl);
            
            return sendEmail(toEmail, subject, htmlContent);
            
        } catch (Exception e) {
            logger.error("发送密码重置邮件失败，邮箱: {}, 错误: {}", toEmail, e.getMessage());
            return false;
        }
    }
    
    /**
     * 构建密码重置邮件内容
     */
    private String buildPasswordResetEmailContent(String username, String resetUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>密码重置</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #dc3545; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 5px 5px; }
                    .button { display: inline-block; padding: 12px 30px; background: #dc3545; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .button:hover { background: #c82333; }
                    .warning { color: #dc3545; font-size: 14px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #6c757d; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>密码重置请求</h1>
                    </div>
                    <div class="content">
                        <p>亲爱的 <strong>%s</strong>，</p>
                        <p>我们收到了您的密码重置请求。请点击下方按钮重置您的密码：</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">重置密码</a>
                        </div>
                        
                        <p>如果按钮无法点击，请复制以下链接到浏览器地址栏：</p>
                        <p style="word-break: break-all; background: #e9ecef; padding: 10px; border-radius: 3px;">
                            %s
                        </p>
                        
                        <div class="warning">
                            <p><strong>安全提醒：</strong></p>
                            <ul>
                                <li>此重置链接将在1小时后失效</li>
                                <li>重置链接只能使用一次</li>
                                <li>如果您没有请求重置密码，请忽略此邮件</li>
                            </ul>
                        </div>
                    </div>
                    <div class="footer">
                        <p>此邮件由系统自动发送，请勿回复</p>
                        <p>&copy; 2024 Bio Data System. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username, resetUrl, resetUrl);
    }
}
