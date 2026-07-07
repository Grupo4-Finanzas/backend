package com.example.finanzas.service;

import com.example.finanzas.dto.api.PaymentScheduleRowApiDto;
import com.example.finanzas.dto.api.SimulationCalculationResponseDto;
import com.example.finanzas.dto.api.SimulationResultsDto;
import com.example.finanzas.entity.Cliente;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final SimulationService simulationService;

    public byte[] buildSimulationPdf(Long id, Cliente cliente) {
        SimulationCalculationResponseDto simulation = simulationService.getById(id, cliente);
        SimulationResultsDto results = simulation.getResults();

        List<String> lines = new ArrayList<>();
        lines.add("Reporte de simulacion de credito vehicular");
        lines.add("Simulacion ID: " + simulation.getId());
        lines.add("Fecha: " + simulation.getCreatedAt());
        lines.add("Moneda: " + results.getCurrency());
        lines.add("Monto financiado: " + money(results.getInitialCapital()));
        lines.add("Plazo meses: " + results.getTermMonths());
        lines.add("Cuota mensual ordinaria: " + money(results.getMonthlyPayment()));
        lines.add("TEM %: " + money(results.getEffectiveRatePercentage()));
        lines.add("TCEA %: " + money(results.getTceaPercentage()));
        lines.add("TIR %: " + money(results.getTirPercentage()));
        lines.add("VAN: " + money(results.getVan()));
        lines.add("Viabilidad: " + results.getViability());
        return buildSimplePdf(lines);
    }

    public byte[] buildSchedulePdf(Long id, Cliente cliente) {
        List<PaymentScheduleRowApiDto> schedule = simulationService.getSchedule(id, cliente);

        List<String> lines = new ArrayList<>();
        lines.add("Cronograma de pagos");
        lines.add("Simulacion ID: " + id);
        lines.add("Periodo | Fecha | Saldo inicial | Interes | Amortizacion | Pago total | Saldo final");
        for (PaymentScheduleRowApiDto row : schedule) {
            lines.add(row.getPeriod()
                    + " | " + row.getPaymentDate()
                    + " | " + money(row.getInitialBalance())
                    + " | " + money(row.getInterest())
                    + " | " + money(row.getAmortization())
                    + " | " + money(row.getTotalPayment())
                    + " | " + money(row.getFinalBalance()));
        }
        return buildSimplePdf(lines);
    }

    public byte[] buildScheduleXlsx(Long id, Cliente cliente) {
        List<PaymentScheduleRowApiDto> schedule = simulationService.getSchedule(id, cliente);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                addZipEntry(zip, "[Content_Types].xml", contentTypes());
                addZipEntry(zip, "_rels/.rels", rels());
                addZipEntry(zip, "xl/workbook.xml", workbook());
                addZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                addZipEntry(zip, "xl/styles.xml", styles());
                addZipEntry(zip, "xl/worksheets/sheet1.xml", sheet(schedule));
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not generate schedule XLSX", ex);
        }
    }

    private byte[] buildSimplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 10 Tf\n50 790 Td\n");
        int written = 0;
        for (String line : lines) {
            if (written >= 52) {
                break;
            }
            content.append("(").append(escapePdf(line)).append(") Tj\n0 -14 Td\n");
            written++;
        }
        content.append("ET\n");

        byte[] stream = content.toString().getBytes(StandardCharsets.UTF_8);
        List<byte[]> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.UTF_8),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.UTF_8),
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"
                        .getBytes(StandardCharsets.UTF_8),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes(StandardCharsets.UTF_8),
                ("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream")
                        .getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        try {
            pdf.write("%PDF-1.4\n".getBytes(StandardCharsets.UTF_8));
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(pdf.size());
                pdf.write(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.UTF_8));
                pdf.write(objects.get(i));
                pdf.write("\nendobj\n".getBytes(StandardCharsets.UTF_8));
            }
            int xref = pdf.size();
            pdf.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(StandardCharsets.UTF_8));
            pdf.write("0000000000 65535 f \n".getBytes(StandardCharsets.UTF_8));
            for (Integer offset : offsets) {
                pdf.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.UTF_8));
            }
            pdf.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                    + xref + "\n%%EOF").getBytes(StandardCharsets.UTF_8));
            return pdf.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not generate PDF", ex);
        }
    }

    private String sheet(List<PaymentScheduleRowApiDto> schedule) {
        StringBuilder rows = new StringBuilder();
        rows.append(row(1, List.of(
                cell("A", 1, "Periodo"),
                cell("B", 1, "Fecha pago"),
                cell("C", 1, "Saldo inicial"),
                cell("D", 1, "Interes"),
                cell("E", 1, "Amortizacion"),
                cell("F", 1, "Seguro"),
                cell("G", 1, "Gastos administrativos"),
                cell("H", 1, "Costos"),
                cell("I", 1, "Pago total"),
                cell("J", 1, "Saldo final"),
                cell("K", 1, "Estado"))));

        int index = 2;
        for (PaymentScheduleRowApiDto item : schedule) {
            rows.append(row(index, List.of(
                    numberCell("A", index, item.getPeriod()),
                    cell("B", index, item.getPaymentDate()),
                    decimalCell("C", index, item.getInitialBalance()),
                    decimalCell("D", index, item.getInterest()),
                    decimalCell("E", index, item.getAmortization()),
                    decimalCell("F", index, item.getInsurance()),
                    decimalCell("G", index, item.getAdministrativeExpenses()),
                    decimalCell("H", index, item.getCosts()),
                    decimalCell("I", index, item.getTotalPayment()),
                    decimalCell("J", index, item.getFinalBalance()),
                    cell("K", index, item.getStatus()))));
            index++;
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + rows + "</sheetData></worksheet>";
    }

    private String row(int index, List<String> cells) {
        return "<row r=\"" + index + "\">" + String.join("", cells) + "</row>";
    }

    private String cell(String column, int row, String value) {
        return "<c r=\"" + column + row + "\" t=\"inlineStr\"><is><t>"
                + escapeXml(value) + "</t></is></c>";
    }

    private String numberCell(String column, int row, Integer value) {
        return "<c r=\"" + column + row + "\"><v>" + value + "</v></c>";
    }

    private String decimalCell(String column, int row, BigDecimal value) {
        return "<c r=\"" + column + row + "\"><v>" + money(value) + "</v></c>";
    }

    private void addZipEntry(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private String rels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Cronograma\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                + "<borders count=\"1\"><border/></borders>"
                + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
                + "<cellXfs count=\"1\"><xf xfId=\"0\"/></cellXfs>"
                + "</styleSheet>";
    }

    private String money(BigDecimal value) {
        return value != null ? value.toPlainString() : "";
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
