package com.cowork.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RentalRecordRepository extends JpaRepository<RentalRecord, Long> {

    List<RentalRecord> findByAssetIdOrderByRentedAtDesc(Long assetId);
}
