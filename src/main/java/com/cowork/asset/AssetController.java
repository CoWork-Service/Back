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

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAssets(
            @RequestParam Long cohortId,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String category) {
        List<AssetResponse> list = assetService.getAssets(cohortId, status, category)
                .stream().map(AssetResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAsset(@PathVariable Long id) {
        Asset asset = assetService.getAsset(id);
        List<RentalRecord> rentals = assetService.getRentalHistory(id);
        return ResponseEntity.ok(ApiResponse.ok(AssetDetailResponse.of(asset, rentals)));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/rent")
    public ResponseEntity<ApiResponse<RentalResponse>> rentAsset(
            @PathVariable Long id,
            @RequestBody RentalRequest req) {
        RentalRecord record = assetService.rentAsset(id, req.getBorrowerName(), req.getStudentId(),
                req.getContact(), req.getDueAt(), req.getQuantity(), req.getNote());
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

    @PatchMapping("/{id}/rentals/{rentalId}/return")
    public ResponseEntity<ApiResponse<RentalResponse>> returnAsset(
            @PathVariable Long id,
            @PathVariable Long rentalId) {
        RentalRecord record = assetService.returnAsset(id, rentalId);
        return ResponseEntity.ok(ApiResponse.ok(RentalResponse.of(record)));
    }

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
