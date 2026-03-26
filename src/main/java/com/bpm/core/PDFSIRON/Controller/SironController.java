package com.bpm.core.PDFSIRON.Controller;

import com.bpm.core.PDFSIRON.PDFGenerator;
import com.bpm.core.PDFSIRON.Request.SironRequest;
import com.bpm.core.PDFSIRON.Response.SironResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/siron/v2/siron-pdf/api/pdf")
public class SironController {

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SironResponse> generatePdf(@RequestBody SironRequest request) {
        try {
            String fullName = request.getFullName();
            String jsonContent = request.getJsonContent();

            if (fullName == null || fullName.trim().isEmpty() || jsonContent == null || jsonContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new SironResponse(null, "Missing 'fullName' or 'jsonContent'")
                );
            }

            // Assume this returns Map<String, String> with keys "base64" and "fileName"
            Map<String, String> result = PDFGenerator.generatePDFAsBase64(fullName, jsonContent);

            SironResponse response = new SironResponse(
                    result.get("base64Content"),
                    result.get("fileName")
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new SironResponse(null, "Error generating PDF: " + e.getMessage())
            );
        }
    }
}
