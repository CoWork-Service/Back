package com.cowork.asset;

import com.cowork.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Asset", description = "자산 관리 API — 물품 CRUD, 대여/반납 처리")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(
            summary = "자산 목록 조회",
            description = """
                    코호트에 등록된 자산(물품) 목록을 조회합니다.

                    **사용 시점:** 자산 관리 화면에서 목록 표시 및 필터 적용 시.

                    **필터 옵션:**
                    - `status`: `AVAILABLE`(대여가능) / `RENTED`(대여중) / `MAINTENANCE`(수리중) / `DISPOSED`(폐기)
                    - `category`: "전자기기", "도서" 등 자유 문자열
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자산 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "name": "맥북 프로 14인치",
                                          "category": "전자기기",
                                          "tags": ["노트북", "Apple"],
                                          "photoStoragePath": "assets/macbook.jpg",
                                          "quantity": 2,
                                          "availableQuantity": 1,
                                          "purchasePrice": 2500000,
                                          "location": "동아리방 캐비넷 A",
                                          "status": "RENTED",
                                          "description": "M3 Pro 칩셋, 공용 노트북",
                                          "createdAt": "2025-03-01T10:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAssets(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5")
            @RequestParam Long cohortId,
            @Parameter(description = "자산 상태 필터 (AVAILABLE / RENTED / MAINTENANCE / DISPOSED)", example = "AVAILABLE")
            @RequestParam(required = false) AssetStatus status,
            @Parameter(description = "분류 필터 (예: 전자기기, 도서)", example = "전자기기")
            @RequestParam(required = false) String category) {
        List<AssetResponse> list = assetService.getAssets(cohortId, status, category)
                .stream().map(AssetResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "자산 등록",
            description = """
                    새 자산(물품)을 등록합니다. 사진 파일을 함께 업로드할 수 있습니다.

                    **사용 시점:** 새 물품을 구매하거나 기증받았을 때 등록.

                    **요청 형식:** `multipart/form-data`

                    **사진 업로드:** `photo` 필드에 이미지 파일 첨부 (선택사항).
                    등록 후 사진 URL은 `/uploads/{photoStoragePath}` 형식으로 접근합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자산 등록 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 3,
                                        "cohortId": 5,
                                        "name": "블루투스 스피커",
                                        "category": "전자기기",
                                        "tags": [],
                                        "photoStoragePath": "assets/speaker_abc123.jpg",
                                        "quantity": 1,
                                        "availableQuantity": 1,
                                        "purchasePrice": 80000,
                                        "location": "동아리방 선반",
                                        "status": "AVAILABLE",
                                        "description": "회의실 사용 가능",
                                        "createdAt": "2025-05-10T14:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "자산명 (필수)", required = true, example = "블루투스 스피커") @RequestParam String name,
            @Parameter(description = "분류 (예: 전자기기)", example = "전자기기") @RequestParam(required = false) String category,
            @Parameter(description = "수량 (기본값: 1)", example = "1") @RequestParam(required = false) Integer quantity,
            @Parameter(description = "구매 금액 (원화)", example = "80000") @RequestParam(required = false) Long purchasePrice,
            @Parameter(description = "보관 위치", example = "동아리방 선반") @RequestParam(required = false) String location,
            @Parameter(description = "자산 설명", example = "회의실 사용 가능") @RequestParam(required = false) String description,
            @Parameter(description = "자산 사진 파일 (이미지)") @RequestParam(required = false) MultipartFile photo) {
        Asset asset = assetService.createAsset(cohortId, name, category, null, quantity,
                purchasePrice, location, description, photo);
        return ResponseEntity.ok(ApiResponse.ok(AssetResponse.of(asset)));
    }

    @Operation(
            summary = "자산 상세 조회 (대여 이력 포함)",
            description = """
                    자산 기본 정보와 전체 대여 이력을 함께 조회합니다.

                    **사용 시점:** 자산 상세 페이지에서 정보와 대여 이력을 한 번에 불러올 때.

                    대여 이력의 `returnedAt`이 `null`이면 현재 대여 중인 상태입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자산 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "asset": {
                                          "id": 1,
                                          "cohortId": 5,
                                          "name": "맥북 프로 14인치",
                                          "category": "전자기기",
                                          "tags": ["노트북"],
                                          "photoStoragePath": "assets/macbook.jpg",
                                          "quantity": 2,
                                          "availableQuantity": 1,
                                          "purchasePrice": 2500000,
                                          "location": "동아리방 캐비넷 A",
                                          "status": "RENTED",
                                          "description": "M3 Pro 칩셋",
                                          "createdAt": "2025-03-01T10:00:00"
                                        },
                                        "rentals": [
                                          {
                                            "id": 10,
                                            "assetId": 1,
                                            "borrowerName": "이철수",
                                            "studentId": "2023001",
                                            "contact": "010-1234-5678",
                                            "rentedAt": "2025-05-01T09:00:00",
                                            "dueAt": "2025-05-08T18:00:00",
                                            "returnedAt": null,
                                            "quantity": 1,
                                            "note": "발표 준비용"
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAsset(
            @Parameter(description = "자산 ID", required = true, example = "1") @PathVariable Long id) {
        Asset asset = assetService.getAsset(id);
        List<RentalRecord> rentals = assetService.getRentalHistory(id);
        return ResponseEntity.ok(ApiResponse.ok(AssetDetailResponse.of(asset, rentals)));
    }

    @Operation(
            summary = "자산 정보 수정",
            description = """
                    자산 정보를 수정합니다. 사진 교체도 가능합니다.

                    **사용 시점:** 자산 정보 편집 (이름·분류·수량·가격·위치·상태·설명·사진).

                    **요청 형식:** `multipart/form-data`

                    `photo` 파라미터를 전달하면 기존 사진이 교체되고, 전달하지 않으면 기존 사진이 유지됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자산 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 1,
                                        "cohortId": 5,
                                        "name": "맥북 프로 14인치 (업그레이드)",
                                        "category": "전자기기",
                                        "tags": ["노트북", "Apple"],
                                        "photoStoragePath": "assets/macbook_new.jpg",
                                        "quantity": 3,
                                        "availableQuantity": 2,
                                        "purchasePrice": 2800000,
                                        "location": "동아리방 캐비넷 B",
                                        "status": "AVAILABLE",
                                        "description": "M3 Max 칩셋으로 교체",
                                        "createdAt": "2025-03-01T10:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(
            @Parameter(description = "자산 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "자산명") @RequestParam(required = false) String name,
            @Parameter(description = "분류") @RequestParam(required = false) String category,
            @Parameter(description = "수량") @RequestParam(required = false) Integer quantity,
            @Parameter(description = "구매 금액") @RequestParam(required = false) Long purchasePrice,
            @Parameter(description = "보관 위치") @RequestParam(required = false) String location,
            @Parameter(description = "자산 상태 (AVAILABLE / RENTED / MAINTENANCE / DISPOSED)") @RequestParam(required = false) AssetStatus status,
            @Parameter(description = "자산 설명") @RequestParam(required = false) String description,
            @Parameter(description = "새 사진 파일 (없으면 기존 사진 유지)") @RequestParam(required = false) MultipartFile photo) {
        Asset asset = assetService.updateAsset(id, name, category, null, quantity, purchasePrice,
                location, status, description, photo);
        return ResponseEntity.ok(ApiResponse.ok(AssetResponse.of(asset)));
    }

    @Operation(
            summary = "자산 삭제",
            description = """
                    자산 레코드와 연관된 대여 이력을 삭제합니다.

                    **사용 시점:** 폐기·분실 처리로 자산을 명단에서 제거할 때.

                    **주의:** 현재 대여 중인 자산은 반납 처리 후 삭제하는 것을 권장합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자산 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @Parameter(description = "자산 ID", required = true, example = "1") @PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "자산 대여 등록",
            description = """
                    자산 대여 기록을 등록합니다.

                    **사용 시점:** 학생이 물품을 빌려갈 때 담당자가 등록.

                    **처리 순서:**
                    1. 가용 수량 확인 (부족하면 예외 발생)
                    2. 대여 기록(RentalRecord) 생성
                    3. 자산의 가용 수량 감소 → 수량이 0이 되면 상태가 RENTED로 변경
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대여 등록 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 11,
                                        "assetId": 1,
                                        "borrowerName": "김영희",
                                        "studentId": "2024002",
                                        "contact": "010-9876-5432",
                                        "rentedAt": "2025-05-10T15:00:00",
                                        "dueAt": "2025-05-17T18:00:00",
                                        "returnedAt": null,
                                        "quantity": 1,
                                        "note": "프로젝트 발표용"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "대여 가능 수량 부족",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "대여 가능한 수량이 부족합니다.",
                                      "code": "INSUFFICIENT_QUANTITY"
                                    }
                                    """)))
    })
    @PostMapping("/{id}/rent")
    public ResponseEntity<ApiResponse<RentalResponse>> rentAsset(
            @Parameter(description = "대여할 자산 ID", required = true, example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "대여 요청 정보",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "borrowerName": "김영희",
                              "studentId": "2024002",
                              "contact": "010-9876-5432",
                              "dueAt": "2025-05-17T18:00:00",
                              "quantity": 1,
                              "note": "프로젝트 발표용"
                            }
                            """)))
            @RequestBody RentalRequest req) {
        RentalRecord record = assetService.rentAsset(id, req.getBorrowerName(), req.getStudentId(),
                req.getContact(), req.getDueAt(), req.getQuantity(), req.getNote());
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

    @Operation(
            summary = "자산 반납 처리",
            description = """
                    대여 중인 자산의 반납을 처리합니다.

                    **사용 시점:** 대여자가 물품을 반납했을 때 담당자가 처리.

                    **처리 순서:**
                    1. 대여 기록에 `returnedAt` = 현재 시각 설정
                    2. 자산의 가용 수량 증가 → 수량이 복원되면 상태가 AVAILABLE로 변경
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반납 처리 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 10,
                                        "assetId": 1,
                                        "borrowerName": "이철수",
                                        "studentId": "2023001",
                                        "contact": "010-1234-5678",
                                        "rentedAt": "2025-05-01T09:00:00",
                                        "dueAt": "2025-05-08T18:00:00",
                                        "returnedAt": "2025-05-08T16:30:00",
                                        "quantity": 1,
                                        "note": "발표 준비용"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대여 기록을 찾을 수 없음")
    })
    @PatchMapping("/{id}/rentals/{rentalId}/return")
    public ResponseEntity<ApiResponse<RentalResponse>> returnAsset(
            @Parameter(description = "자산 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "반납할 대여 기록 ID", required = true, example = "10") @PathVariable Long rentalId) {
        RentalRecord record = assetService.returnAsset(id, rentalId);
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    @Getter
    static class RentalRequest {
        private String borrowerName;
        private String studentId;
        private String contact;
        private LocalDateTime dueAt;
        private Integer quantity;
        private String note;
    }

    record AssetResponse(Long id, Long cohortId, String name, String category, List<String> tags,
                         String photoStoragePath, Integer quantity, Integer availableQuantity,
                         Long purchasePrice, String location, String status, String description,
                         LocalDateTime createdAt) {
        static AssetResponse of(Asset a) {
            return new AssetResponse(a.getId(), a.getCohortId(), a.getName(), a.getCategory(),
                    a.getTags(), a.getPhotoStoragePath(), a.getQuantity(), a.getAvailableQuantity(),
                    a.getPurchasePrice(), a.getLocation(), a.getStatus().name(), a.getDescription(),
                    a.getCreatedAt());
        }
    }

    record AssetDetailResponse(AssetResponse asset, List<RentalResponse> rentals) {
        static AssetDetailResponse of(Asset a, List<RentalRecord> records) {
            return new AssetDetailResponse(AssetResponse.of(a),
                    records.stream().map(RentalResponse::of).collect(Collectors.toList()));
        }
    }

    record RentalResponse(Long id, Long assetId, String borrowerName, String studentId,
                          String contact, LocalDateTime rentedAt, LocalDateTime dueAt,
                          LocalDateTime returnedAt, Integer quantity, String note) {
        static RentalResponse of(RentalRecord r) {
            return new RentalResponse(r.getId(), r.getAssetId(), r.getBorrowerName(),
                    r.getStudentId(), r.getContact(), r.getRentedAt(), r.getDueAt(),
                    r.getReturnedAt(), r.getQuantity(), r.getNote());
        }
    }
}
