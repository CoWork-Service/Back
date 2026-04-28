package com.cowork.asset;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Asset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> tags;

    @Column(name = "photo_storage_path", length = 500)
    private String photoStoragePath;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 1;

    @Column(name = "purchase_price")
    private Long purchasePrice;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(columnDefinition = "TEXT")
    private String description;

    public void update(String name, String category, List<String> tags, Integer quantity,
                       Long purchasePrice, String location, AssetStatus status, String description) {
        this.name = name;
        this.category = category;
        this.tags = tags;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.location = location;
        this.status = status;
        this.description = description;
    }

    public void setPhotoPath(String path) {
        this.photoStoragePath = path;
    }

    public void decreaseAvailable(int qty) {
        this.availableQuantity = Math.max(0, this.availableQuantity - qty);
        if (this.availableQuantity == 0) {
            this.status = AssetStatus.RENTED;
        }
    }

    public void increaseAvailable(int qty) {
        this.availableQuantity = Math.min(this.quantity, this.availableQuantity + qty);
        if (this.availableQuantity > 0 && this.status == AssetStatus.RENTED) {
            this.status = AssetStatus.AVAILABLE;
        }
    }
}
