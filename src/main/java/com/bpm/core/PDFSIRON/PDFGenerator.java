package com.bpm.core.PDFSIRON;
import com.bpm.core.PDFSIRON.Configs.HeaderFooterEvent;
import com.bpm.core.PDFSIRON.Configs.PageNumberEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
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

public class PDFGenerator {
    private static final Logger LOGGER = Logger.getLogger(PDFGenerator.class.getName());
    private static final DeviceRgb HEADER_BG = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb CELL_BG = new DeviceRgb(240, 240, 240);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private static final DeviceRgb CELL_BACKGROUND_COLOR = new DeviceRgb(240, 240, 240);
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("MMddyyyyHHmmss");
    private static final float[] COL_WIDTHS = {25.0F, 25.0F, 25.0F, 25.0F};
    private static final String FONT_PATH = "arial.ttf"; // Ensure this matches the actual font file in resources

    public static void decodeBase64(String base64) {
        try {
            byte[] pdfBytes = Base64.getDecoder().decode(base64);
            savePdfToFile(pdfBytes, "output.pdf");
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.severe("Error decoding Base64: " + e.getMessage());
        }
    }
    public static Map<String, String> generatePDFAsBase64(String fullName, String jsonContent) {
        try {
            PDFResult pdfResult = generatePDF(fullName, jsonContent);
            String base64Content = Base64.getEncoder().encodeToString(pdfResult.getPdfBytes());
            Map<String, String> result = new HashMap<>(2);
            result.put("fileName", pdfResult.getFileName());
            result.put("base64Content", base64Content);
            return result;
        } catch (IOException e) {
            LOGGER.severe("Error generating PDF: " + e.getMessage());
            Map<String, String> errorResult = new HashMap<>(2);
            errorResult.put("fileName", "Error generating PDF: " + e.getMessage());
            errorResult.put("base64Content", null);
            return errorResult;
        }
    }
    public static PDFResult generatePDF(String fullName, String jsonContent) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // Load fonts for this specific document
            PdfFont arabicFont = loadFontForDocument();
            PdfFont defaultFont = arabicFont; // Use arabicFont as default for consistency
            PdfFont boldFont = loadFontForDocument(); // Use same font or load a bold variant if available

