package com.aiplatform.util;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import java.time.format.DateTimeFormatter;

public class EmailTemplateUtils {

    private static String wrapInBaseTemplate(String tagLine, String content) {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>\n" +
               "<body style=\"margin:0;padding:0;background:#0d1117;font-family:Inter,sans-serif;\">\n" +
               "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
               "<tr><td align=\"center\" style=\"padding:24px 16px;\">\n" +
               "<table width=\"620\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-radius:14px;overflow:hidden;border:1px solid #1e2433;\">\n" +
               "\n" +
               "  <!-- HEADER -->\n" +
               "  <tr>\n" +
               "    <td style=\"background:#0f1729;padding:20px 28px;border-bottom:1px solid #1e2433;\">\n" +
               "      <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
               "        <tr>\n" +
               "          <td style=\"vertical-align:middle;\">\n" +
               "            <table cellpadding=\"0\" cellspacing=\"0\">\n" +
               "              <tr>\n" +
               "                <td style=\"vertical-align:middle;\">\n" +
               "                  <div style=\"width:36px;height:36px;background:#3b82f6;border-radius:8px;text-align:center;line-height:36px;font-size:18px;\">&#127891;</div>\n" +
               "                </td>\n" +
               "                <td style=\"padding-left:10px;vertical-align:middle;\">\n" +
               "                  <span style=\"font-size:17px;font-weight:700;color:#ffffff;\">GyanVani<span style=\"color:#60a5fa;\">Ai</span></span>\n" +
               "                </td>\n" +
               "              </tr>\n" +
               "            </table>\n" +
               "          </td>\n" +
               "          <td style=\"text-align:right;vertical-align:middle;\">\n" +
               "            <span style=\"font-size:11px;color:#4a5568;\">" + tagLine + "</span>\n" +
               "          </td>\n" +
               "        </tr>\n" +
               "      </table>\n" +
               "    </td>\n" +
               "  </tr>\n" +
               "\n" +
               "  <!-- BODY -->\n" +
               "  <tr>\n" +
               "    <td style=\"background:#111827;padding:32px 28px;\">\n" +
               content +
               "    </td>\n" +
               "  </tr>\n" +
               "\n" +
               "  <!-- FOOTER -->\n" +
               "  <tr>\n" +
               "    <td style=\"background:#0b0f1a;padding:20px 28px 16px;\">\n" +
               "      <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
               "        <tr>\n" +
               "          <td><span style=\"font-size:14px;font-weight:700;color:#e2e8f0;\">GyanVani<span style=\"color:#60a5fa;\">Ai</span></span></td>\n" +
               "          <td style=\"text-align:right;\">\n" +
               "            <a href=\"#\" style=\"font-size:11px;color:#4a5568;text-decoration:none;margin-left:12px;\">Privacy Policy</a>\n" +
               "            <a href=\"#\" style=\"font-size:11px;color:#4a5568;text-decoration:none;margin-left:12px;\">Terms</a>\n" +
               "            <a href=\"#\" style=\"font-size:11px;color:#4a5568;text-decoration:none;margin-left:12px;\">Unsubscribe</a>\n" +
               "          </td>\n" +
               "        </tr>\n" +
               "      </table>\n" +
               "      <hr style=\"border:none;border-top:1px solid #1a2035;margin:14px 0;\">\n" +
               "      <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
               "        <tr>\n" +
               "          <td><span style=\"font-size:11px;color:#2d3a50;\">© 2025 GyanVaniAi. All rights reserved.</span></td>\n" +
               "          <td style=\"text-align:center;\"><span style=\"font-size:11px;color:#3d4f6b;\">Support: <a href=\"mailto:hkbharti777@outlook.com\" style=\"color:#3b82f6;font-weight:500;text-decoration:none;\">hkbharti777@outlook.com</a></span></td>\n" +
               "          <td style=\"text-align:right;\"><span style=\"font-size:11px;color:#2d3a50;\">&#9679; New Delhi, India</span></td>\n" +
               "        </tr>\n" +
               "      </table>\n" +
               "    </td>\n" +
               "  </tr>\n" +
               "\n" +
               "</table>\n" +
               "</td></tr>\n" +
               "</table>\n" +
               "</body>\n" +
               "</html>";
    }

    public static String getWelcomeEmailHtml(User user) {
        String name = user.getFullName() != null ? user.getFullName() : "Learner";
        String content = 
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Namaste <strong>" + name + "</strong>,</p>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Welcome to GyanVaniAi! Your AI-powered platform is officially set up and ready to go.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:rgba(30,36,51,0.5);border:1px solid #1e2433;border-radius:8px;margin:20px 0;padding:20px;\">" +
            "  <tr><td>" +
            "    <h4 style=\"margin:0 0 12px 0;color:#3b82f6;font-size:14px;text-transform:uppercase;letter-spacing:1px;\">Account Details:</h4>" +
            "    <p style=\"color:#e2e8f0;font-size:14px;margin:0 0 8px 0;\">&#10145; <strong>" + user.getMonthlyCredits() + "</strong> AI Credits Available</p>" +
            "    <p style=\"color:#e2e8f0;font-size:14px;margin:0 0 8px 0;\">&#10145; Unlimited Access to Learning Modules</p>" +
            "    <p style=\"color:#e2e8f0;font-size:14px;margin:0;\">&#10145; Powerful AI Integrations</p>" +
            "  </td></tr>" +
            "</table>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:24px;\">Ready to jump in and get started?</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">" +
            "  <a href=\"http://localhost:5173\" style=\"display:inline-block;background:#3b82f6;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Launch Platform</a>" +
            "</td></tr></table>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";
        
        return wrapInBaseTemplate("Platform Setup Complete", content);
    }

