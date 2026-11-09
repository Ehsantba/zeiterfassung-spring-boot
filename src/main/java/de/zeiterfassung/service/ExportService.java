package de.zeiterfassung.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.EntryResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TimeEntryRepository entries;
    private final SummaryService summaries;

    public ExportService(TimeEntryRepository entries, SummaryService summaries) {
        this.entries = entries;
        this.summaries = summaries;
    }

    private List<EntryResponse> entriesOf(AppUser user, YearMonth month) {
        return entries.findByUserAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                user, month.atDay(1), month.atEndOfMonth()).stream().map(EntryResponse::of).toList();
    }

    /** CSV mit Semikolon (deutsches Excel) und UTF-8-BOM. */
    public byte[] csv(AppUser user, YearMonth month) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Datum;Beginn;Ende;Pause (Min);Netto (Std);Notiz\r\n");
        for (EntryResponse e : entriesOf(user, month)) {
            sb.append(e.workDate().format(DATE)).append(';')
                    .append(e.startTime()).append(';')
                    .append(e.endTime()).append(';')
                    .append(e.breakMinutes()).append(';')
                    .append(hours(e.netMinutes())).append(';')
                    .append(csvCell(e.note())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Schutz vor CSV-Injection: Zellen, die mit = + - @ beginnen, würden in Excel als Formel
     * ausgeführt. Ein vorangestelltes Apostroph verhindert das.
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    public byte[] pdf(AppUser user, YearMonth month) {
        List<EntryResponse> list = entriesOf(user, month);
        var summary = summaries.month(user, month);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("Monatsübersicht " + String.format("%02d/%d", month.getMonthValue(), month.getYear()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        doc.add(new Paragraph(user.getName() + " (" + user.getEmail() + ")",
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[]{2.2f, 1.4f, 1.4f, 1.4f, 1.6f, 4f});
        table.setWidthPercentage(100);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        for (String h : new String[]{"Datum", "Beginn", "Ende", "Pause", "Netto (Std)", "Notiz"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, head));
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(c);
        }
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (EntryResponse e : list) {
            table.addCell(new Phrase(e.workDate().format(DATE), body));
            table.addCell(new Phrase(e.startTime().toString(), body));
            table.addCell(new Phrase(e.endTime().toString(), body));
            table.addCell(new Phrase(e.breakMinutes() + " min", body));
            table.addCell(new Phrase(hours(e.netMinutes()), body));
            table.addCell(new Phrase(e.note() == null ? "" : e.note(), body));
        }
        doc.add(table);
        doc.add(new Paragraph(" "));
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        doc.add(new Paragraph("Ist: " + hours(summary.workedMinutes()) + " Std   Soll: "
                + hours(summary.targetMinutes()) + " Std   Saldo: " + signedHours(summary.balanceMinutes()) + " Std", bold));
        doc.close();
        return out.toByteArray();
    }

    static String hours(long minutes) {
        return String.format(Locale.GERMANY, "%.2f", minutes / 60.0);
    }

    static String signedHours(long minutes) {
        return (minutes > 0 ? "+" : "") + hours(minutes);
    }
}
