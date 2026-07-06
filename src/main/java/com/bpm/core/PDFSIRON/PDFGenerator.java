package com.bpm.core.PDFSIRON;

import com.bpm.core.PDFSIRON.Configs.HeaderFooterEvent;
import com.bpm.core.PDFSIRON.Configs.PageNumberEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.color.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.border.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.BaseDirection;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.text.pdf.languages.ArabicLigaturizer;

import java.io.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Generates SironKYC screening-hit PDF reports from the KYC score JSON response.
 *
 * Structure:
 *  1. Constants & cached resources
 *  2. Public API
 *  3. Core document generation
 *  4. Match content (title / header / detail sections)
 *  5. Detail sub-processors (IDs, names, birth dates, nationalities, addresses)
 *  6. End content (history, attachments, end note)
 *  7. Row / cell builders
 *  8. Text, date & JSON helpers
 *  9. Country code lookup
 */
public class PDFGenerator {

    // =====================================================================
    // 1. Constants & cached resources
    // =====================================================================

    private static final Logger LOGGER = Logger.getLogger(PDFGenerator.class.getName());

    private static final DeviceRgb HEADER_BG = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb CELL_BG   = new DeviceRgb(240, 240, 240);

    private static final float[] COL_WIDTHS = {25.0F, 25.0F, 25.0F, 25.0F};
    private static final float FONT_SIZE = 9.0F;

    /** Must match the actual font file bundled in resources. */
    private static final String FONT_PATH = "arial.ttf";
    private static final String BANK_LOGO = "BankLogo_FAB.png";

    /** Font bytes are loaded from the classpath once and reused for every document. */
    private static volatile byte[] fontBytesCache;

