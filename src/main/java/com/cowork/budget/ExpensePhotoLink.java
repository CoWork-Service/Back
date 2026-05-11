package com.cowork.budget;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "expense_photo_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "photo_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExpensePhotoLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;
}
