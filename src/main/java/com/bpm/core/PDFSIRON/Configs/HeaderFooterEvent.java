package com.bpm.core.PDFSIRON.Configs;
import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.kernel.color.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.border.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Optional;

public class HeaderFooterEvent implements IEventHandler {
    private final Document document;
    private final Image logo;
    private final String title;
    private final String metadata;
    private final JsonNode jsonNode;
    private final String fullName;

    public HeaderFooterEvent(Document document, Image logo, String title, String metadata, JsonNode jsonNode, String fullName) {
        this.document = document;
        this.logo = logo;
        this.title = title;
        this.metadata = metadata;
        this.jsonNode = jsonNode;
        this.fullName = fullName;
    }

    public HeaderFooterEvent(Document document, com.itextpdf.text.Image image, String sironKYC, String metadata, JsonNode jsonNode, String fullName, Document document1, Image logo, String title, String metadata1, JsonNode jsonNode1, String fullName1) {
        this.document = document1;
        this.logo = logo;
        this.title = title;
        this.metadata = metadata1;
        this.jsonNode = jsonNode1;
        this.fullName = fullName1;
    }

    private static Table createHeader(Image image, String title, String metadata, float x, int pageNo, JsonNode jsonNode, String fullName) throws IOException {
        float[] columnWidths = new float[]{50.0F, 60.0F, 50.0F};
        Table table = (new Table(UnitValue.createPercentArray(columnWidths))).useAllAvailableWidth();
        table.setWidth(UnitValue.createPercentValue(100.0F));
        table.setMarginTop(x);
        table.setMarginLeft(25.0F);
        table.setMarginRight(25.0F);
        image.scaleToFit(100.0F, 80.0F);
        image.setHorizontalAlignment(HorizontalAlignment.LEFT);
        PdfFont boldFont = PdfFontFactory.createFont("Times-Roman");
        Paragraph titleParagraph = (Paragraph)((Paragraph)((Paragraph)((Paragraph)(new Paragraph(title)).setFont(boldFont)).setFontSize(12.0F)).setBold()).setTextAlignment(TextAlignment.CENTER);
        PdfFont regularFont = PdfFontFactory.createFont("Times-Roman");
        Paragraph metadataParagraph = (Paragraph)((Paragraph)((Paragraph)((Paragraph)((Paragraph)((Paragraph)(new Paragraph(metadata)).setFont(regularFont)).setFontSize(10.0F)).setBold()).setItalic()).setUnderline()).setTextAlignment(TextAlignment.RIGHT);
        table.addCell((Cell)((Cell)((Cell)(new Cell()).add(image).setBorder((Border)null)).setHorizontalAlignment(HorizontalAlignment.LEFT)).setPaddingTop(15.0F));
        table.addCell((Cell)((Cell)((Cell)(new Cell()).add(titleParagraph).setBorder((Border)null)).setHorizontalAlignment(HorizontalAlignment.CENTER)).setPaddingTop(23.0F));
        table.addCell((Cell)((Cell)((Cell)(new Cell()).add(metadataParagraph).setBorder((Border)null)).setHorizontalAlignment(HorizontalAlignment.RIGHT)).setPaddingTop(20.0F));
        table.addCell((Cell)((Cell)(new Cell(1, 3)).add(createClientInfo(jsonNode)).setBorder((Border)null)).setHorizontalAlignment(HorizontalAlignment.CENTER));
        if (pageNo == 1) {
            table.addCell((Cell)((Cell)(new Cell(1, 3)).add(createPersonDataTable(jsonNode, fullName)).setBorder((Border)null)).setHorizontalAlignment(HorizontalAlignment.CENTER));
        }
        table.setFixedLayout();
        return table;
    }

    private static Table createPersonDataTable(JsonNode jsonNode, String fullName) throws IOException {
        float[] columnWidths = new float[]{100.0F, 100.0F};
        Table table = (new Table(UnitValue.createPercentArray(columnWidths))).useAllAvailableWidth();
        table.setMarginTop(2.0F);
        table.setMarginLeft(20.0F);
        table.setMarginRight(20.0F);
        String[] tableHeader = new String[]{"Field", "Person Data"};
        String[][] tableData = new String[][]{{" Customer no.", extractPersonId(jsonNode)}, {"PEP/SL value", extractPepSLValue(jsonNode)}, {"Relations (own/third-party)", " 0 / 0"}, {"Surname", fullName}, {"Full Name", fullName}, {"Custom attributes", " "}, {"Customer flag 1", "J"} ,
                {"Hits Count" , extractCountWatchlistHits(jsonNode)}};
        PdfFont font = PdfFontFactory.createFont("Times-Roman");
        String[] var7 = tableHeader;
        int var8 = tableHeader.length;

        int var9;
        for(var9 = 0; var9 < var8; ++var9) {
            String header = var7[var9];
            table.addCell((Cell)((Cell)((Cell)((Cell)((Cell)(new Cell()).add(new Paragraph(header)).setFont(font)).setFontSize(11.0F)).setBold()).setBackgroundColor(new DeviceRgb(220, 220, 220))).setTextAlignment(TextAlignment.LEFT));
        }

        boolean isEvenRow = false;
        String[][] var18 = tableData;
        var9 = tableData.length;

        for(int var19 = 0; var19 < var9; ++var19) {
            String[] row = var18[var19];
            String[] var12 = row;
            int var13 = row.length;

            for(int var14 = 0; var14 < var13; ++var14) {
                String cellData = var12[var14];
                Cell cell = (new Cell()).add((IBlockElement)((Paragraph)((Paragraph)(new Paragraph(cellData)).setFont(font)).setFontSize(10.0F)).setTextAlignment(TextAlignment.LEFT));
                if (isEvenRow) {
                    cell.setBackgroundColor(new DeviceRgb(245, 245, 245));
                } else {
                    cell.setBackgroundColor(new DeviceRgb(255, 255, 255));
                }

                table.addCell(cell);
            }

            isEvenRow = !isEvenRow;
        }

        return table;
    }

