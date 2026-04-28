package com.cowork.file;

import com.cowork.cohort.Department;
import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileItemResponse>>> getFiles(
            @RequestParam Long cohortId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Department department) {
        List<FileItemResponse> list = fileService.getFiles(cohortId, parentId, department)
                .stream().map(FileItemResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/folder")
    public ResponseEntity<ApiResponse<FileItemResponse>> createFolder(
            @RequestBody FolderCreateRequest req) {
        FileItem folder = fileService.createFolder(req.getCohortId(), req.getName(),
                req.getParentId(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(folder)));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileItemResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long cohortId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Department department,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem fileItem = fileService.uploadFile(cohortId, file, parentId, department,
                user.getName(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(fileItem)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(@PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        List<FileLog> logs = fileService.getLogs(id);
        return ResponseEntity.ok(ApiResponse.ok(FileDetailResponse.of(file, logs)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FileItemResponse>> renameFile(
            @PathVariable Long id,
            @RequestBody FileUpdateRequest body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.updateFile(id, body.getName(), body.getDepartment(), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FileItemResponse>> moveFile(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.moveFile(id, body.get("parentId"), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        fileService.deleteFile(id, user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        Resource resource = fileService.loadForDownload(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @Getter
    static class FolderCreateRequest {
        private Long cohortId;
        private String name;
        private Long parentId;
        private Department department;
    }

    @Getter
    static class FileUpdateRequest {
        private String name;
        private Department department;
    }

    record FileItemResponse(Long id, Long cohortId, String name, String type, String mimeType,
                            Long size, Long parentId, String department, String uploadedBy,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        static FileItemResponse of(FileItem f) {
            return new FileItemResponse(f.getId(), f.getCohortId(), f.getName(), f.getType().name(),
                    f.getMimeType(), f.getSize(), f.getParentId(),
                    f.getDepartment() != null ? f.getDepartment().name() : null,
                    f.getUploadedBy(), f.getCreatedAt(), f.getUpdatedAt());
        }
    }

    record FileDetailResponse(FileItemResponse file, List<FileLogResponse> logs) {
        static FileDetailResponse of(FileItem f, List<FileLog> logs) {
            return new FileDetailResponse(FileItemResponse.of(f),
                    logs.stream().map(FileLogResponse::of).collect(Collectors.toList()));
        }
    }

    record FileLogResponse(Long id, String action, String actorName, Map<String, Object> detail,
                           LocalDateTime createdAt) {
        static FileLogResponse of(FileLog l) {
            return new FileLogResponse(l.getId(), l.getAction().name(), l.getActorName(),
                    l.getDetail(), l.getCreatedAt());
        }
    }
}
