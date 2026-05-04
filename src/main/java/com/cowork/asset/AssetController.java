package com.cowork.asset;

import com.cowork.common.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 자산 관리 컨트롤러 (AssetController)
 *
 * 역할:
 *   코호트가 보유한 물품(자산) 의 등록·조회·수정·삭제 및 대여/반납 API 를 제공한다.
 *   기본 경로: /api/assets
 *
 * 주요 기능:
 *   - 자산 CRUD
 *   - 사진 업로드 (multipart)
 *   - 대여 등록 (POST /{id}/rent)
 *   - 반납 처리 (PATCH /{id}/rentals/{rentalId}/return)
 *   - 자산 상세 + 대여 이력 한 번에 조회
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * 자산 목록 조회
     *
     * 동작: cohortId 기준으로 자산 목록을 조회하며, 상태·분류로 필터링 가능.
     * 사용 시점: 자산 관리 화면에서 목록 표시 및 필터 적용 시.
     *
     * @param cohortId 필수. 조회할 코호트 ID
     * @param status   선택. 자산 상태 필터 (AVAILABLE / RENTED / MAINTENANCE / DISPOSED)
     * @param category 선택. 분류 필터 (예: "전자기기")
     * @return 자산 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAssets(
            @RequestParam Long cohortId,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String category) {
        List<AssetResponse> list = assetService.getAssets(cohortId, status, category)
                .stream().map(AssetResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 자산 등록
     *
     * 동작: 새 자산을 등록한다. 사진 파일(multipart)을 함께 업로드할 수 있다.
     * 사용 시점: 새 물품을 구매하거나 기증받았을 때 등록.
     *
     * Content-Type: multipart/form-data
     * @param cohortId      필수. 소속 코호트 ID
     * @param name          필수. 자산명
     * @param category      선택. 분류
     * @param quantity      선택. 수량 (기본값 1)
     * @param purchasePrice 선택. 구매 금액
     * @param location      선택. 보관 위치
     * @param description   선택. 자산 설명
     * @param photo         선택. 사진 파일
     * @return 생성된 자산 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @RequestParam Long cohortId,
            @RequestParam String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Long purchasePrice,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile photo) {
        Asset asset = assetService.createAsset(cohortId, name, category, null, quantity,
                purchasePrice, location, description, photo);
        return ResponseEntity.ok(ApiResponse.ok(AssetResponse.of(asset)));
    }

    /**
     * 자산 상세 조회 (대여 이력 포함)
     *
     * 동작: 자산 기본 정보와 전체 대여 이력을 함께 반환한다.
     * 사용 시점: 자산 상세 페이지에서 정보와 대여 이력을 한 번에 불러올 때.
     *
     * @param id 자산 ID
     * @return 자산 정보 + 대여 이력 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAsset(@PathVariable Long id) {
        Asset asset = assetService.getAsset(id);
        List<RentalRecord> rentals = assetService.getRentalHistory(id);
        return ResponseEntity.ok(ApiResponse.ok(AssetDetailResponse.of(asset, rentals)));
    }

    /**
     * 자산 정보 수정
     *
     * 동작: 자산 정보를 수정한다. 사진 교체도 가능 (photo 파라미터 전달 시).
     * 사용 시점: 자산 정보 편집 시 (이름·분류·수량·가격·위치·상태·설명·사진).
     *
     * Content-Type: multipart/form-data
     * @param id       자산 ID
     * @param photo    선택. 새 사진 파일 (없으면 기존 사진 유지)
     * @return 수정된 자산 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Long purchasePrice,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile photo) {
        Asset asset = assetService.updateAsset(id, name, category, null, quantity, purchasePrice,
                location, status, description, photo);
        return ResponseEntity.ok(ApiResponse.ok(AssetResponse.of(asset)));
    }

    /**
     * 자산 삭제
     *
     * 동작: 자산 레코드와 연관된 대여 이력을 삭제한다.
     *       현재 대여 중인 자산을 삭제하면 예외 발생할 수 있으므로 주의.
     * 사용 시점: 폐기·분실 처리로 자산을 명단에서 제거할 때.
     *
     * @param id 자산 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 자산 대여 등록
     *
     * 동작:
     *   1. 가용 수량 확인 (부족하면 예외)
     *   2. RentalRecord 생성
     *   3. Asset.decreaseAvailable() 호출 → 가용 수량 감소, 필요 시 상태 RENTED 로 변경
     *
     * 사용 시점: 학생이 물품을 빌려갈 때 담당자가 등록.
     *
     * @param id  대여할 자산 ID
     * @param req { borrowerName, studentId, contact, dueAt, quantity, note }
     * @return 생성된 대여 기록
     */
    @PostMapping("/{id}/rent")
    public ResponseEntity<ApiResponse<RentalResponse>> rentAsset(
            @PathVariable Long id,
            @RequestBody RentalRequest req) {
        RentalRecord record = assetService.rentAsset(id, req.getBorrowerName(), req.getStudentId(),
                req.getContact(), req.getDueAt(), req.getQuantity(), req.getNote());
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

    /**
     * 자산 반납 처리
     *
     * 동작:
     *   1. RentalRecord.returnAsset() 호출 → returnedAt = 현재 시각
     *   2. Asset.increaseAvailable() 호출 → 가용 수량 복원, 필요 시 상태 AVAILABLE 로 변경
     *
     * 사용 시점: 대여자가 물품을 반납했을 때 담당자가 처리.
     *
     * @param id       자산 ID
     * @param rentalId 반납할 대여 기록 ID
     * @return 업데이트된 대여 기록 (returnedAt 포함)
     */
    @PatchMapping("/{id}/rentals/{rentalId}/return")
    public ResponseEntity<ApiResponse<RentalResponse>> returnAsset(
            @PathVariable Long id,
            @PathVariable Long rentalId) {
        RentalRecord record = assetService.returnAsset(id, rentalId);
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST /{id}/rent 요청 바디 */
    @Getter
    static class RentalRequest {
        private String borrowerName;
        private String studentId;
        private String contact;
        private LocalDateTime dueAt;
        private Integer quantity;
        private String note;
    }

    /** 자산 목록 응답 DTO */
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

    /** 자산 상세 응답 DTO (자산 정보 + 대여 이력 목록) */
    record AssetDetailResponse(AssetResponse asset, List<RentalResponse> rentals) {
        static AssetDetailResponse of(Asset a, List<RentalRecord> records) {
            return new AssetDetailResponse(AssetResponse.of(a),
                    records.stream().map(RentalResponse::of).collect(Collectors.toList()));
        }
    }

    /** 대여 기록 응답 DTO */
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
