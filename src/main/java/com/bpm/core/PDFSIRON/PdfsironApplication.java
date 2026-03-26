package com.bpm.core.PDFSIRON;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class PdfsironApplication extends SpringBootServletInitializer {

    public PdfsironApplication() {
    }

    public static void main(String[] args) {

        SpringApplication.run(PdfsironApplication.class, args);

    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.bannerMode(Banner.Mode.CONSOLE).sources(PdfsironApplication.class);
    }

//    @Bean
//    public ServletWebServerFactory servletContainer() {
//        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
//        tomcat.addContextCustomizers(context -> {
//            // Fix for JSP compilation in WAR deployment
//            context.addParameter("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
//        })
//        return tomcat;
//    }
}