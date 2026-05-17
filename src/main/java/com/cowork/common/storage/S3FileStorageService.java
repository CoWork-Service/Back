package com.cowork.common.storage;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private final String bucket;
    private final String keyPrefix;
    private final S3Client s3Client;
    private final StoredFileRepository storedFileRepository;

    public S3FileStorageService(
            @Value("${file.storage.s3.bucket}") String bucket,
            @Value("${file.storage.s3.region:ap-northeast-2}") String region,
            @Value("${file.storage.s3.key-prefix:}") String keyPrefix,
            StoredFileRepository storedFileRepository) {
        this.bucket = bucket;
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.storedFileRepository = storedFileRepository;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
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
        String storagePath = module + "/" + cohortId + "/" + storedName;
        String objectKey = objectKey(storagePath);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            storedFileRepository.save(StoredFile.builder()
                    .storageType("s3")
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .storagePath(storagePath)
                    .module(module)
                    .cohortId(cohortId)
                    .originalName(originalFilename)
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .build());
            return storagePath;
        } catch (IOException e) {
            log.error("Failed to read multipart file for S3 upload: {}", storagePath, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        } catch (RuntimeException e) {
            log.error("Failed to upload file to S3: bucket={}, key={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        try {
            String objectKey = storedFileRepository.findByStoragePathAndDeletedAtIsNull(storagePath)
                    .map(StoredFile::getObjectKey)
                    .orElseGet(() -> objectKey(storagePath));
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return new InputStreamResource(object);
        } catch (RuntimeException e) {
            log.warn("Failed to load S3 object: bucket={}, storagePath={}", bucket, storagePath, e);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Override
    public String getContentType(String storagePath) {
        return storedFileRepository.findByStoragePathAndDeletedAtIsNull(storagePath)
                .map(StoredFile::getContentType)
                .filter(StringUtils::hasText)
                .orElse("application/octet-stream");
    }

    @Override
    public void delete(String storagePath) {
        storedFileRepository.findByStoragePathAndDeletedAtIsNull(storagePath)
                .ifPresent(storedFile -> {
                    try {
                        s3Client.deleteObject(builder -> builder.bucket(bucket).key(storedFile.getObjectKey()));
                    } catch (RuntimeException e) {
                        log.warn("Failed to delete S3 object: bucket={}, key={}", bucket, storedFile.getObjectKey(), e);
                    }
                    storedFile.softDelete();
                });
    }

    @PreDestroy
    public void close() {
        s3Client.close();
    }

    private String objectKey(String storagePath) {
        return keyPrefix + storagePath;
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String normalized = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