            JsonNode jsonNode = new ObjectMapper().readTree(jsonContent);
            PdfWriter writer = new PdfWriter(outputStream);
            writer.setCompressionLevel(9);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(360.0F, 10.0F, 60.0F, 10.0F);
            byte[] imageBytes = loadImageBytes("BankLogo_FAB.png");
            if (imageBytes != null) {
                ImageData imageData = ImageDataFactory.create(imageBytes);
                Image image = new Image(imageData);
                String metadata = "sironKYC 22.1 (220801)\nGenerated on: " + DATE_FORMAT.format(new Date());
                pdf.addEventHandler("StartPdfPage", new HeaderFooterEvent(document, image, "SironKYC", metadata, jsonNode, fullName));
                pdf.addEventHandler("EndPdfPage", new PageNumberEventHandler(document));
            }
            processMatches(document, jsonNode, pdf, defaultFont, boldFont);
            String fileName = addEndContent(document, jsonNode, defaultFont, boldFont);
            document.close();
            return new PDFResult(fileName, outputStream.toByteArray());
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing JSON: " + e.getMessage(), e);
        } finally {
            outputStream.close();
        }
    }
    private static PdfFont loadFontForDocument() throws IOException {
        InputStream fontStream = PDFGenerator.class.getClassLoader().getResourceAsStream(FONT_PATH);
        if (fontStream == null) {
            throw new IOException("Font file '" + FONT_PATH + "' not found in resources.");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = fontStream.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        fontStream.close();

        return PdfFontFactory.createFont(buffer.toByteArray(), PdfEncodings.IDENTITY_H, true);
    }

    private static String addEndContent(Document document, JsonNode jsonNode, PdfFont defaultFont, PdfFont boldFont) throws IOException {
        String dateTime = DATE_FORMAT.format(new Date());
        PdfFont timesRomanBold = PdfFontFactory.createFont("Times-Roman", "Cp1252", true);
        PdfFont timesRomanRegular = PdfFontFactory.createFont("Times-Roman", "Cp1252", true);
        addHistorySection(document, jsonNode, timesRomanBold, timesRomanRegular, dateTime);
        String fileName = addAttachmentsSection(document, jsonNode, timesRomanRegular, dateTime);
        addEndNote(document, timesRomanRegular);
        return fileName;
    }

    private static void addEndNote(Document document, PdfFont regularFont) {
        Paragraph endNote = new Paragraph("End of document - Number of pages: ** " + document.getPdfDocument().getNumberOfPages() + " **(including this page)")
                .setFont(regularFont)
                .setFontSize(9.0f)
                .setUnderline()
                .setBackgroundColor(CELL_BACKGROUND_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(50.0f)
                .setMarginTop(20.0f);
        document.add(endNote);
    }

    private static void addHistorySection(Document document, JsonNode jsonNode, PdfFont boldFont, PdfFont regularFont, String dateTime) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{21.0f, 1.0f})).useAllAvailableWidth();
        table.addCell(new Cell().add(new Paragraph("History").setFont(boldFont)).setFontSize(9.0f).setBackgroundColor(CELL_BACKGROUND_COLOR).setBorder(new SolidBorder(0.5f)));
        table.setMarginLeft(25.0f).setMarginTop(10.0f);
        document.add(table);
        Table historyTable = new Table(UnitValue.createPercentArray(new float[]{1.0f, 2.0f})).useAllAvailableWidth();
        historyTable.setMarginLeft(25.0f).setMarginRight(25.0f).setMarginTop(5.0f);
        addHistoryRow(historyTable, "Status", "New, unedited", regularFont);
        addHistoryRow(historyTable, "Date/time", dateTime, regularFont);
        addHistoryRow(historyTable, "Resubmission", "--- / --- (Manually / Automatic)", regularFont);
        addHistoryRow(historyTable, "Alert", "", regularFont);
        addHistoryRow(historyTable, "Comment", "", regularFont);
        addHistoryRow(historyTable, "Risk", jsonNode != null ? extractRisk(jsonNode) : "N/A", regularFont);
        addHistoryRow(historyTable, "Hit type", "E: SL", regularFont);
        document.add(historyTable);
    }

    private static String addAttachmentsSection(Document document, JsonNode jsonNode, PdfFont regularFont, String dateTime) {
        Table attachmentsTableH = new Table(UnitValue.createPercentArray(new float[]{2.0f, 1.0f, 1.0f})).useAllAvailableWidth();
        attachmentsTableH.addCell(new Cell().add(new Paragraph("Attachments").setFont(regularFont)).setFontSize(9.0f).setBackgroundColor(CELL_BACKGROUND_COLOR).setBorder(new SolidBorder(0.5f)));
        attachmentsTableH.addCell(new Cell().add(new Paragraph("Status").setFont(regularFont)).setFontSize(9.0f).setBackgroundColor(CELL_BACKGROUND_COLOR).setBorder(new SolidBorder(0.5f)));
        attachmentsTableH.addCell(new Cell().add(new Paragraph("Version").setFont(regularFont)).setFontSize(9.0f).setBackgroundColor(CELL_BACKGROUND_COLOR).setBorder(new SolidBorder(0.5f)));
        attachmentsTableH.setMarginLeft(25.0f).setMarginRight(25.0f).setMarginTop(10.0f);
        document.add(attachmentsTableH);
        int randomNumber = (int) (Math.random() * 9.99999999E8) + 1;
        String formattedNumber = new DecimalFormat("000000000").format(randomNumber);
        String fileName = (jsonNode != null ? extractPersonId(jsonNode) : "N/A") + "_" + formattedNumber + "@" + FILE_DATE_FORMAT.format(new Date()) + ".pdf";
        Table attachmentsTable = new Table(UnitValue.createPercentArray(new float[]{2.0f, 1.0f, 1.0f})).useAllAvailableWidth();
        attachmentsTable.setMarginLeft(25.0f).setMarginRight(25.0f);
        attachmentsTable.addCell(new Cell().add(new Paragraph(fileName).setFont(regularFont)).setFontSize(9.0f).setBorder(new SolidBorder(0.5f)));
        attachmentsTable.addCell(new Cell().add(new Paragraph("New, unedited").setFont(regularFont)).setFontSize(9.0f).setBorder(new SolidBorder(0.5f)));
        attachmentsTable.addCell(new Cell().add(new Paragraph(dateTime).setFont(regularFont)).setFontSize(9.0f).setBorder(new SolidBorder(0.5f)));
        document.add(attachmentsTable);
        return fileName;
    }

    private static byte[] loadImageBytes(String resource) throws IOException {
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

    private static void processMatches(Document document, JsonNode jsonNode, PdfDocument pdf, PdfFont defaultFont, PdfFont boldFont) throws IOException, ParseException {
        JsonNode listMatches = jsonNode.path("kycScoreResponse").path("body").path("return").path("personResult").path("personDetailResult").path("listMatches");
        if (!listMatches.isArray() || listMatches.isEmpty()) {
            document.add(new Paragraph("No match data found.").setFont(defaultFont).setTextAlignment(TextAlignment.CENTER).setMarginTop(320.0F));
            return;
        }
        for (int i = 0; i < listMatches.size(); i++) {
            if (i > 0) {
                // pdf.addNewPage();
            }
            document.add(createContentTable(listMatches.get(i), defaultFont, boldFont));
        }
    }

    private static Table createContentTable(JsonNode match, PdfFont defaultFont, PdfFont boldFont) throws IOException, ParseException {
        Table table = new Table(UnitValue.createPercentArray(COL_WIDTHS)).useAllAvailableWidth().setMargins(10.0F, 10.0F, 10.0F, 10.0F);
        addTitleRow(table, match, boldFont);
        addHeaderRow(table, match, defaultFont, boldFont);
        addDataRows(table, match, defaultFont, boldFont);
        return table;
    }

    private static void addTitleRow(Table table, JsonNode match, PdfFont boldFont) throws ParseException {
        PdfFont arabicFont;
        try {
            arabicFont = loadFontForDocument();
        } catch (IOException ex) {
            LOGGER.severe("Failed to load Arabic font: " + ex.getMessage());
            throw new RuntimeException("Font initialization failed", ex);
        }
        table.addCell(new Cell(1, 4).setBorder(null)
                .add(new Paragraph(getAlertTitle(match)).setFont(boldFont)).setFontSize(9.0F)
                .setTextAlignment(TextAlignment.LEFT).setBackgroundColor(CELL_BG).setPadding(5.0F));
    }

    private static void addHeaderRow(Table table, JsonNode match, PdfFont defaultFont, PdfFont boldFont) throws IOException {
        PdfFont arabicFont = loadFontForDocument();
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
        table.addCell(new Cell(1, 4).add(new Paragraph("Alert Words").setFont(boldFont)).setFontSize(9.0F).setPaddingLeft(10.0F).setBackgroundColor(HEADER_BG).setBold());
    }

    private static Cell createHeaderCell(String text, PdfFont boldFont) {
        return new Cell(1, 2).add(new Paragraph(text).setFont(boldFont)).setFontSize(9.0F)
                .setTextAlignment(TextAlignment.LEFT).setBackgroundColor(HEADER_BG).setPadding(5.0F);
    }

    private static void addFieldIfPresent(Table table, String key, JsonNode node, PdfFont defaultFont) throws IOException {
        String value = node.asText("").trim();
        if (!value.isEmpty()) {
            addKeyValueRow(table, key, new TextNode(value), defaultFont);
        }
    }

    private static void processIdentificationDocuments(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slPassNos = match.path("watchListData").path("slPassNos");
        if (slPassNos.isArray() && !slPassNos.isEmpty()) {
            int index = 1;
            for (JsonNode slPassNo : slPassNos) {
                String passNo = getValidText(slPassNo.path("passNo"));
                String country = getValidText(slPassNo.path("country"));
                String type = getValidText(slPassNo.path("type"));
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
                    String countryName = extractCountryName(country);
                    addKeyValueRow(table, "Country", new TextNode(country + " : " + countryName), defaultFont);
                }
                index++;
            }
        }
    }
    private static void processNames(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slNames = match.path("watchListData").path("slNames");
        if (!slNames.isArray() || slNames.isEmpty()) return;

        int index = 1;
        for (JsonNode slName : slNames) {
            // Reuse validated text values rather than re-reading JsonNodes many times
            String firstName = getValidText(slName.path("firstName"));
            String lastName = getValidText(slName.path("lastName"));
            String middleName = getValidText(slName.path("middleName"));
            String wholeName = getValidText(slName.path("wholeName"));
            String gender = getValidText(slName.path("gender"));
            String title = getValidText(slName.path("title"));

            // skip empty / totally blank name objects
            if (firstName.isEmpty() && lastName.isEmpty() && middleName.isEmpty()
                    && wholeName.isEmpty() && gender.isEmpty() && title.isEmpty()) {
                continue;
            }

            // Header row for this name entry (don't repeatedly set table-wide background here)
            addKeyValueRow(table, "Name (" + index + ")", new TextNode(""), defaultFont);

            // Add the name parts if present
            if (!lastName.isEmpty()) {
                addKeyValueRow(table, "Surname", new TextNode(lastName), defaultFont);
            }
            if (!firstName.isEmpty()) {
                addKeyValueRow(table, "Forename", new TextNode(firstName), defaultFont);
            }
            if (!middleName.isEmpty()) {
                addKeyValueRow(table, "Middle Name", new TextNode(middleName), defaultFont);
            }

            // Decide Type and Full Name logic:
            // - If both first and last present -> PRIMARY NAME
            // - Else if first and/or last empty but wholeName present and first/last both empty -> ALIASES (only whole name known)
            // - If wholeName exists in addition to first/last, show Full Name as extra row (but Type remains PRIMARY)
            if (!firstName.isEmpty() && !lastName.isEmpty()) {
                addKeyValueRow(table, "Type", new TextNode("PRIMARY NAME"), defaultFont);
                if (!wholeName.isEmpty()) {
                    addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
                }
            } else if (firstName.isEmpty() && lastName.isEmpty() && !wholeName.isEmpty()) {
                addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
                addKeyValueRow(table, "Type", new TextNode("ALIASES"), defaultFont);
            } else {
                addKeyValueRow(table, "Type", new TextNode("PRIMARY NAME"), defaultFont);
                if (!wholeName.isEmpty()) {
                    addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
                }
            }

            if (!gender.isEmpty()) {
                String genderText = gender.equalsIgnoreCase("M") ? "M: MALE" :
                        gender.equalsIgnoreCase("F") ? "F: FEMALE" :
                                gender;
                addKeyValueRow(table, "Gender", new TextNode(genderText), defaultFont);
            }
            if (!title.isEmpty()) {
                addKeyValueRow(table, "Title", new TextNode(title), defaultFont);
            }

            index++;
        }
    }

