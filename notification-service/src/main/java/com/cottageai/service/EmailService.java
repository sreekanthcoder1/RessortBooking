package com.cottageai.service;

import com.cottageai.dto.BookingNotification;
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
    public void sendBookingConfirmationToUser(BookingNotification n) {
        String subject = "✅ Booking Confirmed — " + n.getBookingRef();
        String html = """
            <html><body style="font-family:Arial,sans-serif;padding:20px">
            <div style="max-width:600px;margin:0 auto;background:white;border-radius:12px;padding:40px;border:1px solid #ddd">
              <h1 style="color:#2d6a4f">🏡 CottageAI</h1>
              <h2>Booking Confirmed!</h2>
              <p>Hi <strong>%s</strong>, your booking is confirmed:</p>
              <table style="width:100%%;border-collapse:collapse;margin:20px 0">
                <tr style="background:#f0faf0"><td style="padding:10px;border:1px solid #ddd"><b>Ref</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr><td style="padding:10px;border:1px solid #ddd"><b>Cottage</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr style="background:#f0faf0"><td style="padding:10px;border:1px solid #ddd"><b>Check-in</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr><td style="padding:10px;border:1px solid #ddd"><b>Check-out</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr style="background:#f0faf0"><td style="padding:10px;border:1px solid #ddd"><b>Nights</b></td><td style="padding:10px;border:1px solid #ddd">%d</td></tr>
                <tr><td style="padding:10px;border:1px solid #ddd"><b>Amount</b></td><td style="padding:10px;border:1px solid #ddd;color:#2d6a4f;font-weight:bold">₹%.2f</td></tr>
              </table>
            </div></body></html>
            """.formatted(n.getUserName(), n.getBookingRef(), n.getCottageName(),
                n.getCheckIn(), n.getCheckOut(), n.getTotalNights(), n.getTotalAmount());
        sendEmail(n.getUserEmail(), subject, html);
    }

    @Async
    public void sendBookingNotificationToAdmin(BookingNotification n) {
        if (n.getAdminEmail() == null) return;
        String subject = "📅 New Booking — " + n.getBookingRef();
        String html = """
            <html><body style="font-family:Arial,sans-serif;padding:20px">
            <div style="max-width:600px;margin:0 auto;background:white;border-radius:12px;padding:40px">
              <h1 style="color:#2d6a4f">🏡 CottageAI Admin</h1>
              <h2>New Booking for %s</h2>
              <table style="width:100%%;border-collapse:collapse;margin:20px 0">
                <tr style="background:#f5f5f5"><td style="padding:10px;border:1px solid #ddd"><b>Ref</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr><td style="padding:10px;border:1px solid #ddd"><b>Guest</b></td><td style="padding:10px;border:1px solid #ddd">%s (%s)</td></tr>
                <tr style="background:#f5f5f5"><td style="padding:10px;border:1px solid #ddd"><b>Check-in</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr><td style="padding:10px;border:1px solid #ddd"><b>Check-out</b></td><td style="padding:10px;border:1px solid #ddd">%s</td></tr>
                <tr style="background:#f5f5f5"><td style="padding:10px;border:1px solid #ddd"><b>Revenue</b></td><td style="padding:10px;border:1px solid #ddd;color:#2d6a4f;font-weight:bold">₹%.2f</td></tr>
              </table>
            </div></body></html>
            """.formatted(n.getCottageName(), n.getBookingRef(), n.getUserName(),
                n.getUserEmail(), n.getCheckIn(), n.getCheckOut(), n.getTotalAmount());
        sendEmail(n.getAdminEmail(), subject, html);
    }

    private void sendEmail(String toEmail, String subject, String html) {
        Email from = new Email(fromEmail, "CottageAI");
        Email to   = new Email(toEmail);
        Content content = new Content("text/html", html);
        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(sendGridApiKey);
        Request req = new Request();
        try {
            req.setMethod(Method.POST);
            req.setEndpoint("mail/send");
            req.setBody(mail.build());
            Response resp = sg.api(req);
            log.info("Email sent to {} | Status: {}", toEmail, resp.getStatusCode());
        } catch (IOException e) {
            log.error("Email failed to {}: {}", toEmail, e.getMessage());
        }
    }
}
