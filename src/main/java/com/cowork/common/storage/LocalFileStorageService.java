package com.cowork.common.storage;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path basePath;
    private final StoredFileRepository storedFileRepository;

    public LocalFileStorageService(@Value("${file.storage.base-path:./uploads}") String basePath,
                                   StoredFileRepository storedFileRepository) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.storedFileRepository = storedFileRepository;
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 디렉토리를 생성할 수 없습니다: " + basePath, e);
        }
    }

    @Override
    public String store(MultipartFile file, String module, Long cohortId) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String storedName = UUID.randomUUID() + extension;
        String relativePath = module + "/" + cohortId + "/" + storedName;

        try {
            Path targetDir = this.basePath.resolve(module + "/" + cohortId);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(storedName);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            storedFileRepository.save(StoredFile.builder()
                    .storageType("local")
                    .objectKey(relativePath)
                    .storagePath(relativePath)
                    .module(module)
                    .cohortId(cohortId)
                    .originalName(originalFilename)
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .build());
            return relativePath;
        } catch (IOException e) {
            log.error("Failed to store file: {}", relativePath, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        try {
            Path file = this.basePath.resolve(storagePath).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Override
    public String getContentType(String storagePath) {
        return storedFileRepository.findByStoragePathAndDeletedAtIsNull(storagePath)
                .map(StoredFile::getContentType)
                .filter(StringUtils::hasText)
                .orElseGet(() -> probeContentType(storagePath));
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path file = this.basePath.resolve(storagePath).normalize();
            Files.deleteIfExists(file);
            storedFileRepository.findByStoragePathAndDeletedAtIsNull(storagePath)
                    .ifPresent(StoredFile::softDelete);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", storagePath, e);
        }
    }

    private String probeContentType(String storagePath) {
        try {
            String contentType = Files.probeContentType(this.basePath.resolve(storagePath).normalize());
            return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
