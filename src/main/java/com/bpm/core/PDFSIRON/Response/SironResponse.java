package com.bpm.core.PDFSIRON.Response;

public class SironResponse {
    private String base64Content;
    private String fileName;

    public SironResponse(String base64Content, String fileName) {
        this.base64Content = base64Content;
        this.fileName = fileName;
    }

    public String getBase64Content() { return base64Content; }
    public String getFileName() { return fileName; }
}