    // SimpleDateFormat is NOT thread-safe -> create per use via these factories.
    private static SimpleDateFormat displayDateFormat() { return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"); }
    private static SimpleDateFormat fileDateFormat()    { return new SimpleDateFormat("MMddyyyyHHmmss"); }

    // =====================================================================
    // 2. Public API
    // =====================================================================

    public static Map<String, String> generatePDFAsBase64(String fullName, String jsonContent) {
        Map<String, String> result = new HashMap<>(2);
        try {
            PDFResult pdfResult = generatePDF(fullName, jsonContent);
            result.put("fileName", pdfResult.getFileName());
            result.put("base64Content", Base64.getEncoder().encodeToString(pdfResult.getPdfBytes()));
        } catch (Exception e) {
            LOGGER.severe("Error generating PDF: " + e.getMessage());
            result.put("fileName", "Error generating PDF: " + e.getMessage());
            result.put("base64Content", null);
        }
        return result;
    }

    public static PDFResult generatePDF(String fullName, String jsonContent) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // One font per document; the same (Arabic-capable) font is used as default.
            PdfFont defaultFont = createDocumentFont();
            PdfFont boldFont = createDocumentFont(); // replace with a bold variant if one is added to resources

            JsonNode jsonNode = new ObjectMapper().readTree(jsonContent);

            PdfWriter writer = new PdfWriter(outputStream);
            writer.setCompressionLevel(9);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(360.0F, 10.0F, 60.0F, 10.0F);

            registerPageEventHandlers(pdf, document, jsonNode, fullName);

            processMatches(document, jsonNode, defaultFont, boldFont);
            String fileName = addEndContent(document, jsonNode);

            document.close();
            return new PDFResult(fileName, outputStream.toByteArray());
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing JSON: " + e.getMessage(), e);
        }
    }

    public static void decodeBase64(String base64) {
        try {
            savePdfToFile(Base64.getDecoder().decode(base64), "output.pdf");
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.severe("Error decoding Base64: " + e.getMessage());
        }
    }

    public static void savePdfToFile(byte[] pdfBytes, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(pdfBytes);
        }
    }

    public static String readJsonFromFile(String filePath) throws IOException {
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }
        return jsonContent.toString();
    }

    public static String extractPersonId(JsonNode jsonData) {
        return Optional.ofNullable(jsonData)
                .map(node -> node.path("kycScoreResponse").path("body").path("return")
                        .path("personResult").path("personID"))
                .filter(node -> !node.isMissingNode() && !node.isNull())
                .map(JsonNode::asText)
                .orElse("N/A");
    }

    public static String[] customizeKeysAndValue(String key, String value) {
        switch (key) {
            case "type":         key = "Type";       break;
            case "comment":      key = "Comment";    break;
            case "slLegalBasis": key = "Regulation"; break;
        }
        switch (value) {
            case "P": value = "Individual"; break;
            case "E": value = "Entity";     break;
            case "C": value = "Company";    break;
            case "V": value = "Vessel";     break;
            default:
                if (value.isEmpty() || value.equals("null")) value = " ";
        }
        return new String[]{key, value};
    }

    // =====================================================================
    // 3. Core document generation
    // =====================================================================

    private static void registerPageEventHandlers(PdfDocument pdf, Document document,
                                                  JsonNode jsonNode, String fullName) throws IOException {
        byte[] imageBytes = loadResourceBytes(BANK_LOGO);
        if (imageBytes == null) {
            LOGGER.warning("Bank logo '" + BANK_LOGO + "' not found in resources; header/footer skipped.");
            return;
        }
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData);
        String metadata = "sironKYC 22.1 (220801)\nGenerated on: " + displayDateFormat().format(new Date());
        pdf.addEventHandler("StartPdfPage",
                new HeaderFooterEvent(document, image, "SironKYC", metadata, jsonNode, fullName));
        pdf.addEventHandler("EndPdfPage", new PageNumberEventHandler(document));
    }

    private static void processMatches(Document document, JsonNode jsonNode,
                                       PdfFont defaultFont, PdfFont boldFont)
            throws IOException, ParseException {
        JsonNode listMatches = jsonNode.path("kycScoreResponse").path("body").path("return")
                .path("personResult").path("personDetailResult").path("listMatches");
        if (!listMatches.isArray() || listMatches.isEmpty()) {
            document.add(new Paragraph("No match data found.")
                    .setFont(defaultFont)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(320.0F));
            return;
        }
        for (JsonNode match : listMatches) {
            document.add(createContentTable(match, defaultFont, boldFont));
        }
    }

    private static Table createContentTable(JsonNode match, PdfFont defaultFont, PdfFont boldFont)
            throws IOException, ParseException {
        Table table = new Table(UnitValue.createPercentArray(COL_WIDTHS))
                .useAllAvailableWidth()
                .setMargins(10.0F, 10.0F, 10.0F, 10.0F);
        addTitleRow(table, match, boldFont);
        addHeaderRow(table, match, defaultFont, boldFont);
        addDataRows(table, match, defaultFont, boldFont);
        return table;
    }

    // =====================================================================
    // 4. Match content (title / header sections)
    // =====================================================================

    private static void addTitleRow(Table table, JsonNode match, PdfFont boldFont) throws ParseException {
        table.addCell(new Cell(1, 4).setBorder(null)
                .add(new Paragraph(getAlertTitle(match)).setFont(boldFont)).setFontSize(FONT_SIZE)
                .setTextAlignment(TextAlignment.LEFT).setBackgroundColor(CELL_BG).setPadding(5.0F));
    }

    private static void addHeaderRow(Table table, JsonNode match, PdfFont defaultFont, PdfFont boldFont)
            throws IOException {
        table.addCell(createHeaderCell("Key", boldFont));
        table.addCell(createHeaderCell("Value", boldFont));

        addFieldIfPresent(table, "Type", match.path("type"), defaultFont);
        addFieldIfPresent(table, "Regulation", match.path("watchListData").path("slLegalBasis"), defaultFont);
        addFieldIfPresent(table, "Comment", match.path("watchListData").path("slRemark"), defaultFont);

        processIdentificationDocuments(table, match, defaultFont);
        processNames(table, match, defaultFont);
        processBirthDates(table, match, defaultFont);
        processNationalities(table, match, defaultFont);
        processAddresses(table, match, defaultFont);

        table.addCell(new Cell(1, 4).add(new Paragraph("Alert Words").setFont(boldFont))
                .setFontSize(FONT_SIZE).setPaddingLeft(10.0F).setBackgroundColor(HEADER_BG).setBold());
    }

    private static void addDataRows(Table table, JsonNode match, PdfFont defaultFont, PdfFont boldFont) {
        JsonNode hits = match.path("hits");
        addRow(table, "Field", "Person data", "List data", "Hit value", true, defaultFont, boldFont);
        if (hits.isArray() && !hits.isEmpty()) {
            for (JsonNode hit : hits) {
                addRow(table.setBackgroundColor(DeviceRgb.WHITE), "Full Name",
                        hit.path("searchedWord").asText("N/A"),
                        hit.path("matchedWord").asText("N/A"),
                        hit.path("score").asText("N/A"),
                        false, defaultFont, boldFont);
            }
        } else {
            addRow(table, "No hits found", "", "", "", false, defaultFont, boldFont);
            LOGGER.info("No hits array found in listMatches or it is empty.");
        }
    }

    private static String getAlertTitle(JsonNode match) {
        try {
            if (match.isMissingNode()) return "No Match Found";
            StringBuilder title = new StringBuilder();
            if (match.has("slListName") && isValidValue(match.get("slListName").asText())) {
                title.append(match.get("slListName").asText());
            }
            if (match.has("slId") && isValidValue(match.get("slId").asText())) {
                title.append(" ").append(match.get("slId").asText()).append(" ");
            }
            if (match.has("score") && match.has("Hit_Type") && isValidValue(match.get("Hit_Type").asText())) {
                title.append(" , Score: (").append(match.get("score").asText()).append("%)");
            }
            if (match.has("Hit_date")) {
                String hitDate = match.get("Hit_date").asText();
                if (isValidValue(hitDate)) {
                    Date date = new SimpleDateFormat("yyyyMMddHHmmssSSS").parse(hitDate);
                    title.append("  Generated (")
                            .append(new SimpleDateFormat("MM/dd/yyyy, hh:mm a").format(date))
                            .append(")");
                }
            }
            return title.toString();
        } catch (Exception e) {
            LOGGER.severe("Error generating alert title: " + e.getMessage());
            return "Error generating alert title";
        }
    }

    // =====================================================================
    // 5. Detail sub-processors
    // =====================================================================

    private static void processIdentificationDocuments(Table table, JsonNode match, PdfFont defaultFont)
            throws IOException {
        JsonNode slPassNos = match.path("watchListData").path("slPassNos");
        if (!slPassNos.isArray() || slPassNos.isEmpty()) return;

        int index = 1;
        for (JsonNode slPassNo : slPassNos) {
            String passNo  = getValidText(slPassNo.path("passNo"));
            String country = getValidText(slPassNo.path("country"));
            String type    = getValidText(slPassNo.path("type"));
            if (passNo.isEmpty() && country.isEmpty() && type.isEmpty()) continue;

            addKeyValueRow(table.setBackgroundColor(CELL_BG), "ID card (" + index + ")", new TextNode(""), defaultFont);
            if (!passNo.isEmpty()) {
                addKeyValueRow(table, "ID card no", new TextNode(passNo), defaultFont);
            }
            String originalIdNo = getValidText(slPassNo.path("SL_NUMBER_ORIGINAL"));
            if (!originalIdNo.isEmpty()) {
                addKeyValueRow(table, "Original ID no.", new TextNode(originalIdNo), defaultFont);
            }
            if (!type.isEmpty()) {
                addKeyValueRow(table, "Type", new TextNode(type), defaultFont);
            }
            if (!country.isEmpty()) {
                addKeyValueRow(table, "Country", new TextNode(formatCountry(country)), defaultFont);
            }
            index++;
        }
    }

    private static void processNames(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slNames = match.path("watchListData").path("slNames");
        if (!slNames.isArray() || slNames.isEmpty()) return;

        int index = 1;
        for (JsonNode slName : slNames) {
            String firstName  = getValidText(slName.path("firstName"));
            String lastName   = getValidText(slName.path("lastName"));
            String middleName = getValidText(slName.path("middleName"));
            String wholeName  = getValidText(slName.path("wholeName"));
            String gender     = getValidText(slName.path("gender"));
            String title      = getValidText(slName.path("title"));

            if (firstName.isEmpty() && lastName.isEmpty() && middleName.isEmpty()
                    && wholeName.isEmpty() && gender.isEmpty() && title.isEmpty()) {
                continue;
            }

            addKeyValueRow(table, "Name (" + index + ")", new TextNode(""), defaultFont);

            if (!lastName.isEmpty())   addKeyValueRow(table, "Surname", new TextNode(lastName), defaultFont);
            if (!firstName.isEmpty())  addKeyValueRow(table, "Forename", new TextNode(firstName), defaultFont);
            if (!middleName.isEmpty()) addKeyValueRow(table, "Middle Name", new TextNode(middleName), defaultFont);

            // Type / Full Name rules:
            //  - first + last present            -> PRIMARY NAME (wholeName shown as extra "Full Name" row)
            //  - only wholeName present          -> Full Name + ALIASES
            //  - anything else                   -> PRIMARY NAME (wholeName shown if present)
            if (firstName.isEmpty() && lastName.isEmpty() && !wholeName.isEmpty()) {
                addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
                addKeyValueRow(table, "Type", new TextNode("ALIASES"), defaultFont);
            } else {
                addKeyValueRow(table, "Type", new TextNode("PRIMARY NAME"), defaultFont);
                if (!wholeName.isEmpty()) {
                    addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
                }
            }

            if (!gender.isEmpty()) {
                String genderText = gender.equalsIgnoreCase("M") ? "M: MALE"
                        : gender.equalsIgnoreCase("F") ? "F: FEMALE"
                        : gender;
                addKeyValueRow(table, "Gender", new TextNode(genderText), defaultFont);
            }
            if (!title.isEmpty()) {
                addKeyValueRow(table, "Title", new TextNode(title), defaultFont);
            }
            index++;
        }
    }

    private static void processBirthDates(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slBirthDates = match.path("watchListData").path("slBirths");
        if (!slBirthDates.isArray() || slBirthDates.isEmpty()) return;

        int index = 1;
        for (JsonNode slBirthDate : slBirthDates) {
            String rawBirthDate = getValidText(slBirthDate.path("birthDate"));
            String slDateLong   = getValidText(slBirthDate.path("slDateLong"));
            String birthPlace   = getValidText(slBirthDate.path("place"));
            String country      = getValidText(slBirthDate.path("country"));

            if (rawBirthDate.isEmpty() && slDateLong.isEmpty() && birthPlace.isEmpty() && country.isEmpty()) {
                continue;
            }

            addKeyValueRow(table.setBackgroundColor(CELL_BG), "Birth Date (" + index + ")",
                    new TextNode(""), defaultFont);

            // --- Date: "birthDate (slDateLong)" e.g. 12/12/1975 (12/12/1957) ---
            String compact = (!rawBirthDate.isEmpty() && !slBirthDate.path("birthDate").has("nil"))
                    ? formatCompactDate(rawBirthDate) : "";
            String longDate = (!slDateLong.isEmpty() && !slBirthDate.path("slDateLong").has("nil"))
                    ? formatSlDateLong(slDateLong) : "";

            String dateDisplay;
            if (!compact.isEmpty() && !longDate.isEmpty()) {
                dateDisplay = compact + " (" + longDate + ")";
            } else if (!compact.isEmpty()) {
                dateDisplay = compact;
            } else {
                dateDisplay = longDate; // may be empty; row skipped below
            }

            if (!dateDisplay.isEmpty()) {
                addKeyValueRow(table, "Date", new TextNode(dateDisplay), defaultFont);
            }

            // --- Location ---
            if (!birthPlace.isEmpty()) {
                addKeyValueRow(table, "Location", new TextNode(birthPlace), defaultFont);
            }

            // --- Country ---
            if (!country.isEmpty()) {
                addKeyValueRow(table, "Country",
                        new TextNode(country + " : " + extractCountryName(country)), defaultFont);
            }

            index++;
        }
    }
    private static void processNationalities(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slCitizs = match.path("watchListData").path("slCitizs");
        if (!slCitizs.isArray() || slCitizs.isEmpty()) return;

        for (JsonNode slCitiz : slCitizs) {
            String countryCode = getValidText(slCitiz.path("country"));
            if (countryCode.isEmpty() && slCitiz.path("country").has("nil")) continue;
            String countryName = countryCode + ":" + extractCountryName(countryCode);
            if (!countryName.isEmpty()) {
                addKeyValueRow(table.setBackgroundColor(CELL_BG), "Nationality", new TextNode(countryName), defaultFont);
            }
        }
    }

    private static void processAddresses(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slAddresses = match.path("watchListData").path("slAddresses");
        if (!slAddresses.isArray() || slAddresses.isEmpty()) return;

        int index = 1;
        for (JsonNode slAddress : slAddresses) {
            String address      = getValidText(slAddress.path("address"));
            String city         = getValidText(slAddress.path("city"));
            String country      = getValidText(slAddress.path("country"));
            String state        = getValidText(slAddress.path("state"));
            String street       = getValidText(slAddress.path("street"));
            String streetNumber = getValidText(slAddress.path("streetNumber"));
            String zip          = getValidText(slAddress.path("zip"));

            if (isAllFieldsEmpty(address, city, country, state, street, streetNumber, zip)) continue;

            addKeyValueRow(table.setBackgroundColor(HEADER_BG), "Address (" + index + ")", new TextNode(""), defaultFont);
            addFieldIfPresent(table, "ZIP/city", city, slAddress.path("city"), defaultFont);
            addFieldIfPresent(table, "Country", formatCountry(country), slAddress.path("country"), defaultFont);
            addFieldIfPresent(table, "Street", street, slAddress.path("street"), defaultFont);
            addFieldIfPresent(table, "State", state, slAddress.path("state"), defaultFont);
            addFieldIfPresent(table, "Street Number", streetNumber, slAddress.path("streetNumber"), defaultFont);
            addFieldIfPresent(table, "Zip", zip, slAddress.path("zip"), defaultFont);
            index++;
        }
    }

    // =====================================================================
    // 6. End content (history, attachments, end note)
    // =====================================================================

    private static String addEndContent(Document document, JsonNode jsonNode) throws IOException {
        String dateTime = displayDateFormat().format(new Date());
        PdfFont timesRoman = PdfFontFactory.createFont("Times-Roman", "Cp1252", true);
        addHistorySection(document, jsonNode, timesRoman, dateTime);
        String fileName = addAttachmentsSection(document, jsonNode, timesRoman, dateTime);
        addEndNote(document, timesRoman);
        return fileName;
    }

    private static void addHistorySection(Document document, JsonNode jsonNode, PdfFont font, String dateTime) {
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{21.0f, 1.0f})).useAllAvailableWidth();
        headerTable.addCell(borderedCell("History", font));
        headerTable.setMarginLeft(25.0f).setMarginTop(10.0f);
        document.add(headerTable);

        Table historyTable = new Table(UnitValue.createPercentArray(new float[]{1.0f, 2.0f})).useAllAvailableWidth();
        historyTable.setMarginLeft(25.0f).setMarginRight(25.0f).setMarginTop(5.0f);
        addHistoryRow(historyTable, "Status", "New, unedited", font);
        addHistoryRow(historyTable, "Date/time", dateTime, font);
        addHistoryRow(historyTable, "Resubmission", "--- / --- (Manually / Automatic)", font);
        addHistoryRow(historyTable, "Alert", "", font);
        addHistoryRow(historyTable, "Comment", "", font);
        addHistoryRow(historyTable, "Risk", jsonNode != null ? extractRisk(jsonNode) : "N/A", font);
        addHistoryRow(historyTable, "Hit type", "E: SL", font);
        document.add(historyTable);
    }

    private static String addAttachmentsSection(Document document, JsonNode jsonNode, PdfFont font, String dateTime) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{2.0f, 1.0f, 1.0f})).useAllAvailableWidth();
        header.addCell(borderedCell("Attachments", font));
        header.addCell(borderedCell("Status", font));
        header.addCell(borderedCell("Version", font));
        header.setMarginLeft(25.0f).setMarginRight(25.0f).setMarginTop(10.0f);
        document.add(header);

        String fileName = buildAttachmentFileName(jsonNode);

        Table body = new Table(UnitValue.createPercentArray(new float[]{2.0f, 1.0f, 1.0f})).useAllAvailableWidth();
        body.setMarginLeft(25.0f).setMarginRight(25.0f);
        body.addCell(plainBorderedCell(fileName, font));
        body.addCell(plainBorderedCell("New, unedited", font));
        body.addCell(plainBorderedCell(dateTime, font));
        document.add(body);
        return fileName;
    }

    private static String buildAttachmentFileName(JsonNode jsonNode) {
        int randomNumber = (int) (Math.random() * 9.99999999E8) + 1;
        String formattedNumber = new DecimalFormat("000000000").format(randomNumber);
        String personId = jsonNode != null ? extractPersonId(jsonNode) : "N/A";
        return personId + "_" + formattedNumber + "@" + fileDateFormat().format(new Date()) + ".pdf";
    }

    private static void addEndNote(Document document, PdfFont font) {
        document.add(new Paragraph("End of document - Number of pages: ** "
                + document.getPdfDocument().getNumberOfPages() + " **(including this page)")
                .setFont(font)
                .setFontSize(FONT_SIZE)
                .setUnderline()
                .setBackgroundColor(CELL_BG)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(50.0f)
                .setMarginTop(20.0f));
    }

    // =====================================================================
    // 7. Row / cell builders
    // =====================================================================

    private static Cell createHeaderCell(String text, PdfFont boldFont) {
        return new Cell(1, 2).add(new Paragraph(text).setFont(boldFont)).setFontSize(FONT_SIZE)
                .setTextAlignment(TextAlignment.LEFT).setBackgroundColor(HEADER_BG).setPadding(5.0F);
    }

    private static Cell borderedCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font)).setFontSize(FONT_SIZE)
                .setBackgroundColor(CELL_BG).setBorder(new SolidBorder(0.5f));
    }

    private static Cell plainBorderedCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font)).setFontSize(FONT_SIZE)
                .setBorder(new SolidBorder(0.5f));
    }

    private static void addHistoryRow(Table table, String label, String value, PdfFont font) {
        table.addCell(plainBorderedCell(label, font));
        table.addCell(plainBorderedCell(value, font));
    }

    private static void addFieldIfPresent(Table table, String key, JsonNode node, PdfFont defaultFont)
            throws IOException {
        String value = node.asText("").trim();
        if (!value.isEmpty()) {
            addKeyValueRow(table, key, new TextNode(value), defaultFont);
        }
    }

    private static void addFieldIfPresent(Table table, String key, String value, JsonNode node, PdfFont defaultFont)
            throws IOException {
        if (!value.isEmpty() && !node.has("nil")) {
            addKeyValueRow(table, key, new TextNode(value), defaultFont);
        }
    }

    private static void addKeyValueRow(Table table, String key, JsonNode valueNode, PdfFont defaultFont)
            throws IOException {
        String value = valueNode.asText().trim();
        String[] customized = customizeKeysAndValue(key, value);
        String customizedKey   = notBlankOrSpace(customized[0]);
        String customizedValue = notBlankOrSpace(customized[1]);

        Cell keyCell = new Cell(1, 2)
                .add(new Paragraph(customizedKey).setFont(defaultFont))
                .setFontSize(FONT_SIZE)
                .setTextAlignment(TextAlignment.LEFT)
                .setPaddingLeft(15.0F)
                .setBackgroundColor(DeviceRgb.WHITE);

        Cell valueCell = containsArabic(customizedValue)
                ? buildArabicValueCell(customizedValue, defaultFont)
                : new Cell(1, 2)
                .add(new Paragraph(customizedValue).setFont(defaultFont))
                .setFontSize(FONT_SIZE)
                .setTextAlignment(TextAlignment.LEFT)
                .setPaddingLeft(10.0F)
                .setBackgroundColor(DeviceRgb.WHITE);

        // Section header rows ("Name (1)", "Address (2)", "Nationality"...) get a grey background.
        if (customizedKey.contains("(") || customizedKey.contains("Nationality")) {
            keyCell.setPaddingLeft(5.0F).setBackgroundColor(HEADER_BG);
            valueCell.setBackgroundColor(HEADER_BG);
        }

        table.addCell(keyCell);
        table.addCell(valueCell);
        table.setFixedLayout();
    }

    private static Cell buildArabicValueCell(String value, PdfFont arabicFont) {
        List<String> lines = splitTextIntoLines(value, 70);
        Paragraph arabicParagraph = new Paragraph()
                .setFont(arabicFont)
                .setFontSize(FONT_SIZE)
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT);
        ArabicLigaturizer arabicProcessor = new ArabicLigaturizer();
        for (int i = 0; i < lines.size(); i++) {
            arabicParagraph.add(arabicProcessor.process(lines.get(i)));
            if (i < lines.size() - 1) {
                arabicParagraph.add("\n");
            }
        }
        return new Cell(1, 2)
                .add(arabicParagraph)
                .setPaddingRight(10.0F)
                .setBackgroundColor(DeviceRgb.WHITE);
    }

    private static void addRow(Table table, String field, String personData, String listData, String hitValue,
                               boolean isHeader, PdfFont defaultFont, PdfFont boldFont) {
        PdfFont font = isHeader ? boldFont : defaultFont;
        table.setMarginRight(25.0F).setMarginLeft(25.0F);
        for (String text : new String[]{field, personData, listData, hitValue}) {
            table.addCell(new Cell().add(new Paragraph(text).setFont(font)).setFontSize(FONT_SIZE)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setBackgroundColor(isHeader ? CELL_BG : null));
        }
        table.setFixedLayout();
    }

    // =====================================================================
    // 8. Text, date & JSON helpers
    // =====================================================================

    /** Formats yyyyMMdd -> MM/dd/yyyy (00 month & day rendered as 00/00/yyyy). */
    private static String formatCompactDate(String raw) {
        if (raw == null || raw.length() != 8) return raw;
        String year = raw.substring(0, 4), month = raw.substring(4, 6), day = raw.substring(6, 8);
        return (month.equals("00") && day.equals("00")) ? "00/00/" + year : month + "/" + day + "/" + year;
    }

    /** Formats slDateLong (yyyy/MM/dd, e.g. 1957/07/30) to match the birthDate display format. */
    private static String formatSlDateLong(String raw) {
        try {
            String[] parts = raw.split("/");
            if (parts.length == 3) {
                String year = parts[0], month = parts[1], day = parts[2];
                return (month.equals("00") && day.equals("00")) ? "00/00/" + year : month + "/" + day + "/" + year;
            }
            if (raw.length() == 8) {
                return formatCompactDate(raw); // fallback: raw yyyyMMdd without separators
            }
            return raw;
        } catch (Exception e) {
            LOGGER.warning("Failed to format slDateLong: " + raw);
            return raw;
        }
    }

    private static String getValidText(JsonNode node) {
        return !node.has("nil") && !node.asText().trim().isEmpty() ? node.asText().trim() : "";
    }

    private static boolean isValidValue(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("null") && !value.equals("nil");
    }

    private static String notBlankOrSpace(String value) {
        return (value != null && !value.isEmpty()) ? value : " ";
    }

    private static boolean isAllFieldsEmpty(String... fields) {
        for (String field : fields) {
            if (!field.isEmpty()) return false;
        }
        return true;
    }

    private static boolean containsArabic(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.ARABIC) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static List<String> splitTextIntoLines(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split(" ")) {
            if (currentLine.length() + word.length() + 1 <= maxChars) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    private static String extractRisk(JsonNode jsonNode) {
        if (jsonNode == null) return "N/A";
        JsonNode resultData = jsonNode.path("kycScoreResponse").path("body").path("return").path("resultData");
        String machineRisk = resultData.path("maschineRisk").asText("N/A");
        String finalRisk   = resultData.path("kycRisk").path("finalRisk").asText("N/A");
        return "**" + machineRisk + "** / **" + finalRisk + "** (Manually / Final)";
    }

    private static String formatCountry(String countryCode) {
        return countryCode.isEmpty() ? "" : countryCode + " : " + extractCountryName(countryCode);
    }

    // ---- Resource loading -------------------------------------------------

    /** Creates a fresh PdfFont per document from cached font bytes (PdfFont must not be shared across documents). */
    private static PdfFont createDocumentFont() throws IOException {
        return PdfFontFactory.createFont(getFontBytes(), PdfEncodings.IDENTITY_H, true);
    }

    private static byte[] getFontBytes() throws IOException {
        byte[] cached = fontBytesCache;
        if (cached != null) return cached;
        synchronized (PDFGenerator.class) {
            if (fontBytesCache == null) {
                byte[] bytes = loadResourceBytes(FONT_PATH);
                if (bytes == null) {
                    throw new IOException("Font file '" + FONT_PATH + "' not found in resources.");
                }
                fontBytesCache = bytes;
            }
            return fontBytesCache;
        }
    }

    private static byte[] loadResourceBytes(String resource) throws IOException {
        try (InputStream is = PDFGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return null;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        }
    }

    // =====================================================================
    // 9. Country code lookup
    // =====================================================================

    private static final Map<String, String> COUNTRY_NAMES = buildCountryNames();

    private static String extractCountryName(String countryCode) {
        return COUNTRY_NAMES.getOrDefault(countryCode, countryCode);
    }

    private static Map<String, String> buildCountryNames() {
        Map<String, String> m = new HashMap<>(300);
        m.put("AD", "Andorra");
        m.put("AE", "United Arab Emirates, Abu Dhabi, Ajman, Dubai, Fujayrah, Ras al Khaymah, Sharjah, Umm al Qaywayn");
        m.put("AF", "Afghanistan");
        m.put("AG", "Antigua, Barbuda");
        m.put("AI", "Anguilla");
        m.put("AL", "Albania");
        m.put("AM", "Armenia");
        m.put("AN", "Netherlands Antilles, Curacao Island, Bonaire, Saba, St. Eustatius, Dutch part of St. Martin");
        m.put("AO", "Angola, Cabinda-Landana");
        m.put("AQ", "Antarctica");
        m.put("AR", "Argentina");
        m.put("AS", "American Samoa");
        m.put("AT", "Austria (without Jungholz and Mittelberg)");
        m.put("AU", "Australia, Lord Howe Island, Tasmania");
        m.put("AW", "Aruba");
        m.put("AX", "?land Islands");
        m.put("AZ", "Azerbaijan");
        m.put("BA", "Bosnia and Herzegovina");
        m.put("BB", "Barbados");
        m.put("BD", "Bangladesh");
        m.put("BE", "Belgium");
        m.put("BF", "Burkina Faso (formerly Obervolta)");
        m.put("BG", "Bulgaria");
        m.put("BH", "Bahrain");
        m.put("BI", "Burundi");
        m.put("BJ", "Benin (formerly Dahomey)");
        m.put("BM", "Bermuda");
        m.put("BN", "Brunei");
        m.put("BO", "Bolivia");
        m.put("BR", "Brazil");
        m.put("BS", "Bahamas");
        m.put("BT", "Bhutan");
        m.put("BV", "Bouvet Island");
        m.put("BW", "Botswana");
        m.put("BY", "Belarus (formerly White Russia)");
        m.put("BZ", "Belize (formerly British Honduras)");
        m.put("CA", "Canada");
        m.put("CC", "Cocos (Keeling) Islands");
        m.put("CD", "Congo, Democratic Republic (formerly Zaire)");
        m.put("CF", "Central African Republic");
        m.put("CG", "Congo, Republic (formerly Congo)");
        m.put("CH", "Switzerland, Buesingen");
        m.put("CI", "Cote d'Ivoire (formerly Ivory Coast)");
        m.put("CK", "Cook Islands");
        m.put("CL", "Chile");
        m.put("CM", "Cameroon");
        m.put("CN", "China, People's Republic, Manchuria, Tibet, Inner Mongolia");
        m.put("CO", "Colombia");
        m.put("CR", "Costa Rica");
        m.put("CU", "Cuba");
        m.put("CV", "Cap Verde");
        m.put("CX", "Christmas Island (Indian Ocean)");
        m.put("CY", "Cyprus");
        m.put("CZ", "Czech Republic");
        m.put("DE", "Germany, Federal Republic");
        m.put("DJ", "Djibouti");
        m.put("DK", "Denmark");
        m.put("DM", "Dominica");
        m.put("DO", "Dominican Republic");
        m.put("DZ", "Algeria");
        m.put("EC", "Ecuador, Galapagos Islands");
        m.put("EE", "Estonia");
        m.put("EG", "Egypt");
        m.put("EH", "Western Sahara");
        m.put("ER", "Eritrea");
        m.put("ES", "Spain, Canary Islands, Tenerife");
        m.put("ET", "Ethiopia");
        m.put("FI", "Finland");
        m.put("FJ", "Fiji");
        m.put("FK", "Falkland Islands");
        m.put("FM", "Federated States of Micronesia, Caroline Islands");
        m.put("FO", "Faroe Islands");
        m.put("FR", "France, Monaco, Reunion, French Guiana, Guadeloupe, Desirade, Les Saintes, Marie Galante, St.Barth Islands, Martinique");
        m.put("GA", "Gabon");
        m.put("GB", "Great Britain, Northern Ireland, British Channel Islands, Isle of Man, United Kingdom");
        m.put("GD", "Grenada (formerly South Grenadines)");
        m.put("GE", "Georgian Republic");
        m.put("GF", "French Guiana");
        m.put("GH", "Ghana");
        m.put("GI", "Gibraltar");
        m.put("GL", "Greenland");
        m.put("GM", "Gambia");
        m.put("GN", "Guinea");
        m.put("GP", "Guadeloupe");
        m.put("GQ", "Equatorial Guinea, Annobon Island");
        m.put("GR", "Greece");
        m.put("GS", "South Georgia and South Sandwich Islands");
        m.put("GT", "Guatemala");
        m.put("GU", "Guam");
        m.put("GW", "Guinea-Bissau (formerly Portuguese Guinea)");
        m.put("GY", "Guyana");
        m.put("HM", "Heard and McDonald Islands");
        m.put("HN", "Honduras, Swan Islands");
        m.put("HR", "Croatia");
        m.put("HT", "Haiti");
        m.put("HU", "Hungary");
        m.put("ID", "Indonesia, South Borneo");
        m.put("IE", "Ireland");
        m.put("IL", "Israel");
        m.put("IN", "India, Sikkim");
        m.put("IO", "British Indian Ocean Territories, Chagos Islands");
        m.put("IQ", "Iraq");
        m.put("IR", "Iran, Islamic Republic");
        m.put("IS", "Iceland");
        m.put("IT", "Italy");
        m.put("JM", "Jamaica");
        m.put("JO", "Jordan");
        m.put("JP", "Japan, Riukiu Islands");
        m.put("KE", "Kenya");
        m.put("KG", "Kyrgyzstan");
        m.put("KH", "Cambodia");
        m.put("KI", "Kiribati (formerly Gilbert Islands), Christmas Islands (Pacific Ocean)");
        m.put("KM", "Comoros");
        m.put("KN", "St. Kitts, Nevis (formerly St.Christoph - Nevis)");
        m.put("KP", "Korea, Democratic People's Republic (formerly North Korea)");
        m.put("KR", "Korea, Republic (formerly South Korea)");
        m.put("KW", "Kuwait");
        m.put("KY", "Cayman Islands");
        m.put("KZ", "Kazakhstan");
        m.put("LA", "Laos, Democratic People's Republic");
        m.put("LB", "Lebanon");
        m.put("LC", "St. Lucia");
        m.put("LI", "Liechtenstein");
        m.put("LK", "Sri Lanka (formerly Ceylon)");
        m.put("LR", "Liberia");
        m.put("LS", "Lesotho");
        m.put("LT", "Lithuania");
        m.put("LU", "Luxembourg");
        m.put("LV", "Latvia");
        m.put("LY", "Libyan Arab Jamahiriya (formerly Libya)");
        m.put("MA", "Morocco");
        m.put("MC", "Monaco");
        m.put("MD", "Moldova, Republic");
        m.put("MG", "Madagascar");
        m.put("MH", "Marshall Islands");
        m.put("MK", "Macedonia (formerly Republic of Yugoslavia)");
        m.put("ML", "Mali");
        m.put("MM", "Myanmar (formerly Burma)");
        m.put("MN", "Mongolia");
        m.put("MO", "Macau");
        m.put("MP", "Northern Mariana Islands");
        m.put("MQ", "Martinique");
        m.put("MR", "Mauritania");
        m.put("MS", "Montserrat");
        m.put("MT", "Malta");
        m.put("MU", "Mauritius");
        m.put("MV", "Maldives");
        m.put("MW", "Malawi");
        m.put("MX", "Mexico");
        m.put("MY", "Malaysia, East Malaysia, Labuan, Sarawak, Sabah, North Borneo");
        m.put("MZ", "Mozambique");
        m.put("NA", "Namibia");
        m.put("NC", "New Caledonia");
        m.put("NE", "Niger");
        m.put("NF", "Norfolk Island");
        m.put("NG", "Nigeria");
        m.put("NI", "Nicaragua");
        m.put("NL", "Netherlands");
        m.put("NO", "Norway, Spitsbergen, Svalbard");
        m.put("NP", "Nepal");
        m.put("NR", "Nauru");
        m.put("NU", "Niue");
        m.put("NZ", "New Zealand, Campell Island");
        m.put("OM", "Oman");
        m.put("PA", "Panama (incl. former channel zone), Cristobal");
        m.put("PE", "Peru");
        m.put("PF", "French Polynesia, Society, Sous-le-Vent, Tahiti, Tuamotu, Tubuai Islands");
        m.put("PG", "Papua New Guinea, Solomon Islands");
        m.put("PH", "Philippines");
        m.put("PK", "Pakistan");
        m.put("PL", "Poland");
        m.put("PM", "St. Pierre, Miquelon");
        m.put("PN", "Pitcairn");
        m.put("PR", "Puerto Rico");
        m.put("PS", "Occupied Palestinian Territories (formerly Gaza Strip, Jericho, East Jerusalem, West Bank)");
        m.put("PT", "Portugal, Madeira, Azores");
        m.put("PW", "Palau");
        m.put("PY", "Paraguay");
        m.put("QA", "Qatar");
        m.put("RE", "Réunion");
        m.put("HK", "Hong Kong");
        m.put("RO", "Romania");
        m.put("RU", "Russia, Russian Federation");
        m.put("RW", "Rwanda");
        m.put("SA", "Saudi Arabia");
        m.put("SB", "Solomon Islands, Santa Cruz Islands, Lord Howe Island (Solomon Islands)");
        m.put("SC", "Seychelles, Amirante Island");
        m.put("SD", "Sudan");
        m.put("SE", "Sweden");
        m.put("SG", "Singapore");
        m.put("SH", "St. Helena, Ascension, Gough, Tristan de Cunha");
        m.put("SI", "Slovenia");
        m.put("SJ", "Svalbard and Jan Mayen");
        m.put("SK", "Slovakia");
        m.put("SL", "Sierra Leone");
        m.put("SM", "San Marino");
        m.put("SN", "Senegal");
        m.put("SO", "Somalia");
        m.put("SR", "Suriname");
        m.put("ST", "Sao Tome and Principe");
        m.put("SV", "El Salvador");
        m.put("SY", "Syria, Arab Republic");
        m.put("SZ", "Swaziland");
        m.put("TC", "Turks and Caicos Islands");
        m.put("TD", "Chad");
        m.put("TF", "French Southern Territories");
        m.put("TG", "Togo");
        m.put("TH", "Thailand, Siam");
        m.put("TJ", "Tajikistan");
        m.put("TK", "Tokelau");
        m.put("TL", "Timor-Leste");
        m.put("TM", "Turkmenistan");
        m.put("TN", "Tunisia");
        m.put("TO", "Tonga");
        m.put("TP", "East Timor");
        m.put("TR", "Turkey");
        m.put("TT", "Trinidad, Tobago");
        m.put("TV", "Tuvalu");
        m.put("TW", "Taiwan, Formosa");
        m.put("TZ", "Zanzibar, Tanzania, United Republic");
        m.put("UA", "Ukraine");
        m.put("UG", "Uganda");
        m.put("UM", "United States Minor Outlying Islands");
        m.put("US", "U S A, United States of America, Puerto Rico");
        m.put("UY", "Uruguay");
        m.put("UZ", "Uzbekistan");
        m.put("VA", "Vatican City");
        m.put("VC", "St. Vincent and North Grenadines");
        m.put("VE", "Venezuela");
        m.put("VG", "Virgin Islands (GB)");
        m.put("VI", "Virgin Islands (USA)");
        m.put("VN", "Vietnam");
        m.put("VU", "Vanuatu");
        m.put("WF", "Wallis, Futuna, Alofi Islands");
        m.put("WS", "Samoa (formerly Western Samoa)");
        m.put("XC", "Ceuta");
        m.put("XL", "Melilla");
        m.put("YE", "Yemen, Aden");
        m.put("YT", "Mayotte");
        m.put("ZA", "South Africa");
        m.put("ZM", "Zambia");
        m.put("ZW", "Zimbabwe (formerly Rhodesia)");
        m.put("--", "(Foreign or unknown country)");
        m.put("ME", "Montenegro");
        m.put("XS", "Serbia (old)");
        m.put("IM", "Isle of Man");
        m.put("SS", "Südsudan");
        m.put("GG", "GUERNSEY (C. I.)");
        m.put("JE", "JERSEY (C. I.)");
        m.put("PZ", "PANAMA CANAL ZONE");
        m.put("UW", "UNKNOWN");
        m.put("YU", "YUGOSLAVIA");
        m.put("ZR", "ZAIRE");
        m.put("FW", "Guinea-Bissau");
        m.put("RS", "Serbia");
        m.put("DBZ", "TEST COUNTRY DBZ");
        m.put("IK", "DENMARK");
        m.put("JA", "JOINT ACCOUNT");
        m.put("MF", "SAINT MARTIN");
        m.put("RSA", "REPUBLIC OF SOUTH AFRICA");
        m.put("XX", "DUMMY");
        m.put("ZZ", "MIGRATION");
        m.put("CW", "Curaçao");
        m.put("SX", "Sint Maarten");
        m.put("XK", "Kosovo");
        m.put("AB", "ABKHAZIA");
        m.put("AK", "AKROTIRI AND DHEKELIA");
        m.put("AY", "ASCENSION ISLAND");
        m.put("BK", "BOYS");
        m.put("CB", "COX ISLANDS");
        m.put("CS", "SERBIA AND MONTENEGRO");
        m.put("DD", "DMDefault");
        m.put("KO", "REPUBLIC OF KOSOVO");
        m.put("NN", "STATELESS NATIONALITY");
        m.put("SQ", "SOUTH OSSETIA");
        m.put("TS", "TRANSNISTRIA");
        m.put("DS", "GERMANY_ISO");
        m.put("DR", "REPUBLIC OF CONGO");
        return Collections.unmodifiableMap(m);
    }

    // =====================================================================
    // Local test entry point
    // =====================================================================

    public static void main(String[] args) {
        try {
            String jsonContent = readJsonFromFile("package.json");
            PDFResult pdfResult = generatePDF("John Doe", jsonContent);
            savePdfToFile(pdfResult.getPdfBytes(), pdfResult.getFileName());
            System.out.println("PDF saved at: " + pdfResult.getFileName());

            Scanner input = new Scanner(System.in);
            System.out.println("Open PDF? (Y/N)");
            if (input.next().equalsIgnoreCase("Y")) {
                java.awt.Desktop.getDesktop().open(new File(pdfResult.getFileName()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}