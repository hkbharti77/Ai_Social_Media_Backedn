package com.aiplatform.service;

import com.aiplatform.model.PaymentOrder;
import com.aiplatform.model.User;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.awt.Color;
import java.net.HttpURLConnection;

@Service
public class PdfService {

    private final Color PRIMARY_PURPLE = Color.decode("#7c3aed");
    private final Color TEXT_DARK = Color.decode("#1e293b");
    private final Color TEXT_MUTED = Color.decode("#64748b");

    public byte[] generateRoiReport(User user, int postsCount, long hoursSaved, double reachGrowth, String topPostCaption) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, PRIMARY_PURPLE);
            Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TEXT_DARK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11, TEXT_DARK);
            Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_MUTED);

            // 1. Title
            Paragraph title = new Paragraph("Social Media ROI Report", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph subtitle = new Paragraph("Prepared for: " + user.getFullName(), mutedFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(30);
            document.add(subtitle);

            // 2. Metrics Grid (Summary)
            PdfPTable statsTable = new PdfPTable(3);
            statsTable.setWidthPercentage(100);
            statsTable.setSpacingBefore(20);
            statsTable.setSpacingAfter(20);

            addStatCell(statsTable, "Posts Published", String.valueOf(postsCount), "Total this month");
            addStatCell(statsTable, "Time Saved", hoursSaved + " hrs", "AI Automation Effect");
            addStatCell(statsTable, "Reach Growth", String.format("%.1f%%", reachGrowth), "Vs last month");

            document.add(statsTable);

            // 3. Value Breakdown
            document.add(new Paragraph("Efficiency Analysis", subHeaderFont));
            document.add(new Paragraph("\n"));
            
            Paragraph desc = new Paragraph("By using VaniAI Studio, you've automated your content workflow. " +
                    "Based on industry standards (45 mins per post), you've recovered significant business hours.", normalFont);
            desc.setSpacingAfter(15);
            document.add(desc);

            // 4. Top Performing Content
            if (topPostCaption != null && !topPostCaption.isEmpty()) {
                document.add(new Paragraph("Top Performing Post", subHeaderFont));
                PdfPTable topPostTable = new PdfPTable(1);
                topPostTable.setWidthPercentage(100);
                topPostTable.setSpacingBefore(10);
                
                PdfPCell cell = new PdfPCell(new Phrase("\"" + topPostCaption + "\"", italicFont()));
                cell.setPadding(15);
                cell.setBackgroundColor(new Color(248, 250, 252));
                cell.setBorderColor(new Color(226, 232, 240));
                topPostTable.addCell(cell);
                document.add(topPostTable);
            }

            // 5. Future Recommendation
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("AI Recommendation for Next Month", subHeaderFont));
            document.add(new Paragraph("Your engagement peaks on Tuesdays. We recommend increasing video content by 20% to maximize reach.", normalFont));

            document.add(new Paragraph("\n\n\n"));
            LineSeparator ls = new LineSeparator();
            ls.setLineColor(new Color(226, 232, 240));
            document.add(new Chunk(ls));
            
            Paragraph footer = new Paragraph("© 2026 VaniAI Studio - Your Partner in AI Growth", mutedFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private void addStatCell(PdfPTable table, String label, String value, String subLabel) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        
        Paragraph pLabel = new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_MUTED));
        pLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pLabel);
        
        Paragraph pValue = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_PURPLE));
        pValue.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pValue);
        
        Paragraph pSub = new Paragraph(subLabel, FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED));
        pSub.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pSub);
        
        table.addCell(cell);
    }

    private Font italicFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, TEXT_DARK);
    }

    public byte[] generatePaymentReceipt(PaymentOrder order) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font styles
            Font brandFont      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Color.WHITE);
            Font headerFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, PRIMARY_PURPLE);
            Font subHeaderFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXT_MUTED);
            Font normalFont     = FontFactory.getFont(FontFactory.HELVETICA, 11, TEXT_DARK);
            Font boldFont       = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK);
            Font tableHeaderFont= FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            // Safe values — guard against nulls
            String userName   = order.getUser() != null && order.getUser().getFullName() != null
                                ? order.getUser().getFullName() : "VaniAI User";
            String userEmail  = order.getUser() != null && order.getUser().getEmail() != null
                                ? order.getUser().getEmail() : "";
            String orderId    = order.getRazorpayOrderId() != null ? order.getRazorpayOrderId() : "N/A";
            String dateStr    = order.getCompletedAt() != null
                                ? order.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                : order.getCreatedAt() != null
                                    ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    : "N/A";
            long amountInr    = order.getAmount() != null ? order.getAmount() / 100 : 0;

            // Friendly plan name
            String planName;
            if (order.getTargetTier() != null) {
                // Convert SUPER_PRO → Super Pro
                String raw = order.getTargetTier().name().replace("_", " ").toLowerCase();
                StringBuilder sb = new StringBuilder();
                for (String word : raw.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0)))
                          .append(word.substring(1))
                          .append(" ");
                    }
                }
                planName = sb.toString().trim();
            } else if (order.getVideoModelId() != null) {
                planName = "Video Credits (" + order.getVideoModelId() + " x " + order.getVideoCreditsPurchased() + ")";
            } else {
                planName = "Subscription";
            }

            // 1. Header Banner
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase("VANIAI STUDIO", brandFont));
            bannerCell.setBackgroundColor(PRIMARY_PURPLE);
            bannerCell.setPadding(30);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerTable.addCell(bannerCell);
            document.add(headerTable);
            document.add(new Paragraph("\n"));

            // 2. Receipt Info Section
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Phrase("INVOICED TO:", subHeaderFont));
            leftCell.addElement(new Phrase(userName, boldFont));
            leftCell.addElement(new Phrase(userEmail, normalFont));
            infoTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(new Phrase("RECEIPT DETAILS:", subHeaderFont));
            rightCell.addElement(new Phrase("Order ID: " + orderId, normalFont));
            rightCell.addElement(new Phrase("Date: " + dateStr, normalFont));
            infoTable.addCell(rightCell);

            document.add(infoTable);
            document.add(new Paragraph("\n\n"));

            // 3. Main Details Table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1f});

            addTableHeader(table, "Description", tableHeaderFont);
            addTableHeader(table, "Amount", tableHeaderFont);

            addStyledCell(table, planName + " - 1 Month", normalFont, false);
            addStyledCell(table, "\u20B9" + amountInr, normalFont, true);

            addStyledCell(table, "Platform Access & AI Engine", normalFont, false);
            addStyledCell(table, "Included", normalFont, true);

            document.add(table);

            // 4. Total Section
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            totalTable.setWidths(new float[]{3f, 1f});

            addTotalCell(totalTable, "Subtotal", normalFont, false);
            addTotalCell(totalTable, "\u20B9" + amountInr, normalFont, true);

            addTotalCell(totalTable, "GST (18% incl.)", normalFont, false);
            addTotalCell(totalTable, "Included", normalFont, true);

            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL PAID", boldFont));
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setBorderWidth(1f);
            totalLabel.setPadding(10);
            totalTable.addCell(totalLabel);

            PdfPCell totalValue = new PdfPCell(new Phrase("\u20B9" + amountInr, headerFont));
            totalValue.setBorder(Rectangle.TOP);
            totalValue.setBorderWidth(1f);
            totalValue.setPadding(10);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.addCell(totalValue);

            document.add(totalTable);

            // 5. Footer
            document.add(new Paragraph("\n\n\n\n"));
            LineSeparator ls = new LineSeparator();
            ls.setLineColor(new Color(226, 232, 240));
            document.add(new Chunk(ls));

            Paragraph footer = new Paragraph(
                "This is an electronically generated receipt. No physical signature required.", subHeaderFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(15f);
            document.add(footer);

            Paragraph support = new Paragraph("Support: support@vaniai.com | Portal: studio.vaniai.com", normalFont);
            support.setAlignment(Element.ALIGN_CENTER);
            document.add(support);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(PRIMARY_PURPLE);
        cell.setPadding(12);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addStyledCell(PdfPTable table, String text, Font font, boolean alignRight) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(12);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(241, 245, 249));
        if (alignRight) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addTotalCell(PdfPTable table, String text, Font font, boolean alignRight) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(10);
        cell.setBorder(Rectangle.NO_BORDER);
        if (alignRight) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    /**
     * Generates a multi-page PDF where each page contains one image.
     * Used for LinkedIn Document Posts (Carousels).
     */
    public byte[] generateCarouselPdf(List<String> imageUrls) {
        Document document = new Document(PageSize.A4, 0, 0, 0, 0); // No margins for full image display
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            for (String imageUrl : imageUrls) {
                try {
                    // SSRF Protection: Validate URL before fetching
                    validateImageUrl(imageUrl);
                    
                    // 1. Download image
                    Image img = Image.getInstance(new URL(imageUrl));
                    
                    // 2. Scale image to fit page (LinkedIn looks better with squared or A4 ratio)
                    img.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                    
                    // 3. Center image
                    float x = (PageSize.A4.getWidth() - img.getScaledWidth()) / 2;
                    float y = (PageSize.A4.getHeight() - img.getScaledHeight()) / 2;
                    img.setAbsolutePosition(x, y);

                    // 4. Add to document
                    document.add(img);
                    
                    // 5. Create new page for next slide (except for the last slide)
                    document.newPage();
                } catch (Exception e) {
                    // Skip failed images
                    System.err.println("Failed to add image to PDF: " + imageUrl + " - " + e.getMessage());
                }
            }

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    /**
     * SSRF Protection: Validates that the image URL is safe to fetch.
     * Blocks local, private IP ranges and keeps protocols restricted to HTTPS.
     */
    private void validateImageUrl(String imageUrl) throws Exception {
        if (imageUrl == null || !imageUrl.toLowerCase().startsWith("https://")) {
            throw new Exception("Invalid protocol. Only HTTPS is allowed for image fetching.");
        }

        java.net.URL url = new java.net.URL(imageUrl);
        String host = url.getHost();
        java.net.InetAddress address = java.net.InetAddress.getByName(host);

        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            throw new Exception("SSRF Attack Blocked: Attempted to fetch from a private or local IP address (" + host + ").");
        }
        
        // Additional protection: Check against a list of blocked hosts if needed
        if (host.equalsIgnoreCase("localhost") || host.contains("169.254.169.254")) {
            throw new Exception("SSRF Attack Blocked: Restricted host detected.");
        }
    }
}
