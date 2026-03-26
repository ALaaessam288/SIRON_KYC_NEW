package com.bpm.core.PDFSIRON.Request;

import com.bpm.core.PDFSIRON.Configs.FlexibleStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SironRequest {
    String fullName ;
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    String jsonContent;

}
