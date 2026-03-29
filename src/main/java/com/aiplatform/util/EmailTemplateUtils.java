package com.aiplatform.util;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import java.time.format.DateTimeFormatter;

public class EmailTemplateUtils {

    private static final String PRIMARY_COLOR = "#7c3aed";
    private static final String ACCENT_COLOR = "#f5f3ff";
    private static final String TEXT_MAIN = "#1e293b";
    private static final String TEXT_MUTED = "#64748b";

    private static String wrapInBaseTemplate(String title, String content) {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<style>" +
               "body { font-family: 'Inter', -apple-system, sans-serif; line-height: 1.6; color: " + TEXT_MAIN + "; margin: 0; padding: 0; background-color: #f8fafc; }" +
               ".wrapper { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 20px; overflow: hidden; margin-top: 30px; margin-bottom: 30px; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1); }" +
               ".header { background: " + PRIMARY_COLOR + "; padding: 50px 30px; text-align: center; color: white; }" +
               ".header h1 { margin: 0; font-size: 32px; font-weight: 800; letter-spacing: -1px; text-transform: uppercase; font-style: italic; }" +
               ".content { padding: 40px 35px; }" +
               ".footer { text-align: center; padding: 30px; background: #f1f5f9; color: " + TEXT_MUTED + "; font-size: 13px; }" +
               ".btn { display: inline-block; background: " + PRIMARY_COLOR + "; color: #ffffff !important; padding: 14px 28px; border-radius: 12px; text-decoration: none; font-weight: 700; font-size: 16px; margin: 20px 0; }" +
               ".card { background: " + ACCENT_COLOR + "; border: 1px solid #e9e5ff; border-radius: 16px; padding: 25px; margin: 25px 0; }" +
               ".feature-list { margin: 0; padding: 0; list-style: none; }" +
               ".feature-item { display: flex; align-items: center; margin-bottom: 10px; color: " + TEXT_MAIN + "; font-weight: 500; }" +
               ".feature-icon { color: " + PRIMARY_COLOR + "; margin-right: 10px; font-weight: 900; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class='wrapper'>" +
               "<div class='header'>" +
               "<h1>VaniAI</h1>" +
               "<p style='opacity: 0.9; margin-top: 5px; font-weight: 500;'>" + title + "</p>" +
               "</div>" +
               "<div class='content'>" +
               content +
               "</div>" +
               "<div class='footer'>" +
               "<p>&copy; 2026 VaniAI Intelligence Studio. All rights reserved.</p>" +
               "<p>123 Neural Way, San Francisco, CA</p>" +
               "</div>" +
               "</div>" +
               "</body>" +
               "</html>";
    }

    public static String getWelcomeEmailHtml(User user) {
        String content = "<h2>Welcome to the Studio! \u2728</h2>" +
                "<p>Your laboratory for AI-powered social media growth is officially open. We're thrilled to have you automate and scale your presence with VaniAI.</p>" +
                "<div class='card'>" +
                "<h4 style='margin-top: 0;'>What's inside your lab:</h4>" +
                "<ul class='feature-list'>" +
                "<li class='feature-item'><span class='feature-icon'>\u2713</span> 10 Monthly AI Credits</li>" +
                "<li class='feature-item'><span class='feature-icon'>\u2713</span> Infinite Content Variations</li>" +
                "<li class='feature-item'><span class='feature-icon'>\u2713</span> Visual Brand Consistency</li>" +
                "</ul>" +
                "</div>" +
                "<p>Ready to generate your first viral post?</p>" +
                "<center><a href='http://localhost:5173' class='btn'>Launch Dashboard</a></center>";
        
        return wrapInBaseTemplate("IDENTIFICATION SUCCESSFUL", content);
    }

    public static String getPaymentReceiptHtml(PaymentOrder order) {
        String name = order.getUser().getFullName() != null ? order.getUser().getFullName() : "Valued Customer";
        String date = order.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        
        String content = "<h2>Transaction Authorized, " + name + "! \u2705</h2>" +
                "<p>Your subscription core has been updated. The <strong>" + order.getTargetTier() + "</strong> plan is now active on your account.</p>" +
                "<div class='card'>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Order ID:</span> <span style='font-weight: 700;'>" + order.getRazorpayOrderId() + "</span></div>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Payment ID:</span> <span style='font-weight: 700;'>" + order.getRazorpayPaymentId() + "</span></div>" +
                "<div style='display: flex; justify-content: space-between; margin-bottom: 8px;'><span style='color: " + TEXT_MUTED + ";'>Timestamp:</span> <span style='font-weight: 700;'>" + date + "</span></div>" +
                "<div style='border-top: 1px dashed #cbd5e1; margin-top: 15px; padding-top: 15px; display: flex; justify-content: space-between;'>" +
                "<span style='font-weight: 800; font-size: 18px;'>TOTAL PAID:</span> <span style='font-weight: 800; font-size: 22px; color: " + PRIMARY_COLOR + ";'>\u20B9 " + (order.getAmount() / 100.0) + "</span></div>" +
                "</div>" +
                "<p>Your credits have been reset and your daily limits increased for peak throughput.</p>" +
                "<center><a href='http://localhost:5173/settings' class='btn'>Manage Subscription</a></center>";
        
        return wrapInBaseTemplate("INVOICE GENERATED", content);
    }

    public static String getLowCreditAlertHtml(User user) {
        String content = "<h2>Neural Capacity Alert \u26A0\uFE0F</h2>" +
                "<p>Your VaniAI credit balance is nearly exhausted. To ensure your AI content engine continues running without interruption, please recharge or upgrade your plan.</p>" +
                "<div class='card' style='text-align: center;'>" +
                "<p style='margin: 0; color: " + TEXT_MUTED + ";'>Remaining Credits:</p>" +
                "<h1 style='font-size: 48px; margin: 10px 0; color: #ef4444;'>" + user.getMonthlyCredits() + "</h1>" +
                "</div>" +
                "<p>Upgrading to **Pro** gives you 100+ credits and ultra-hd image exports.</p>" +
                "<center><a href='http://localhost:5173/pricing' class='btn'>Refill Credits</a></center>";
        
        return wrapInBaseTemplate("CRITICAL CAPACITY ALERT", content);
    }

    public static String getPlanExpiredHtml(User user) {
        String content = "<h2>Subscription Expired \u231b</h2>" +
                "<p>Your premium tier access has expired and your account has been automatically shifted to the <strong>FREE</strong> tier.</p>" +
                "<div class='card'>" +
                "<p>To restore your high-limit generating capacity and continue using premium features, please renew your subscription.</p>" +
                "</div>" +
                "<center><a href='http://localhost:5173/pricing' class='btn'>Renew Subscription</a></center>";
        
        return wrapInBaseTemplate("SYSTEM DOWNGRADE NOTICE", content);
    }
}