    private static String extractCountWatchlistHits(JsonNode jsonNode) {
        // Safe traversal to kycScoreResponse.body.return.personResult.personDetailResult.countWatchlistHits
        return Optional.ofNullable(jsonNode)
                .map(node -> node.path("kycScoreResponse").path("body").path("return").path("personResult").path("personDetailResult").path("countWatchlistHits"))
                .map(JsonNode::asText)
                .filter(s -> s != null && !s.isEmpty())
                .orElse("0");
    }
    private static Table createClientInfo(JsonNode jsonNode) throws IOException {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25.0F, 25.0F, 25.0F, 25.0F}));
        table.setWidth(UnitValue.createPercentValue(100.0F));
        table.setMarginTop(5.0F);
        table.setMarginLeft(20.0F);
        table.setMarginRight(20.0F);
        String[] header = new String[]{"Person", "Client", "History", "Status"};
        String[][] data = new String[][]{{extractPersonId(jsonNode), "First Abu Dhabi Bank Misr", "(1/1)(" + (new SimpleDateFormat("dd/MM/yyyy  HH:mm:ss")).format(System.currentTimeMillis()) + ")", extractPstatusDet(jsonNode)}};
        PdfFont font = PdfFontFactory.createFont("Times-Roman");
        String[] var5 = header;
        int var6 = header.length;

        int var7;
        for(var7 = 0; var7 < var6; ++var7) {
            String header1 = var5[var7];
            table.addCell((Cell)((Cell)((Cell)((Cell)((Cell)(new Cell()).add(new Paragraph(header1)).setFont(font)).setFontSize(11.0F)).setBold()).setBackgroundColor(new DeviceRgb(220, 220, 220))).setTextAlignment(TextAlignment.LEFT));
        }

        boolean isEvenRow = false;
        String[][] var16 = data;
        var7 = data.length;

        for(int var17 = 0; var17 < var7; ++var17) {
            String[] row = var16[var17];
            isEvenRow = !isEvenRow;
            String[] var10 = row;
            int var11 = row.length;

            for(int var12 = 0; var12 < var11; ++var12) {
                String cellData = var10[var12];
                Cell cell = (new Cell()).add((IBlockElement)((Paragraph)((Paragraph)(new Paragraph(cellData)).setFont(font)).setFontSize(10.0F)).setTextAlignment(TextAlignment.LEFT));
                if (isEvenRow) {
                    cell.setBackgroundColor(new DeviceRgb(245, 245, 245));
                }

                table.addCell(cell);
            }
        }

        return table;
    }

    private static String pageNumber(PdfDocument pdf) {
        int currentPage = pdf.getPageNumber(pdf.getLastPage());
        return "Page " + currentPage;
    }

    public static String extractPersonId(JsonNode jsonData) {
        return (String)Optional.ofNullable(jsonData).map((node) -> {
            return node.path("kycScoreResponse").path("body").path("return").path("personResult").path("personID");
        }).map(JsonNode::asText).orElse("N/A");
    }

    public static String extractPepSLValue(JsonNode rootNode) {
        JsonNode personResult = (JsonNode)Optional.ofNullable(rootNode).map((node) -> {
            return node.path("kycScoreResponse").path("body").path("return").path("personResult");
        }).orElse((JsonNode)null);
        if (personResult != null) {
            String hitValuePep = personResult.path("hitValuePep").asText("0") + "%";
            String hitValueEmb = personResult.path("hitValueEmb").asText("0") + "%";
            return hitValuePep + " / " + hitValueEmb;
        } else {
            return "0% / 0%";
        }
    }

    public static String extractPstatusDet(JsonNode rootNode) {
        return (String)Optional.ofNullable(rootNode).map((node) -> {
            return node.path("kycScoreResponse").path("body").path("return").path("personResult").path("status");
        }).filter(JsonNode::isTextual).map(JsonNode::asText).map((status) -> {
            return "check".equals(status) ? "New, Undefined" : status;
        }).orElse("N/A");
    }

    public void handleEvent(Event event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent)event;
        PdfDocument pdf = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
        Rectangle pageSize = page.getPageSize();
        Canvas canvas = new Canvas(pdfCanvas, pdf, pageSize);

        try {
            Table headerTable;
            if (pdf.getPageNumber(page) == 1) {
                headerTable = createHeader(this.logo, this.title, this.metadata, 15.0F, pdf.getPageNumber(page), this.jsonNode, this.fullName);
                this.document.setTopMargin(310.0F);
                canvas.add(headerTable);
            } else {
                headerTable = createHeader(this.logo, this.title, this.metadata, 5.0F, 0, this.jsonNode, this.fullName);
                canvas.add(headerTable);
                this.document.setTopMargin(140.0F);
            }

            canvas.add(new Paragraph("\n"));
            pageNumber(pdf);
        } catch (IOException var10) {
            IOException var9 = var10;
            IOException e = var9;
            e.printStackTrace();
        }

        canvas.close();
    }

    public int getCurrentPage(PdfDocumentEvent docEvent) {
        return docEvent.getDocument().getPageNumber(docEvent.getPage());
    }
}