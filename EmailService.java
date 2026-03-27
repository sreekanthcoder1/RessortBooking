package com.cottageai.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class EmailService {

    @Value("${app.email.sendgrid-key}")
    private String sendGridApiKey;

    @Value("${app.email.from}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        sendEmail(toEmail, "Your CottageAI Verification Code", buildOtpHtml(otp, "Email Verification", "Use this code to verify your email and complete registration."));
    }

    @Async
    public void sendPasswordResetOtpEmail(String toEmail, String otp) {
        sendEmail(toEmail, "CottageAI Password Reset Code", buildOtpHtml(otp, "Reset Your Password", "Use this code to reset your password. If you didn't request this, ignore this email."));
    }

    @Async
    public void sendSuperAdminSetupOtpEmail(String toEmail, String otp) {
        sendEmail(toEmail, "CottageAI Super Admin Setup Code", buildOtpHtml(otp, "Super Admin Setup", "Use this code to complete your Super Admin account setup."));
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String name) {
        String html = """
            <html><body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:20px">
            <div style="max-width:500px;margin:0 auto;background:white;border-radius:12px;padding:40px">
              <h1 style="color:#2d6a4f;text-align:center">🏡 CottageAI</h1>
              <h2>Welcome, %s! 👋</h2>
              <p style="color:#666">Your account has been created. You can now browse and book cottages.</p>
              <div style="text-align:center;margin:30px 0">
                <a href="http://localhost:3000" style="background:#2d6a4f;color:white;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:bold">Start Exploring →</a>
              </div>
            </div></body></html>""".formatted(name);
        sendEmail(toEmail, "Welcome to CottageAI! 🏡", html);
    }

    @Async
    public void sendPasswordChangedEmail(String toEmail, String name) {
        String html = """
            <html><body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:20px">
            <div style="max-width:500px;margin:0 auto;background:white;border-radius:12px;padding:40px">
              <h1 style="color:#2d6a4f;text-align:center">🏡 CottageAI</h1>
              <h2 style="color:#333">Password Changed ✅</h2>
              <p style="color:#666">Hi %s, your password has been successfully reset.</p>
              <p style="color:#999;font-size:14px">If you did not make this change, please contact support immediately.</p>
            </div></body></html>""".formatted(name);
        sendEmail(toEmail, "Your CottageAI password was changed", html);
    }

    private String buildOtpHtml(String otp, String title, String subtitle) {
        return """
            <html><body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:20px">
            <div style="max-width:500px;margin:0 auto;background:white;border-radius:12px;padding:40px">
              <h1 style="color:#2d6a4f;text-align:center">🏡 CottageAI</h1>
              <h2 style="color:#333;text-align:center">%s</h2>
              <p style="color:#666;text-align:center">%s</p>
              <div style="text-align:center;margin:30px 0">
                <span style="font-size:48px;font-weight:bold;letter-spacing:12px;color:#2d6a4f;background:#f0faf0;padding:20px 30px;border-radius:8px;display:inline-block">%s</span>
              </div>
              <p style="color:#999;text-align:center;font-size:14px">This code expires in 10 minutes. Do not share it with anyone.</p>
            </div></body></html>""".formatted(title, subtitle, otp);
    }

    private void sendEmail(String toEmail, String subject, String htmlBody) {
        Email from = new Email(fromEmail, "CottageAI");
        Email to = new Email(toEmail);
        Mail mail = new Mail(from, subject, to, new Content("text/html", htmlBody));
        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            if (response.getStatusCode() >= 400)
                log.error("SendGrid error: {} - {}", response.getStatusCode(), response.getBody());
            else
                log.info("Email sent to: {} | Status: {}", toEmail, response.getStatusCode());
        } catch (IOException e) {
            log.error("Failed to send email to: {} | {}", toEmail, e.getMessage());
        }
    }
}
