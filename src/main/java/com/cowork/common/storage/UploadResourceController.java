package com.cowork.common.storage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class UploadResourceController {

    private static final String PREFIX = "/uploads/";

    private final FileStorageService storageService;

    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> getUpload(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        int prefixIndex = requestUri.indexOf(PREFIX);
        String storagePath = prefixIndex >= 0
                ? requestUri.substring(prefixIndex + PREFIX.length())
                : "";
        storagePath = UriUtils.decode(storagePath, StandardCharsets.UTF_8);

        Resource resource = storageService.loadAsResource(storagePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storageService.getContentType(storagePath)))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(resource);
    }
}