    public static String getPaymentReceiptHtml(PaymentOrder order) {
        String name = order.getUser().getFullName() != null ? order.getUser().getFullName() : "Valued Customer";
        String date = order.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        
        String content = 
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Namaste <strong>" + name + "</strong>,</p>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Your payment was successful. The <strong>" + order.getTargetTier() + "</strong> plan is now active on your account.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:rgba(30,36,51,0.5);border:1px solid #1e2433;border-radius:8px;margin:20px 0;padding:20px;\">" +
            "  <tr><td>" +
            "    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" +
            "      <tr><td style=\"color:#94a3b8;font-size:14px;padding-bottom:8px;\">Order ID:</td><td align=\"right\" style=\"color:#e2e8f0;font-size:14px;font-weight:600;padding-bottom:8px;\">" + order.getRazorpayOrderId() + "</td></tr>" +
            "      <tr><td style=\"color:#94a3b8;font-size:14px;padding-bottom:8px;\">Payment ID:</td><td align=\"right\" style=\"color:#e2e8f0;font-size:14px;font-weight:600;padding-bottom:8px;\">" + order.getRazorpayPaymentId() + "</td></tr>" +
            "      <tr><td style=\"color:#94a3b8;font-size:14px;padding-bottom:16px;\">Timestamp:</td><td align=\"right\" style=\"color:#e2e8f0;font-size:14px;font-weight:600;padding-bottom:16px;\">" + date + "</td></tr>" +
            "      <tr><td colspan=\"2\" style=\"border-bottom:1px dashed #334155;padding-bottom:16px;\"></td></tr>" +
            "      <tr><td style=\"color:#e2e8f0;font-size:16px;font-weight:700;padding-top:16px;\">TOTAL PAID:</td><td align=\"right\" style=\"color:#3b82f6;font-size:18px;font-weight:700;padding-top:16px;\">&#8377; " + (order.getAmount() / 100.0) + "</td></tr>" +
            "    </table>" +
            "  </td></tr>" +
            "</table>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:24px;\">Your credits have been reset and your daily limits increased for peak throughput.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">" +
            "  <a href=\"http://localhost:5173/settings\" style=\"display:inline-block;background:#3b82f6;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Manage Subscription</a>" +
            "</td></tr></table>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";
        
        return wrapInBaseTemplate("Invoice Generated", content);
    }

    public static String getLowCreditAlertHtml(User user) {
        String name = user.getFullName() != null ? user.getFullName() : "Learner";
        String content = 
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Namaste <strong>" + name + "</strong>,</p>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Your GyanVaniAi credit balance is critically low. To maintain your scheduled generations without interruption, please top up your account.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);border-radius:8px;margin:20px 0;padding:30px;text-align:center;\">" +
            "  <tr><td align=\"center\">" +
            "    <p style=\"color:#ef4444;font-size:12px;text-transform:uppercase;letter-spacing:1px;margin:0;\">Remaining Capacity:</p>" +
            "    <h1 style=\"color:#ef4444;font-size:48px;font-weight:800;margin:10px 0;\">" + user.getMonthlyCredits() + "</h1>" +
            "    <p style=\"color:#ef4444;font-size:12px;font-weight:700;margin:0;\">CREDITS REMAINING</p>" +
            "  </td></tr>" +
            "</table>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:24px;\">Upgrade to the <strong>Pro</strong> plan for expanded access and priority processing.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">" +
            "  <a href=\"http://localhost:5173/pricing\" style=\"display:inline-block;background:#ef4444;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Add Credits</a>" +
            "</td></tr></table>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";
        
        return wrapInBaseTemplate("Capacity Alert", content);
    }

    public static String getPlanExpiredHtml(User user) {
        String name = user.getFullName() != null ? user.getFullName() : "Learner";
        String content = 
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Namaste <strong>" + name + "</strong>,</p>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Your premium tier access has expired and your account has been automatically shifted to the <strong>FREE</strong> tier.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:rgba(30,36,51,0.5);border:1px solid #1e2433;border-radius:8px;margin:20px 0;padding:20px;\">" +
            "  <tr><td>" +
            "    <p style=\"color:#e2e8f0;font-size:14px;margin:0;line-height:1.6;\">To restore your high-limit generating capacity and continue using premium features, please renew your subscription.</p>" +
            "  </td></tr>" +
            "</table>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:24px;\"><tr><td align=\"center\">" +
            "  <a href=\"http://localhost:5173/pricing\" style=\"display:inline-block;background:#3b82f6;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Renew Subscription</a>" +
            "</td></tr></table>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";
        
        return wrapInBaseTemplate("Subscription Expired", content);
    }

    public static String getVerificationEmailHtml(User user, String token) {
        String name = user.getFullName() != null ? user.getFullName() : "Learner";
        String verificationUrl = "http://localhost:8080/api/v1/auth/verify?token=" + token;
        
        String content = 
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Namaste <strong>" + name + "</strong>,</p>" +
            "<p style=\"color:#e2e8f0;font-size:15px;line-height:1.7;margin:0;margin-bottom:16px;\">Thank you for creating an account on GyanVaniAi. Please verify your email address to get access to all features.</p>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:24px;margin-bottom:24px;\"><tr><td align=\"center\">" +
            "  <a href=\"" + verificationUrl + "\" style=\"display:inline-block;background:#3b82f6;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Verify Email Address</a>" +
            "</td></tr></table>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:0;margin-bottom:16px;\">If you didn't create an account, you can safely ignore this email.</p>" +
            "<p style=\"color:#94a3b8;font-size:14px;line-height:1.7;margin:24px 0 0 0;\">Regards,<br>GyanVaniAi Team</p>";
        
        return wrapInBaseTemplate("Account Verification", content);
    }
}
