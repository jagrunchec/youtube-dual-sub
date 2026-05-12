package com.dualsub.service;

import com.dualsub.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@dualsub.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Confirmation d'inscription ────────────────────────────────

    public void sendVerificationEmail(User user, String token) {
        if (!mailEnabled) {
            System.out.println("[Mail] DISABLED — verification link: "
                + baseUrl + "/api/auth/verify?token=" + token);
            return;
        }
        String link    = baseUrl + "/api/auth/verify?token=" + token;
        String prenom  = user.getFirstName() != null ? user.getFirstName() : "Utilisateur";
        String subject = "DualSub — Confirmez votre inscription";
        String html    = buildHtml(prenom, subject,
            "Bienvenue sur <strong>DualSub</strong> !<br>"
            + "Cliquez sur le bouton ci-dessous pour activer votre compte.",
            link, "ACTIVER MON COMPTE",
            "Ce lien est valable <strong>24 heures</strong>. "
            + "Si vous n'avez pas créé de compte, ignorez cet email.");

        send(user.getEmail(), subject, html);
    }

    // ── Réinitialisation de mot de passe ──────────────────────────

    public void sendPasswordResetEmail(User user, String token) {
        if (!mailEnabled) {
            System.out.println("[Mail] DISABLED — reset link: "
                + baseUrl + "/?resetToken=" + token);
            return;
        }
        String link    = baseUrl + "/?resetToken=" + token;
        String prenom  = user.getFirstName() != null ? user.getFirstName() : "Utilisateur";
        String subject = "DualSub — Réinitialisation de votre mot de passe";
        String html    = buildHtml(prenom, subject,
            "Vous avez demandé la réinitialisation de votre mot de passe <strong>DualSub</strong>.<br>"
            + "Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe.",
            link, "RÉINITIALISER MON MOT DE PASSE",
            "Ce lien est valable <strong>1 heure</strong>. "
            + "Si vous n'avez pas demandé de réinitialisation, ignorez cet email.");

        send(user.getEmail(), subject, html);
    }

    // ── Envoi bas-niveau ──────────────────────────────────────────

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            System.out.println("[Mail] Sent \"" + subject + "\" → " + to);
        } catch (Exception e) {
            System.err.println("[Mail] Failed to send to " + to + ": " + e.getMessage());
        }
    }

    // ── Template HTML ─────────────────────────────────────────────

    private String buildHtml(String prenom, String title,
                             String bodyText, String link,
                             String btnLabel, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8">
            <title>%s</title></head>
            <body style="margin:0;padding:0;background:#06060e;font-family:Arial,sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0"
                   style="background:#06060e;padding:40px 0;">
              <tr><td align="center">
                <table width="520" cellpadding="0" cellspacing="0"
                       style="background:#090915;border:1px solid rgba(0,212,255,.25);
                              border-radius:6px;overflow:hidden;">
                  <!-- Header -->
                  <tr><td style="background:#090915;padding:28px 32px 16px;
                                 border-bottom:2px solid #00d4ff;text-align:center;">
                    <span style="font-size:26px;font-weight:900;letter-spacing:.1em;
                                 color:#ffffff;">
                      <span style="color:#00ff88;">▶</span>
                      <span style="color:#00d4ff;">DUAL</span>SUB
                    </span>
                  </td></tr>
                  <!-- Body -->
                  <tr><td style="padding:32px;">
                    <p style="color:#00d4ff;font-size:13px;margin:0 0 16px;">
                      Bonjour <strong style="color:#ffffff;">%s</strong>,
                    </p>
                    <p style="color:#c0c8e8;font-size:14px;line-height:1.6;margin:0 0 28px;">
                      %s
                    </p>
                    <table cellpadding="0" cellspacing="0" style="margin:0 auto 28px;">
                      <tr><td align="center"
                              style="background:#00d4ff;border-radius:4px;
                                     padding:14px 32px;">
                        <a href="%s"
                           style="color:#06060e;font-size:13px;font-weight:700;
                                  letter-spacing:.1em;text-decoration:none;">
                          %s
                        </a>
                      </td></tr>
                    </table>
                    <p style="color:rgba(255,255,255,.35);font-size:11px;
                               line-height:1.5;margin:0;border-top:1px solid rgba(255,255,255,.08);
                               padding-top:16px;">
                      %s
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """.formatted(title, prenom, bodyText, link, btnLabel, footer);
    }
}
