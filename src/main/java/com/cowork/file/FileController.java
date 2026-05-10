package com.cowork.file;

import com.cowork.cohort.Department;
import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "File", description = "파일 관리 API — 파일/폴더 CRUD, 업로드/다운로드, 이동, 변경 이력 조회")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @Operation(
            summary = "파일/폴더 목록 조회",
            description = """
                    특정 폴더의 하위 파일과 폴더 목록을 조회합니다.

                    **사용 시점:** 파일 탐색기 화면에서 현재 폴더의 내용을 불러올 때.

                    **트리 구조 탐색:**
                    - `parentId` 없음 → 루트 항목 목록
                    - `parentId = N` → N번 폴더의 하위 항목 목록

                    **type 필드 값:** `FILE` / `FOLDER`

                    **부서(department) 필터:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "name": "기획팀",
                                          "type": "FOLDER",
                                          "mimeType": null,
                                          "size": null,
                                          "parentId": null,
                                          "department": "PLANNING",
                                          "uploadedBy": "홍길동",
                                          "createdAt": "2025-01-10T09:00:00",
                                          "updatedAt": "2025-05-01T14:00:00"
                                        },
                                        {
                                          "id": 2,
                                          "cohortId": 5,
                                          "name": "MT_계획서.pdf",
                                          "type": "FILE",
                                          "mimeType": "application/pdf",
                                          "size": 204800,
                                          "parentId": 1,
                                          "department": "PLANNING",
                                          "uploadedBy": "홍길동",
                                          "createdAt": "2025-02-15T11:30:00",
                                          "updatedAt": "2025-02-15T11:30:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<FileItemResponse>>> getFiles(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "부모 폴더 ID (null이면 루트 조회)", example = "1") @RequestParam(required = false) Long parentId,
            @Parameter(description = "부서 필터 (PLANNING / MARKETING / OPERATION / FINANCE / GENERAL)") @RequestParam(required = false) Department department) {
        List<FileItemResponse> list = fileService.getFiles(cohortId, parentId, department)
                .stream().map(FileItemResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "폴더 생성",
            description = """
                    지정한 위치에 새 폴더를 생성합니다. 실제 스토리지에는 생성되지 않고 DB 레코드만 추가됩니다.

                    **사용 시점:** 파일 탐색기에서 "새 폴더" 버튼 클릭 시.

                    `parentId`를 지정하면 해당 폴더의 하위에, 생략하면 루트에 생성됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "폴더 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 10,
                                        "cohortId": 5,
                                        "name": "2025년 1학기",
                                        "type": "FOLDER",
                                        "mimeType": null,
                                        "size": null,
                                        "parentId": 1,
                                        "department": "PLANNING",
                                        "uploadedBy": null,
                                        "createdAt": "2025-05-10T10:00:00",
                                        "updatedAt": "2025-05-10T10:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping("/folder")
    public ResponseEntity<ApiResponse<FileItemResponse>> createFolder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "폴더 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "name": "2025년 1학기",
                              "parentId": 1,
                              "department": "PLANNING"
                            }
                            """)))
            @RequestBody FolderCreateRequest req) {
        FileItem folder = fileService.createFolder(req.getCohortId(), req.getName(),
                req.getParentId(), req.getDepartment());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(folder)));
    }

    @Operation(
            summary = "파일 업로드",
            description = """
                    파일을 업로드합니다.

                    **사용 시점:** 파일 탐색기에서 파일을 드래그하거나 선택하여 업로드할 때.

                    **요청 형식:** `multipart/form-data`

                    **처리 순서:**
                    1. 파일을 스토리지에 저장
                    2. FileItem 레코드 생성 (name, mimeType, size, storagePath, uploadedBy 저장)
                    3. FileLog(UPLOAD) 레코드 생성

                    `parentId`를 지정하면 해당 폴더에, 생략하면 루트에 업로드됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 업로드 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 15,
                                        "cohortId": 5,
                                        "name": "발표자료.pptx",
                                        "type": "FILE",
                                        "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                        "size": 2097152,
                                        "parentId": 10,
                                        "department": "PLANNING",
                                        "uploadedBy": "홍길동",
                                        "createdAt": "2025-05-10T11:00:00",
                                        "updatedAt": "2025-05-10T11:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileItemResponse>> uploadFile(
            @Parameter(description = "업로드할 파일", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "코호트 ID", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "업로드 위치 폴더 ID (null이면 루트)", example = "10") @RequestParam(required = false) Long parentId,
            @Parameter(description = "담당 부서") @RequestParam(required = false) Department department,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem fileItem = fileService.uploadFile(cohortId, file, parentId, department,
                user.getName(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(fileItem)));
    }

    @Operation(
            summary = "파일/폴더 상세 조회 (변경 이력 포함)",
            description = """
                    파일/폴더 기본 정보와 변경 이력(FileLog)을 함께 반환합니다.

                    **사용 시점:** 파일 상세 패널에서 정보와 활동 이력을 확인할 때.

                    **변경 이력 action 값:** UPLOAD / RENAME / MOVE / DELETE
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "file": {
                                          "id": 15,
                                          "cohortId": 5,
                                          "name": "발표자료.pptx",
                                          "type": "FILE",
                                          "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                          "size": 2097152,
                                          "parentId": 10,
                                          "department": "PLANNING",
                                          "uploadedBy": "홍길동",
                                          "createdAt": "2025-05-10T11:00:00",
                                          "updatedAt": "2025-05-10T11:30:00"
                                        },
                                        "logs": [
                                          {
                                            "id": 1,
                                            "action": "UPLOAD",
                                            "actorName": "홍길동",
                                            "detail": { "originalName": "발표자료.pptx" },
                                            "createdAt": "2025-05-10T11:00:00"
                                          },
                                          {
                                            "id": 2,
                                            "action": "RENAME",
                                            "actorName": "홍길동",
                                            "detail": { "from": "발표자료_v1.pptx", "to": "발표자료.pptx" },
                                            "createdAt": "2025-05-10T11:30:00"
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(
            @Parameter(description = "파일/폴더 ID", required = true, example = "15") @PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        List<FileLog> logs = fileService.getLogs(id);
        return ResponseEntity.ok(ApiResponse.ok(FileDetailResponse.of(file, logs)));
    }

    @Operation(
            summary = "파일/폴더 이름 변경 (+ 부서 변경)",
            description = """
                    파일/폴더의 이름과 부서를 변경합니다. 변경 이력(FileLog RENAME)이 자동으로 기록됩니다.

                    **사용 시점:** 파일/폴더 이름을 수정하거나 부서 분류를 변경할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이름 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 15,
                                        "cohortId": 5,
                                        "name": "최종_발표자료.pptx",
                                        "type": "FILE",
                                        "mimeType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                        "size": 2097152,
                                        "parentId": 10,
                                        "department": "MARKETING",
                                        "uploadedBy": "홍길동",
                                        "createdAt": "2025-05-10T11:00:00",
                                        "updatedAt": "2025-05-10T14:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FileItemResponse>> renameFile(
            @Parameter(description = "파일/폴더 ID", required = true, example = "15") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "이름/부서 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "name": "최종_발표자료.pptx", "department": "MARKETING" }
                            """)))
            @RequestBody FileUpdateRequest body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.updateFile(id, body.getName(), body.getDepartment(), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    @Operation(
            summary = "파일/폴더 이동",
            description = """
                    파일/폴더를 다른 폴더로 이동합니다. 이동 이력(FileLog MOVE)이 자동으로 기록됩니다.

                    **사용 시점:** 파일을 다른 폴더로 드래그하거나 "이동" 기능을 사용할 때.

                    `parentId`를 `null`로 보내면 루트로 이동합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 이동 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 15,
                                        "cohortId": 5,
                                        "name": "최종_발표자료.pptx",
                                        "type": "FILE",
                                        "parentId": 20,
                                        "department": "MARKETING",
                                        "uploadedBy": "홍길동",
                                        "createdAt": "2025-05-10T11:00:00",
                                        "updatedAt": "2025-05-10T15:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FileItemResponse>> moveFile(
            @Parameter(description = "이동할 파일/폴더 ID", required = true, example = "15") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "이동 요청 (null이면 루트로 이동)",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "parentId": 20 }
                            """)))
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        FileItem file = fileService.moveFile(id, body.get("parentId"), user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok(FileItemResponse.of(file)));
    }

    @Operation(
            summary = "파일/폴더 삭제",
            description = """
                    파일/폴더를 삭제합니다. 삭제 이력(FileLog DELETE)이 자동으로 기록됩니다.

                    **사용 시점:** 더 이상 필요없는 파일이나 폴더를 제거할 때.

                    **주의:** 파일인 경우 스토리지에서도 실제 파일이 삭제됩니다.
                    폴더인 경우 하위 항목이 있으면 예외가 발생할 수 있습니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @Parameter(description = "삭제할 파일/폴더 ID", required = true, example = "15") @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
        fileService.deleteFile(id, user.getId(), user.getName());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "파일 다운로드",
            description = """
                    파일을 다운로드합니다.

                    **사용 시점:** 파일 탐색기에서 "다운로드" 버튼 클릭 시.

                    응답은 `Content-Disposition: attachment` 헤더와 함께 바이너리 스트림으로 반환됩니다.
                    브라우저에서 파일 다운로드 창이 열립니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 다운로드 성공 (바이너리 응답)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "다운로드할 파일 ID", required = true, example = "15") @PathVariable Long id) {
        FileItem file = fileService.getFile(id);
        Resource resource = fileService.loadForDownload(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

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
