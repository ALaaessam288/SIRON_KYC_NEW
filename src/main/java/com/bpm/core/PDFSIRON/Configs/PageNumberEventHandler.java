package com.bpm.core.PDFSIRON.Configs;

import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

public class PageNumberEventHandler implements IEventHandler {
    private final Document document;

    public PageNumberEventHandler(Document document) {
        this.document = document;
    }

    public void handleEvent(Event event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent)event;
        PdfDocument pdfDoc = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        int pageNumber = pdfDoc.getPageNumber(page);
        Rectangle pageSize = page.getPageSize();
        PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
        Canvas canvas = new Canvas(pdfCanvas, pdfDoc, pageSize);
        canvas.setFontSize(10.0F);
        String pageText = String.format("Page %d", pageNumber);
        canvas.showTextAligned(pageText, pageSize.getWidth() / 2.0F, pageSize.getBottom() + 20.0F, TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0.0F);
        String textBottom = "First Abu Dhabi Bank Misr";
        canvas.showTextAligned(textBottom, 30.0F, pageSize.getBottom() + 20.0F, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0.0F);
        canvas.close();
    }
}
