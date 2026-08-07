package com.novelrealm.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Adresse publique du site, celle que verront les destinataires.
     *
     * <p>Elle était écrite en dur (« http://localhost:5173 ») dans le corps du mail :
     * chaque lecteur inscrit depuis un autre appareil recevait donc un bouton pointant
     * vers <b>sa propre machine</b>. Elle vient maintenant de {@code app.frontend-url},
     * comme la redirection après connexion Google — un seul réglage pour les deux.
     */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendWelcomeEmail(String toEmail, String pseudo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur NovelRealm !");
            
            helper.setText(buildWelcomeHtml(pseudo), true); // true = c'est du HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Échec de l'envoi de l'email de bienvenue", e);
        }
    }

    private String buildWelcomeHtml(String pseudo) {
        return """
                <div style="font-family: Arial, sans-serif; background-color: #0b0e11; padding: 40px; color: #eaecef;">
                  <div style="max-width: 480px; margin: 0 auto; background-color: #1e2329; border-radius: 12px; padding: 32px;">
                    <h1 style="font-size: 24px; margin: 0 0 8px;">
                      Novel<span style="color: #fcd535;">Realm</span>
                    </h1>
                    <h2 style="font-size: 18px; font-weight: 600; margin: 24px 0 12px;">
                      Bienvenue, %s !
                    </h2>
                    <p style="color: #929aa5; line-height: 1.6; margin: 0 0 24px;">
                      Ton compte a bien été créé. Tu peux dès maintenant explorer
                      des centaines de mondes à lire.
                    </p>
                    <a href="%s/login"
                       style="display: inline-block; background-color: #fcd535; color: #181a20;
                              text-decoration: none; font-weight: 600; padding: 12px 24px;
                              border-radius: 8px;">
                      Commencer à lire
                    </a>
                  </div>
                </div>
                """
                // Ordre des %s dans le gabarit : le pseudo, puis l'adresse du site.
                .formatted(pseudo, frontendUrl);
    }
}