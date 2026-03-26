package com.bpm.core.PDFSIRON;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

public class PDFResult {
    private String fileName;
    private byte[] pdfBytes;

    public PDFResult(String fileName, byte[] pdfBytes) {
        this.fileName = fileName;
        this.pdfBytes = pdfBytes;
    }

    public String getFileName() {
        return this.fileName;
    }

    public byte[] getPdfBytes() {
        return this.pdfBytes;
    }
}
