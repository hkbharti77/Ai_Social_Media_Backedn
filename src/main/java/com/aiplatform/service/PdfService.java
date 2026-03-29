package com.aiplatform.service;

import com.aiplatform.model.PaymentOrder;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.awt.Color;

@Service
public class PdfService {

    private final Color PRIMARY_PURPLE = Color.decode("#7c3aed");
    private final Color TEXT_DARK = Color.decode("#1e293b");
    private final Color TEXT_MUTED = Color.decode("#64748b");

    public byte[] generatePaymentReceipt(PaymentOrder order) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font styles
            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Color.WHITE);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, PRIMARY_PURPLE);
            Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXT_MUTED);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11, TEXT_DARK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

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
            
            // Left Side: Invoiced To
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Phrase("INVOICED TO:", subHeaderFont));
            leftCell.addElement(new Phrase(order.getUser().getFullName(), boldFont));
            leftCell.addElement(new Phrase(order.getUser().getEmail(), normalFont));
            infoTable.addCell(leftCell);

            // Right Side: Receipt Details
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(new Phrase("RECEIPT DETAILS:", subHeaderFont));
            rightCell.addElement(new Phrase("Order ID: " + order.getRazorpayOrderId(), normalFont));
            rightCell.addElement(new Phrase("Date: " + order.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), normalFont));
            infoTable.addCell(rightCell);

            document.add(infoTable);
            document.add(new Paragraph("\n\n"));

            // 3. Main Details Table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1f});

            // Table Header
            addTableHeader(table, "Subscription Details", tableHeaderFont);
            addTableHeader(table, "Amount", tableHeaderFont);

            // Table Rows
            addStyledCell(table, order.getTargetTier() + " Plan Upgrade - 1 Month Subscription", normalFont, false);
            addStyledCell(table, "\u20B9" + (order.getAmount() / 100.0), normalFont, true);

            addStyledCell(table, "Platform Access & AI Engine Tokenization", normalFont, false);
            addStyledCell(table, "\u20B9 0.00", normalFont, true);

            document.add(table);

            // 4. Total Section
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            totalTable.setWidths(new float[]{3f, 1f});

            addTotalCell(totalTable, "Subtotal", normalFont, false);
            addTotalCell(totalTable, "\u20B9" + (order.getAmount() / 100.0), normalFont, true);

            addTotalCell(totalTable, "Tax (Included)", normalFont, false);
            addTotalCell(totalTable, "\u20B9 0.00", normalFont, true);

            // Final Total Highlight
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL PAID", boldFont));
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setBorderWidth(1f);
            totalLabel.setPadding(10);
            totalTable.addCell(totalLabel);

            PdfPCell totalValue = new PdfPCell(new Phrase("\u20B9" + (order.getAmount() / 100.0), headerFont));
            totalValue.setBorder(Rectangle.TOP);
            totalValue.setBorderWidth(1f);
            totalValue.setPadding(10);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.addCell(totalValue);

            document.add(totalTable);

            // 5. Footer / Support
            document.add(new Paragraph("\n\n\n\n"));
            LineSeparator ls = new LineSeparator();
            ls.setLineColor(new Color(226, 232, 240));
            document.add(new Chunk(ls));
            
            Paragraph footer = new Paragraph("This is an electronically generated receipt for your VaniAI subscription. No physical signature is required.", subHeaderFont);
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
}
