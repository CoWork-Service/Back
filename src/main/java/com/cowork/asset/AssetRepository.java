package com.cowork.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    @Query("SELECT a FROM Asset a WHERE a.cohortId = :cohortId AND a.deletedAt IS NULL " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:category IS NULL OR a.category = :category) " +
           "ORDER BY a.createdAt DESC")
    List<Asset> findFiltered(Long cohortId, AssetStatus status, String category);
}
