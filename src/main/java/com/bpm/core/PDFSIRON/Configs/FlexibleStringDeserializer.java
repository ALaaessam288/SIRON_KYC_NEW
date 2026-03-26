package com.bpm.core.PDFSIRON.Configs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class FlexibleStringDeserializer extends StdDeserializer<String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    public FlexibleStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        // If BPM sends it as a plain string → return as-is
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        // If Postman sends it as a raw JSON object → serialize it back to String
        return mapper.writeValueAsString(p.readValueAsTree());
    }
}