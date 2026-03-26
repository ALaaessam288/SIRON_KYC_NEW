package com.bpm.core.PDFSIRON.Configs;

import com.bpm.core.PDFSIRON.PdfsironApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(PdfsironApplication.class);
    }
}