package com.cowork.asset;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.common.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final RentalRecordRepository rentalRepository;
    private final FileStorageService storageService;

    public List<Asset> getAssets(Long cohortId, AssetStatus status, String category) {
        return assetRepository.findFiltered(cohortId, status, category);
    }

    public Asset getAsset(Long id) {
        return findById(id);
    }

    public List<RentalRecord> getRentalHistory(Long assetId) {
        return rentalRepository.findByAssetIdOrderByRentedAtDesc(assetId);
    }

    @Transactional
    public Asset createAsset(Long cohortId, String name, String category, List<String> tags,
                             Integer quantity, Long purchasePrice, String location,
                             String description, MultipartFile photo) {
        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            photoPath = storageService.store(photo, "assets", cohortId);
        }

        Asset asset = Asset.builder()
                .cohortId(cohortId)
                .name(name)
                .category(category)
                .tags(tags)
                .quantity(quantity != null ? quantity : 1)
                .availableQuantity(quantity != null ? quantity : 1)
                .purchasePrice(purchasePrice)
                .location(location)
                .description(description)
                .photoStoragePath(photoPath)
                .build();
        return assetRepository.save(asset);
    }

    @Transactional
    public Asset updateAsset(Long id, String name, String category, List<String> tags,
                             Integer quantity, Long purchasePrice, String location,
                             AssetStatus status, String description, MultipartFile photo) {
        Asset asset = findById(id);
        if (photo != null && !photo.isEmpty()) {
            if (asset.getPhotoStoragePath() != null) storageService.delete(asset.getPhotoStoragePath());
            asset.setPhotoPath(storageService.store(photo, "assets", asset.getCohortId()));
        }
        asset.update(name, category, tags, quantity, purchasePrice, location, status, description);
        return asset;
    }

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = findById(id);
        if (asset.getPhotoStoragePath() != null) storageService.delete(asset.getPhotoStoragePath());
        asset.softDelete();
    }

    @Transactional
    public RentalRecord rentAsset(Long assetId, String borrowerName, String studentId,
                                  String managerName, Boolean idCardSubmitted,
                                  LocalDateTime rentedAt, LocalDateTime dueAt, Integer quantity, String note) {
        Asset asset = findById(assetId);
        int qty = Math.max(1, quantity != null ? quantity : 1);
        if (asset.getAvailableQuantity() < qty) {
            throw new BusinessException(ErrorCode.ASSET_UNAVAILABLE);
        }

        asset.decreaseAvailable(qty);

        RentalRecord record = RentalRecord.builder()
                .assetId(assetId)
                .borrowerName(borrowerName)
                .studentId(studentId)
                .managerName(managerName)
                .idCardSubmitted(idCardSubmitted != null ? idCardSubmitted : false)
                .rentedAt(rentedAt != null ? rentedAt : LocalDateTime.now())
                .dueAt(dueAt)
                .quantity(qty)
                .note(note)
                .build();
        return rentalRepository.save(record);
    }

    @Transactional
    public RentalRecord updateRental(Long assetId, Long rentalId, String borrowerName, String studentId,
                                     String managerName, Boolean idCardSubmitted,
                                     LocalDateTime rentedAt, LocalDateTime dueAt, Integer quantity, String note) {
        RentalRecord record = rentalRepository.findById(rentalId)
                .filter(r -> r.getAssetId().equals(assetId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
        Asset asset = findById(assetId);

        Integer nextQuantity = Math.max(1, quantity != null ? quantity : record.getQuantity());
        if (!record.isReturned() && nextQuantity != null && !nextQuantity.equals(record.getQuantity())) {
            int delta = nextQuantity - record.getQuantity();
            if (delta > 0) {
                if (asset.getAvailableQuantity() < delta) {
                    throw new BusinessException(ErrorCode.ASSET_UNAVAILABLE);
                }
                asset.decreaseAvailable(delta);
            } else if (delta < 0) {
                asset.increaseAvailable(-delta);
            }
        }

        record.updateRental(borrowerName, studentId, managerName, idCardSubmitted,
                rentedAt, dueAt, nextQuantity, note);
        return record;
    }

    @Transactional
    public RentalRecord returnAsset(Long assetId, Long rentalId) {
        RentalRecord record = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
        Asset asset = findById(assetId);
        record.returnAsset();
        asset.increaseAvailable(record.getQuantity());
        return record;
    }

    private Asset findById(Long id) {
        return assetRepository.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    }
}
