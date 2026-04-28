package com.cowork.survey;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, length = 500)
    private String label;
}