//    private static void processNames(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
//        JsonNode slNames = match.path("watchListData").path("slNames");
//        if (slNames.isArray() && !slNames.isEmpty()) {
//            int index = 1;
//            for (JsonNode slName : slNames) {
//                String firstName = getValidText(slName.path("firstName"));
//                String lastName = getValidText(slName.path("lastName"));
//                String middleName = getValidText(slName.path("middleName"));
//                String wholeName = getValidText(slName.path("wholeName"));
//                String gender = getValidText(slName.path("gender"));
//                String title = getValidText(slName.path("title"));
//                if (firstName.isEmpty() && lastName.isEmpty() && middleName.isEmpty() && wholeName.isEmpty() && gender.isEmpty() && title.isEmpty()) continue;
//                addKeyValueRow(table.setBackgroundColor(CELL_BG), "Name (" + index + ")", new TextNode(""), defaultFont);
//                if (!lastName.isEmpty() || !slName.path("lastName").has("nil")) {
//                    addKeyValueRow(table, "Surname", slName.path("lastName"), defaultFont);
//
//                }
//                if (!firstName.isEmpty() || !slName.path("firstName").has("nil")) {
//                    addKeyValueRow(table, "Forename", slName.path("firstName"), defaultFont);
//                }
//                if (!middleName.isEmpty() || !slName.path("middleName").has("nil")) {
//                    addKeyValueRow(table, "Middle Name", slName.path("middleName"), defaultFont);
//                }
//                if (lastName.isEmpty() && firstName.isEmpty() && !wholeName.isEmpty()) {
//                    addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
//                    addKeyValueRow(table, "Type", new TextNode("ALIASES"), defaultFont);
//                } else if (!lastName.isEmpty() && !firstName.isEmpty()) {
//                    addKeyValueRow(table, "Type", new TextNode("PRIMARY NAME"), defaultFont);
//                }
//                else if(!lastName.isEmpty() && !firstName.isEmpty() && !wholeName.isEmpty() )
//                {
//                    addKeyValueRow(table, "Full Name", new TextNode(wholeName), defaultFont);
//                    addKeyValueRow(table, "Forename", slName.path("firstName"), defaultFont);
//                    addKeyValueRow(table, "Surname", slName.path("lastName"), defaultFont);
//
//
//                }
//                if (!gender.isEmpty()) {
//                    addKeyValueRow(table, "Gender", new TextNode(gender.equals("M") ? "M: MALE" : "F: FEMALE"), defaultFont);
//                }
//                if (!title.isEmpty()) {
//                    addKeyValueRow(table, "Title", slName.path("title"), defaultFont);
//                }
//                index++;
//            }
//        }
//    }

    private static void processBirthDates(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slBirthDates = match.path("watchListData").path("slBirths");
        if (slBirthDates.isArray() && !slBirthDates.isEmpty()) {
            int index = 1;
            for (JsonNode slBirthDate : slBirthDates) {
                String rawBirthDate = getValidText(slBirthDate.path("birthDate"));
                String birthPlace = getValidText(slBirthDate.path("place"));
                String country = getValidText(slBirthDate.path("country"));
                if (rawBirthDate.isEmpty() && birthPlace.isEmpty() && country.isEmpty()) continue;
                addKeyValueRow(table.setBackgroundColor(CELL_BG), "Birth Date (" + index + ")", new TextNode(""), defaultFont);
                if (!rawBirthDate.isEmpty() && !slBirthDate.path("birthDate").has("nil")) {
                    String year = rawBirthDate.substring(0, 4);
                    String month = rawBirthDate.substring(4, 6);
                    String day = rawBirthDate.substring(6, 8);
                    String formattedDate = month.equals("00") && day.equals("00") ? "00/00/" + year : month + "/" + day + "/" + year;
                    addKeyValueRow(table, "Date", new TextNode(formattedDate), defaultFont);
                }
                if (!birthPlace.isEmpty() || !slBirthDate.path("place").has("nil")) {
                    addKeyValueRow(table, "Location", new TextNode(birthPlace), defaultFont);
                }
                if (!country.isEmpty() || !slBirthDate.path("country").has("nil")) {
                    String countryName = extractCountryName(country);
                    addKeyValueRow(table, "Country", new TextNode(country + " : " + countryName), defaultFont);
                }
                index++;
            }
        }
    }
    private static void processNationalities(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slCities = match.path("watchListData").path("slCitizs");
        if (slCities.isArray() && !slCities.isEmpty()) {
            for (JsonNode slCity : slCities) {
                String city = getValidText(slCity.path("country"));
                if (city.isEmpty() && slCity.path("country").has("nil")) continue;
                String countryName = city + ":" + extractCountryName(city);
                if (!countryName.isEmpty()) {
                    addKeyValueRow(table.setBackgroundColor(CELL_BG), "Nationality", new TextNode(countryName), defaultFont);
                }
            }
        }
    }

    private static void processAddresses(Table table, JsonNode match, PdfFont defaultFont) throws IOException {
        JsonNode slAddresses = match.path("watchListData").path("slAddresses");
        if (slAddresses.isArray() && !slAddresses.isEmpty()) {
            int index = 1;
            for (JsonNode slAddress : slAddresses) {
                String address = getValidText(slAddress.path("address"));
                String city = getValidText(slAddress.path("city"));
                String country = getValidText(slAddress.path("country"));
                String state = getValidText(slAddress.path("state"));
                String street = getValidText(slAddress.path("street"));
                String streetNumber = getValidText(slAddress.path("streetNumber"));
                String zip = getValidText(slAddress.path("zip"));
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
    }

    private static void addFieldIfPresent(Table table, String key, String value, JsonNode node, PdfFont defaultFont) throws IOException {
        if (!value.isEmpty() && !node.has("nil")) {
            addKeyValueRow(table, key, new TextNode(value), defaultFont);
        }
    }

    private static boolean isAllFieldsEmpty(String... fields) {
        for (String field : fields) {
            if (!field.isEmpty()) return false;
        }
        return true;
    }

    private static String formatCountry(String countryCode) {
        return countryCode.isEmpty() ? "" : countryCode + " : " + extractCountryName(countryCode);
    }

    private static boolean containsArabic(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.ARABIC) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static void addKeyValueRow(Table table, String key, JsonNode valueNode, PdfFont defaultFont) throws IOException {
        PdfFont arabicFont = loadFontForDocument();
        String value = valueNode.asText().trim();
        String[] customized = customizeKeysAndValue(key, value);
        String customizedKey = customized[0] != null && !customized[0].isEmpty() ? customized[0] : " ";
        String customizedValue = customized[1] != null && !customized[1].isEmpty() ? customized[1] : " ";

        Cell keyCell = new Cell(1, 2)
                .add(new Paragraph(customizedKey).setFont(defaultFont))
                .setFontSize(9.0F)
                .setTextAlignment(TextAlignment.LEFT)
                .setPaddingLeft(15.0F)
                .setBackgroundColor(DeviceRgb.WHITE);

        Cell valueCell;
        if (containsArabic(customizedValue)) {
            List<String> lines = splitTextIntoLines(customizedValue, 70);
            Paragraph arabicParagraph = new Paragraph()
                    .setFont(arabicFont)
                    .setFontSize(9.0F)
                    .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                    .setTextAlignment(TextAlignment.RIGHT);
            ArabicLigaturizer arabicProcessor = new ArabicLigaturizer();
            for (String line : lines) {
                arabicParagraph.add(arabicProcessor.process(line));
                if (lines.indexOf(line) < lines.size() - 1) {
                    arabicParagraph.add("\n");
                }
            }
            valueCell = new Cell(1, 2)
                    .add(arabicParagraph)
                    .setPaddingRight(10.0F)
                    .setBackgroundColor(DeviceRgb.WHITE);
        } else {
            valueCell = new Cell(1, 2)
                    .add(new Paragraph(customizedValue).setFont(defaultFont))
                    .setFontSize(9.0F)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setPaddingLeft(10.0F)
                    .setBackgroundColor(DeviceRgb.WHITE);
        }

        if (customizedKey.contains("(") || customizedKey.contains("Nationality")) {
            keyCell.setPaddingLeft(5.0F).setBackgroundColor(HEADER_BG);
            valueCell.setBackgroundColor(HEADER_BG);
        }

        table.addCell(keyCell);
        table.addCell(valueCell);
        table.setFixedLayout();
    }

    private static String bidiReorder(String text) {
        try {
            ArabicShaping shaper = new ArabicShaping(8);
            String shaped = shaper.shape(text);
            Bidi bidi = new Bidi(shaped, 1);
            bidi.setReorderingMode(0);
            return bidi.writeReordered(2);
        } catch (ArabicShapingException var4) {
            return text;
        }
    }

    private static List<String> splitTextIntoLines(String text, int maxChars) {
        List<String> lines = new ArrayList();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 <= maxChars) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static void addDataRows(Table table, JsonNode match, PdfFont defaultFont, PdfFont boldFont) {
        JsonNode hits = match.path("hits");
        addRow(table, "Field", "Person data", "List data", "Hit value", true, defaultFont, boldFont);
        if (hits.isArray() && !hits.isEmpty()) {
            for (JsonNode hit : hits) {
                addRow(table.setBackgroundColor(DeviceRgb.WHITE), "Full Name", extractLastName(hit), extractFullName(hit), extractHitValue(hit), false, defaultFont, boldFont);
            }
        } else {
            addRow(table, "No hits found", "", "", "", false, defaultFont, boldFont);
            LOGGER.info("No hits array found in listMatches or it is empty.");
        }
    }

    private static void addRow(Table table, String field, String personData, String listData, String hitValue, boolean isHeader, PdfFont defaultFont, PdfFont boldFont) {
        PdfFont font = isHeader ? boldFont : defaultFont;
        table.setMarginRight(25.0F).setMarginLeft(25.0F);
        table.addCell(new Cell().add(new Paragraph(field).setFont(font)).setFontSize(9.0F).setTextAlignment(TextAlignment.LEFT).setBackgroundColor(isHeader ? CELL_BG : null));
        table.addCell(new Cell().add(new Paragraph(personData).setFont(font)).setFontSize(9.0F).setTextAlignment(TextAlignment.LEFT).setBackgroundColor(isHeader ? CELL_BG : null));
        table.addCell(new Cell().add(new Paragraph(listData).setFont(font)).setFontSize(9.0F).setTextAlignment(TextAlignment.LEFT).setBackgroundColor(isHeader ? CELL_BG : null));
        table.addCell(new Cell().add(new Paragraph(hitValue).setFont(font)).setFontSize(9.0F).setTextAlignment(TextAlignment.LEFT).setBackgroundColor(isHeader ? CELL_BG : null));
        table.setFixedLayout();
    }

    private static String getValidText(JsonNode node) {
        return !node.has("nil") && !node.asText().trim().isEmpty() ? node.asText().trim() : "";
    }

    private static String getAlertTitle(JsonNode match) throws ParseException {
        try {
            StringBuilder titleBuilder = new StringBuilder();
            if (!match.isMissingNode()) {
                if (match.has("slListName") && isValidValue(match.get("slListName").asText())) {
                    titleBuilder.append(match.get("slListName").asText());
                }
                if (match.has("slId") && isValidValue(match.get("slId").asText())) {
                    titleBuilder.append(" ").append(match.get("slId").asText()).append(" ");
                }
                if (match.has("score") && match.has("Hit_Type") && isValidValue(match.get("Hit_Type").asText())) {
                    titleBuilder.append(" , Score: (").append(match.get("score").asText()).append("%)");
                }
                if (match.has("Hit_date")) {
                    String hitDate = match.get("Hit_date").asText();
                    if (isValidValue(hitDate)) {
                        SimpleDateFormat sourceFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                        Date date = sourceFormat.parse(hitDate);
                        SimpleDateFormat targetFormat = new SimpleDateFormat("MM/dd/yyyy, hh:mm a");
                        titleBuilder.append("  Generated (").append(targetFormat.format(date)).append(")");
                    }
                }
            } else {
                titleBuilder.append("No Match Found");
            }
            return titleBuilder.toString();
        } catch (Exception e) {
            LOGGER.severe("Error generating alert title: " + e.getMessage());
            return "Error generating alert title";
        }
    }

    private static boolean isValidValue(String value) {
        return value != null && !value.isEmpty() && !value.trim().isEmpty() && !value.equals("null") && !value.equals("nil");
    }

    private static String extractFullName(JsonNode hit) {
        return hit.path("matchedWord").asText("N/A");
    }

    private static String extractLastName(JsonNode hit) {
        return hit.path("searchedWord").asText("N/A");
    }

    private static String extractHitValue(JsonNode hit) {
        return hit.path("score").asText("N/A");
    }

    public static String[] customizeKeysAndValue(String key, String value) {
        switch (key) {
            case "type":
                key = "Type";
                break;
            case "comment":
                key = "Comment";
                break;
            case "slLegalBasis":
                key = "Regulation";
                break;
        }
        if (value.equals("P")) value = "Individual";
        else if (value.equals("E")) value = "Entity";
        else if (value.equals("C")) value = "Company";
        else if (value.equals("V")) value = "Vessel";
        else if (value.isEmpty() || value.equals("null")) value = " ";
        return new String[]{key, value};
    }

    private static String extractCountryName(String countryCode) {
        switch (countryCode) {
            case "AD":
                return "Andorra";
            case "AE":
                return "United Arab Emirates, Abu Dhabi, Ajman, Dubai, Fujayrah, Ras al Khaymah, Sharjah, Umm al Qaywayn";
            case "AF":
                return "Afghanistan";
            case "AG":
                return "Antigua, Barbuda";
            case "AI":
                return "Anguilla";
            case "AL":
                return "Albania";
            case "AM":
                return "Armenia";
            case "AN":
                return "Netherlands Antilles, Curacao Island, Bonaire, Saba, St. Eustatius, Dutch part of St. Martin";
            case "AO":
                return "Angola, Cabinda-Landana";
            case "AQ":
                return "Antarctica";
            case "AR":
                return "Argentina";
            case "AS":
                return "American Samoa";
            case "AT":
                return "Austria (without Jungholz and Mittelberg)";
            case "AU":
                return "Australia, Lord Howe Island, Tasmania";
            case "AW":
                return "Aruba";
            case "AX":
                return "?land Islands";
            case "AZ":
                return "Azerbaijan";
            case "BA":
                return "Bosnia and Herzegovina";
            case "BB":
                return "Barbados";
            case "BD":
                return "Bangladesh";
            case "BE":
                return "Belgium";
            case "BF":
                return "Burkina Faso (formerly Obervolta)";
            case "BG":
                return "Bulgaria";
            case "BH":
                return "Bahrain";
            case "BI":
                return "Burundi";
            case "BJ":
                return "Benin (formerly Dahomey)";
            case "BM":
                return "Bermuda";
            case "BN":
                return "Brunei";
            case "BO":
                return "Bolivia";
            case "BR":
                return "Brazil";
            case "BS":
                return "Bahamas";
            case "BT":
                return "Bhutan";
            case "BV":
                return "Bouvet Island";
            case "BW":
                return "Botswana";
            case "BY":
                return "Belarus (formerly White Russia)";
            case "BZ":
                return "Belize (formerly British Honduras)";
            case "CA":
                return "Canada";
            case "CC":
                return "Cocos (Keeling) Islands";
            case "CD":
                return "Congo, Democratic Republic (formerly Zaire)";
            case "CF":
                return "Central African Republic";
            case "CG":
                return "Congo, Republic (formerly Congo)";
            case "CH":
                return "Switzerland, Buesingen";
            case "CI":
                return "Cote d'Ivoire (formerly Ivory Coast)";
            case "CK":
                return "Cook Islands";
            case "CL":
                return "Chile";
            case "CM":
                return "Cameroon";
            case "CN":
                return "China, People's Republic, Manchuria, Tibet, Inner Mongolia";
            case "CO":
                return "Colombia";
            case "CR":
                return "Costa Rica";
            case "CU":
                return "Cuba";
            case "CV":
                return "Cap Verde";
            case "CX":
                return "Christmas Island (Indian Ocean)";
            case "CY":
                return "Cyprus";
            case "CZ":
                return "Czech Republic";
            case "DE":
                return "Germany, Federal Republic";
            case "DJ":
                return "Djibouti";
            case "DK":
                return "Denmark";
            case "DM":
                return "Dominica";
            case "DO":
                return "Dominican Republic";
            case "DZ":
                return "Algeria";
            case "EC":
                return "Ecuador, Galapagos Islands";
            case "EE":
                return "Estonia";
            case "EG":
                return "Egypt";
            case "EH":
                return "Western Sahara";
            case "ER":
                return "Eritrea";
            case "ES":
                return "Spain, Canary Islands, Tenerife";
            case "ET":
                return "Ethiopia";
            case "FI":
                return "Finland";
            case "FJ":
                return "Fiji";
            case "FK":
                return "Falkland Islands";
            case "FM":
                return "Federated States of Micronesia, Caroline Islands";
            case "FO":
                return "Faroe Islands";
            case "FR":
                return "France, Monaco, Reunion, French Guiana, Guadeloupe, Desirade, Les Saintes, Marie Galante, St.Barth Islands, Martinique";
            case "GA":
                return "Gabon";
            case "GB":
                return "Great Britain, Northern Ireland, British Channel Islands, Isle of Man, United Kingdom";
            case "GD":
                return "Grenada (formerly South Grenadines)";
            case "GE":
                return "Georgian Republic";
            case "GF":
                return "French Guiana";
            case "GH":
                return "Ghana";
            case "GI":
                return "Gibraltar";
            case "GL":
                return "Greenland";
            case "GM":
                return "Gambia";
            case "GN":
                return "Guinea";
            case "GP":
                return "Guadeloupe";
            case "GQ":
                return "Equatorial Guinea, Annobon Island";
            case "GR":
                return "Greece";
            case "GS":
                return "South Georgia and South Sandwich Islands";
            case "GT":
                return "Guatemala";
            case "GU":
                return "Guam";
            case "GW":
                return "Guinea-Bissau (formerly Portuguese Guinea)";
            case "GY":
                return "Guyana";
            case "HM":
                return "Heard and McDonald Islands";
            case "HN":
                return "Honduras, Swan Islands";
            case "HR":
                return "Croatia";
            case "HT":
                return "Haiti";
            case "HU":
                return "Hungary";
            case "ID":
                return "Indonesia, South Borneo";
            case "IE":
                return "Ireland";
            case "IL":
                return "Israel";
            case "IN":
                return "India, Sikkim";
            case "IO":
                return "British Indian Ocean Territories, Chagos Islands";
            case "IQ":
                return "Iraq";
            case "IR":
                return "Iran, Islamic Republic";
            case "IS":
                return "Iceland";
            case "IT":
                return "Italy";
            case "JM":
                return "Jamaica";
            case "JO":
                return "Jordan";
            case "JP":
                return "Japan, Riukiu Islands";
            case "KE":
                return "Kenya";
            case "KG":
                return "Kyrgyzstan";
            case "KH":
                return "Cambodia";
            case "KI":
                return "Kiribati (formerly Gilbert Islands), Christmas Islands (Pacific Ocean)";
            case "KM":
                return "Comoros";
            case "KN":
                return "St. Kitts, Nevis (formerly St.Christoph - Nevis)";
            case "KP":
                return "Korea, Democratic People's Republic (formerly North Korea)";
            case "KR":
                return "Korea, Republic (formerly South Korea)";
            case "KW":
                return "Kuwait";
            case "KY":
                return "Cayman Islands";
            case "KZ":
                return "Kazakhstan";
            case "LA":
                return "Laos, Democratic People's Republic";
            case "LB":
                return "Lebanon";
            case "LC":
                return "St. Lucia";
            case "LI":
                return "Liechtenstein";
            case "LK":
                return "Sri Lanka (formerly Ceylon)";
            case "LR":
                return "Liberia";
            case "LS":
                return "Lesotho";
            case "LT":
                return "Lithuania";
            case "LU":
                return "Luxembourg";
            case "LV":
                return "Latvia";
            case "LY":
                return "Libyan Arab Jamahiriya (formerly Libya)";
            case "MA":
                return "Morocco";
            case "MC":
                return "Monaco";
            case "MD":
                return "Moldova, Republic";
            case "MG":
                return "Madagascar";
            case "MH":
                return "Marshall Islands";
            case "MK":
                return "Macedonia (formerly Republic of Yugoslavia)";
            case "ML":
                return "Mali";
            case "MM":
                return "Myanmar (formerly Burma)";
            case "MN":
                return "Mongolia";
            case "MO":
                return "Macau";
            case "MP":
                return "Northern Mariana Islands";
            case "MQ":
                return "Martinique";
            case "MR":
                return "Mauritania";
            case "MS":
                return "Montserrat";
            case "MT":
                return "Malta";
            case "MU":
                return "Mauritius";
            case "MV":
                return "Maldives";
            case "MW":
                return "Malawi";
            case "MX":
                return "Mexico";
            case "MY":
                return "Malaysia, East Malaysia, Labuan, Sarawak, Sabah, North Borneo";
            case "MZ":
                return "Mozambique";
            case "NA":
                return "Namibia";
            case "NC":
                return "New Caledonia";
            case "NE":
                return "Niger";
            case "NF":
                return "Norfolk Island";
            case "NG":
                return "Nigeria";
            case "NI":
                return "Nicaragua";
            case "NL":
                return "Netherlands";
            case "NO":
                return "Norway, Spitsbergen, Svalbard";
            case "NP":
                return "Nepal";
            case "NR":
                return "Nauru";
            case "NU":
                return "Niue";
            case "NZ":
                return "New Zealand, Campell Island";
            case "OM":
                return "Oman";
            case "PA":
                return "Panama (incl. former channel zone), Cristobal";
            case "PE":
                return "Peru";
            case "PF":
                return "French Polynesia, Society, Sous-le-Vent, Tahiti, Tuamotu, Tubuai Islands";
            case "PG":
                return "Papua New Guinea, Solomon Islands";
            case "PH":
                return "Philippines";
            case "PK":
                return "Pakistan";
            case "PL":
                return "Poland";
            case "PM":
                return "St. Pierre, Miquelon";
            case "PN":
                return "Pitcairn";
            case "PR":
                return "Puerto Rico";
            case "PS":
                return "Occupied Palestinian Territories (formerly Gaza Strip, Jericho, East Jerusalem, West Bank)";
            case "PT":
                return "Portugal, Madeira, Azores";
            case "PW":
                return "Palau";
            case "PY":
                return "Paraguay";
            case "QA":
                return "Qatar";
            case "RE":
                return "Réunion";
            case "HK":
                return "Hong Kong";
            case "RO":
                return "Romania";
            case "RU":
                return "Russia, Russian Federation";
            case "RW":
                return "Rwanda";
            case "SA":
                return "Saudi Arabia";
            case "SB":
                return "Solomon Islands, Santa Cruz Islands, Lord Howe Island (Solomon Islands)";
            case "SC":
                return "Seychelles, Amirante Island";
            case "SD":
                return "Sudan";
            case "SE":
                return "Sweden";
            case "SG":
                return "Singapore";
            case "SH":
                return "St. Helena, Ascension, Gough, Tristan de Cunha";
            case "SI":
                return "Slovenia";
            case "SJ":
                return "Svalbard and Jan Mayen";
            case "SK":
                return "Slovakia";
            case "SL":
                return "Sierra Leone";
            case "SM":
                return "San Marino";
            case "SN":
                return "Senegal";
            case "SO":
                return "Somalia";
            case "SR":
                return "Suriname";
            case "ST":
                return "Sao Tome and Principe";
            case "SV":
                return "El Salvador";
            case "SY":
                return "Syria, Arab Republic";
            case "SZ":
                return "Swaziland";
            case "TC":
                return "Turks and Caicos Islands";
            case "TD":
                return "Chad";
            case "TF":
                return "French Southern Territories";
            case "TG":
                return "Togo";
            case "TH":
                return "Thailand, Siam";
            case "TJ":
                return "Tajikistan";
            case "TK":
                return "Tokelau";
            case "TL":
                return "Timor-Leste";
            case "TM":
                return "Turkmenistan";
            case "TN":
                return "Tunisia";
            case "TO":
                return "Tonga";
            case "TP":
                return "East Timor";
            case "TR":
                return "Turkey";
            case "TT":
                return "Trinidad, Tobago";
            case "TV":
                return "Tuvalu";
            case "TW":
                return "Taiwan, Formosa";
            case "TZ":
                return "Zanzibar, Tanzania, United Republic";
            case "UA":
                return "Ukraine";
            case "UG":
                return "Uganda";
            case "UM":
                return "United States Minor Outlying Islands";
            case "US":
                return "U S A, United States of America, Puerto Rico";
            case "UY":
                return "Uruguay";
            case "UZ":
                return "Uzbekistan";
            case "VA":
                return "Vatican City";
            case "VC":
                return "St. Vincent and North Grenadines";
            case "VE":
                return "Venezuela";
            case "VG":
                return "Virgin Islands (GB)";
            case "VI":
                return "Virgin Islands (USA)";
            case "VN":
                return "Vietnam";
            case "VU":
                return "Vanuatu";
            case "WF":
                return "Wallis, Futuna, Alofi Islands";
            case "WS":
                return "Samoa (formerly Western Samoa)";
            case "XC":
                return "Ceuta";
            case "XL":
                return "Melilla";
            case "YE":
                return "Yemen, Aden";
            case "YT":
                return "Mayotte";
            case "ZA":
                return "South Africa";
            case "ZM":
                return "Zambia";
            case "ZW":
                return "Zimbabwe (formerly Rhodesia)";
            case "--":
                return "(Foreign or unknown country)";
            case "ME":
                return "Montenegro";
            case "XS":
                return "Serbia (old)";
            case "IM":
                return "Isle of Man";
            case "SS":
                return "Südsudan";
            case "GG":
                return "GUERNSEY (C. I.)";
            case "JE":
                return "JERSEY (C. I.)";
            case "PZ":
                return "PANAMA CANAL ZONE";
            case "UW":
                return "UNKNOWN";
            case "YU":
                return "YUGOSLAVIA";
            case "ZR":
                return "ZAIRE";
            case "FW":
                return "Guinea-Bissau";
            case "RS":
                return "Serbia";
            case "DBZ":
                return "TEST COUNTRY DBZ";
            case "IK":
                return "DENMARK";
            case "JA":
                return "JOINT ACCOUNT";
            case "MF":
                return "SAINT MARTIN";
            case "RSA":
                return "REPUBLIC OF SOUTH AFRICA";
            case "XX":
                return "DUMMY";
            case "ZZ":
                return "MIGRATION";
            case "CW":
                return "Curaçao";
            case "SX":
                return "Sint Maarten";
            case "XK":
                return "Kosovo";
            case "AB":
                return "ABKHAZIA";
            case "AK":
                return "AKROTIRI AND DHEKELIA";
            case "AY":
                return "ASCENSION ISLAND";
            case "BK":
                return "BOYS";
            case "CB":
                return "COX ISLANDS";
            case "CS":
                return "SERBIA AND MONTENEGRO";
            case "DD":
                return "DMDefault";
            case "KO":
                return "REPUBLIC OF KOSOVO";
            case "NN":
                return "STATELESS NATIONALITY";
            case "SQ":
                return "SOUTH OSSETIA";
            case "TS":
                return "TRANSNISTRIA";
            case "DS":
                return "GERMANY_ISO";
            case "DR":
                return "REPUBLIC OF CONGO";
            default:
                return countryCode;
        }
    }

    private static void addHistoryRow(Table table, String label, String value, PdfFont font) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(font)).setFontSize(9.0f).setBorder(new SolidBorder(0.5f)));
        table.addCell(new Cell().add(new Paragraph(value).setFont(font)).setFontSize(9.0f).setBorder(new SolidBorder(0.5f)));
    }

    public static String extractPersonId(JsonNode jsonData) {
        return Optional.ofNullable(jsonData)
                .map(node -> node.path("kycScoreResponse").path("body").path("return").path("personResult").path("personID"))
                .filter(node -> !node.isMissingNode() && !node.isNull())
                .map(JsonNode::asText)
                .orElse("N/A");
    }

    private static String extractRisk(JsonNode jsonNode) {
        if (jsonNode == null) return "N/A";
        String machineRisk = jsonNode.path("kycScoreResponse").path("body").path("return").path("resultData").path("maschineRisk").asText("N/A");
        String finalRisk = jsonNode.path("kycScoreResponse").path("body").path("return").path("resultData").path("kycRisk").path("finalRisk").asText("N/A");
        return "**" + machineRisk + "** / **" + finalRisk + "** (Manually / Final)";
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

    public static void main(String[] args) {
        try {
            String jsonContent = readJsonFromFile("package.json");

            PDFResult pdfResult = generatePDF("John Doe", jsonContent);

            String fileName = pdfResult.getFileName();
            savePdfToFile(pdfResult.getPdfBytes(), fileName);

            System.out.println("PDF saved at: " + fileName);

            // optional decode preview
            Scanner input = new Scanner(System.in);
            System.out.println("Open PDF? (Y/N)");
            if (input.next().equalsIgnoreCase("Y")) {
                java.awt.Desktop.getDesktop().open(new File(fileName));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }}