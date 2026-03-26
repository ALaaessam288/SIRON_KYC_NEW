package com.bpm.core.PDFSIRON.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public  class PdfResponse {
    private String base64;

    private String fileName;

    // Getters and setters
    public String getBase64() { return base64; }
    public void setBase64(String base64) { this.base64 = base64; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
