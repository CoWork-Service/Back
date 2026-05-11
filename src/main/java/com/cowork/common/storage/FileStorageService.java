package com.cowork.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * 파일을 저장하고 storagePath (상대경로)를 반환한다.
     * 저장 경로 패턴: {module}/{cohortId}/{uuid}.{ext}
     */
    String store(MultipartFile file, String module, Long cohortId);

    /**
     * storagePath로 Resource를 로드한다.
     */
    Resource loadAsResource(String storagePath);

    /**
     * 저장된 파일의 Content-Type을 반환한다.
     */
    String getContentType(String storagePath);

    /**
     * 파일을 삭제한다.
     */
    void delete(String storagePath);
}
