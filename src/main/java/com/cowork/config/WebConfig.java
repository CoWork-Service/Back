package com.cowork.config;

import com.cowork.event.EventStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.storage.base-path:./uploads}")
    private String basePath;

    @Value("${file.storage.type:local}")
    private String storageType;

    @Value("${CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:5173,https://d3enhw6vmzgeun.cloudfront.net}")
    private String corsAllowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(Arrays.stream(corsAllowedOriginPatterns.split(","))
                        .map(String::trim)
                        .filter(pattern -> !pattern.isEmpty())
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, EventStatus.class, EventStatus::from);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!"local".equalsIgnoreCase(storageType)) {
            return;
        }
        String absolutePath = Paths.get(basePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absolutePath);
    }
}
