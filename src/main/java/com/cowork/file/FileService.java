package com.cowork.file;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.common.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileItemRepository fileItemRepository;
    private final FileLogRepository fileLogRepository;
    private final FileStorageService storageService;

    public List<FileItem> getFiles(Long cohortId, Long parentId, String department) {
        return fileItemRepository.findFiltered(cohortId, parentId, department);
    }

    public FileItem getFile(Long id) {
        return fileItemRepository.findById(id)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    public List<FileLog> getLogs(Long fileItemId) {
        return fileLogRepository.findByFileItemIdOrderByCreatedAtDesc(fileItemId);
    }

    @Transactional
    public FileItem createFolder(Long cohortId, String name, Long parentId, String department, Long eventId) {
        FileItem folder = FileItem.builder()
                .cohortId(cohortId)
                .name(name)
                .type(FileType.FOLDER)
                .parentId(parentId)
                .department(department)
                .eventId(eventId)
                .build();
        return fileItemRepository.save(folder);
    }

    @Transactional
    public FileItem uploadFile(Long cohortId, MultipartFile file, Long parentId,
                               String department, Long eventId, String uploadedBy, Long actorId) {
        String storagePath = storageService.store(file, "files", cohortId);
        FileItem fileItem = FileItem.builder()
                .cohortId(cohortId)
                .name(file.getOriginalFilename())
                .type(FileType.FILE)
                .mimeType(file.getContentType())
                .size(file.getSize())
                .parentId(parentId)
                .storagePath(storagePath)
                .department(department)
                .eventId(eventId)
                .uploadedBy(uploadedBy)
                .build();
        fileItemRepository.save(fileItem);

        logAction(fileItem.getId(), FileLogAction.UPLOAD, actorId, uploadedBy, Map.of("name", file.getOriginalFilename()));
        return fileItem;
    }

    @Transactional
    public FileItem updateFile(Long id, String newName, String department, boolean updateDepartment,
                               Long eventId, boolean updateEvent, Long actorId, String actorName) {
        FileItem file = getFile(id);
        if (newName != null && !newName.equals(file.getName())) {
            String oldName = file.getName();
            file.rename(newName);
            logAction(id, FileLogAction.RENAME, actorId, actorName, Map.of("from", oldName, "to", newName));
        }
        if (updateDepartment && !Objects.equals(department, file.getDepartment())) {
            file.updateDepartment(department);
            logAction(id, FileLogAction.UPDATE, actorId, actorName, Map.of("department", String.valueOf(department)));
        }
        if (updateEvent && !Objects.equals(eventId, file.getEventId())) {
            file.updateEventId(eventId);
            logAction(id, FileLogAction.UPDATE, actorId, actorName, Map.of("eventId", String.valueOf(eventId)));
        }
        return file;
    }

    @Transactional
    public FileItem moveFile(Long id, Long newParentId, Long actorId, String actorName) {
        FileItem file = getFile(id);
        Long oldParentId = file.getParentId();
        file.move(newParentId);
        logAction(id, FileLogAction.MOVE, actorId, actorName,
                Map.of("fromParentId", String.valueOf(oldParentId), "toParentId", String.valueOf(newParentId)));
        return file;
    }

    @Transactional
    public void deleteFile(Long id, Long actorId, String actorName) {
        FileItem file = getFile(id);
        logAction(id, FileLogAction.DELETE, actorId, actorName, Map.of("name", file.getName()));
        file.softDelete();
        if (file.getStoragePath() != null) {
            storageService.delete(file.getStoragePath());
        }
    }

    public Resource loadForDownload(Long id) {
        FileItem file = getFile(id);
        if (file.getStoragePath() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return storageService.loadAsResource(file.getStoragePath());
    }

    private void logAction(Long fileItemId, FileLogAction action, Long actorId, String actorName,
                           Map<String, Object> detail) {
        FileLog log = FileLog.builder()
                .fileItemId(fileItemId)
                .action(action)
                .actorId(actorId)
                .actorName(actorName)
                .detail(detail)
                .build();
        fileLogRepository.save(log);
    }
}
