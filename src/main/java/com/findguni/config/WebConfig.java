package com.findguni.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final Path uploadRoot;

    public WebConfig(@Value("${findguni.upload-dir:./uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리를 준비하지 못했습니다.", e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(ensureTrailingSlash(uploadRoot.toUri().toString()))
                .setCachePeriod(86_400);
    }

    private String ensureTrailingSlash(String uri) { return uri.endsWith("/") ? uri : uri + "/"; }
}
