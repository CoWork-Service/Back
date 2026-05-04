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

/**
 * 파일 관리 컨트롤러 (FileController)
 *
 * 역할:
 *   코호트의 파일과 폴더를 트리 구조로 관리하는 API 를 제공한다.
 *   업로드·다운로드·이름변경·이동·삭제를 지원하며, 모든 변경 작업은 FileLog 에 기록된다.
 *   기본 경로: /api/files
 *
 * 트리 구조:
 *   - parentId = null : 루트 항목
 *   - parentId = N   : N번 폴더의 하위 항목
 *   - GET /api/files?cohortId=&parentId= 로 특정 폴더의 자식 목록 조회
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 *            (다운로드는 공개 접근 허용으로 변경 가능)
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    /**
     * 파일/폴더 목록 조회
     *
     * 동작: cohortId + parentId 조합으로 특정 폴더의 하위 항목(파일·폴더)을 반환.
     *       parentId 가 없으면 루트 항목 목록 반환.
     *       부서(department) 필터로 특정 부서 파일만 조회 가능.
     * 사용 시점: 파일 탐색기 화면에서 현재 폴더의 내용을 불러올 때.
     *
     * @param cohortId   필수. 조회할 코호트 ID
     * @param parentId   선택. 부모 폴더 ID (null 이면 루트)
     * @param department 선택. 부서 필터
     * @return 파일/폴더 항목 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FileItemResponse>>> getFiles(
            @RequestParam Long cohortId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Department department) {
        List<FileItemResponse> list = fileService.getFiles(cohortId, parentId, department)
                .stream().map(FileItemResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 폴더 생성
     *
     * 동작: 지정한 위치에 새 폴더를 생성한다. 실제 스토리지에는 생성하지 않고 DB 레코드만 추가.
     * 사용 시점: 파일 탐색기에서 "새 폴더" 버튼 클릭 시.
     *
     * @param req { cohortId, name, parentId, department }
     * @return 생성된 폴더 정보
     */
    @PostMapping("/folder")
    public ResponseEntity<ApiResponse<FileItemResponse>> createFolder(
            @RequestBody FolderCreateRequest req) {
        FileItem folder = fileService.createFolder(req.getCohortId(), req.getName(),
                req.getParentId(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(folder)));
    }

    /**
     * 파일 업로드
     *
     * 동작:
     *   1. MultipartFile 을 스토리지에 저장.
     *   2. FileItem 레코드 생성 (name, mimeType, size, storagePath, uploadedBy 저장).
     *   3. FileLog(UPLOAD) 레코드 생성.
     * 사용 시점: 파일 탐색기에서 파일을 드래그하거나 선택하여 업로드할 때.
     *
     * Content-Type: multipart/form-data
     * @param file        업로드할 파일
     * @param cohortId    필수. 소속 코호트 ID
     * @param parentId    선택. 업로드 위치 폴더 ID (null 이면 루트)
     * @param department  선택. 담당 부서
     * @param userDetails 현재 로그인 사용자 (업로더)
     * @return 생성된 파일 아이템 정보
     */
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

    /**
     * 파일/폴더 상세 조회 (변경 이력 포함)
     *
     * 동작: 파일/폴더 기본 정보와 FileLog 목록(변경 이력)을 함께 반환.
     * 사용 시점: 파일 상세 패널에서 정보와 활동 이력을 확인할 때.
     *
     * @param id 파일/폴더 ID
     * @return 파일 정보 + 변경 이력 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(@PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        List<FileLog> logs = fileService.getLogs(id);
        return ResponseEntity.ok(ApiResponse.ok(FileDetailResponse.of(file, logs)));
    }

    /**
     * 파일/폴더 이름 변경 (+ 부서 변경)
     *
     * 동작: 파일/폴더의 이름과 부서를 변경하고 FileLog(RENAME) 레코드를 생성한다.
     * 사용 시점: 파일/폴더 이름을 수정하거나 부서 분류를 변경할 때.
     *
     * @param id          파일/폴더 ID
     * @param body        { "name": "새이름.pdf", "department": "PLANNING" }
     * @param userDetails 현재 로그인 사용자 (변경자, 로그에 기록)
     * @return 수정된 파일 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FileItemResponse>> renameFile(
            @PathVariable Long id,
            @RequestBody FileUpdateRequest body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.updateFile(id, body.getName(), body.getDepartment(), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    /**
     * 파일/폴더 이동
     *
     * 동작: 파일/폴더의 parentId 를 변경하여 다른 폴더로 이동하고 FileLog(MOVE) 를 생성한다.
     * 사용 시점: 파일을 다른 폴더로 드래그하거나 "이동" 기능을 사용할 때.
     *
     * @param id          이동할 파일/폴더 ID
     * @param body        { "parentId": 7 } (null 이면 루트로 이동)
     * @param userDetails 현재 로그인 사용자 (이동자, 로그에 기록)
     * @return 이동된 파일 정보
     */
    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FileItemResponse>> moveFile(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.moveFile(id, body.get("parentId"), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    /**
     * 파일/폴더 삭제
     *
     * 동작: 파일인 경우 스토리지 파일도 함께 삭제하고 FileLog(DELETE) 를 생성한다.
     *       폴더인 경우 하위 항목이 있으면 예외 발생 가능 (서비스 레이어에서 검증).
     * 사용 시점: 더 이상 필요없는 파일이나 폴더를 제거할 때.
     *
     * @param id          삭제할 파일/폴더 ID
     * @param userDetails 현재 로그인 사용자 (삭제자, 로그에 기록)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        fileService.deleteFile(id, user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 파일 다운로드
     *
     * 동작: 스토리지에서 파일을 읽어 Content-Disposition: attachment 헤더와 함께 바이너리 응답.
     *       브라우저에서 파일 다운로드 창이 열린다.
     * 사용 시점: 파일 탐색기에서 "다운로드" 버튼 클릭 시.
     *
     * @param id 다운로드할 파일 ID
     * @return 파일 바이너리 (Resource)
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        Resource resource = fileService.loadForDownload(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST /folder 요청 바디 */
    @Getter
    static class FolderCreateRequest {
        private Long cohortId;
        private String name;
        private Long parentId;
        private Department department;
    }

    /** PUT /{id} 요청 바디 */
    @Getter
    static class FileUpdateRequest {
        private String name;
        private Department department;
    }

    /** 파일/폴더 항목 응답 DTO */
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

    /** 파일 상세 응답 DTO (파일 정보 + 변경 이력) */
    record FileDetailResponse(FileItemResponse file, List<FileLogResponse> logs) {
        static FileDetailResponse of(FileItem f, List<FileLog> logs) {
            return new FileDetailResponse(FileItemResponse.of(f),
                    logs.stream().map(FileLogResponse::of).collect(Collectors.toList()));
        }
    }

    /** 파일 변경 이력 응답 DTO */
    record FileLogResponse(Long id, String action, String actorName, Map<String, Object> detail,
                           LocalDateTime createdAt) {
        static FileLogResponse of(FileLog l) {
            return new FileLogResponse(l.getId(), l.getAction().name(), l.getActorName(),
                    l.getDetail(), l.getCreatedAt());
        }
    }
}
