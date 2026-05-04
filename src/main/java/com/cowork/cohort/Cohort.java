package com.cowork.cohort;

import com.cowork.organization.Organization;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 코호트(Cohort) 엔티티
 *
 * 역할:
 *   하나의 Organization 안에서 "기수" 또는 "학기" 단위의 그룹을 나타낸다.
 *   예: "2024년 1기", "2025 봄학기" 등.
 *   모든 학생 관리·행사·예산·파일 등의 데이터는 이 Cohort 단위로 구분된다.
 *
 * 관계:
 *   - Cohort N : 1 Organization    (organization_id FK)
 *   - Cohort 1 : N CohortMember    (cohort_members 테이블)
 *   - Cohort 1 : N Student         (students 테이블의 cohort_id FK)
 *   - Cohort 1 : N CoworkEvent     (cowork_events 테이블의 cohort_id FK)
 *   - ... (자산·파일·설문 등도 cohort_id 로 연결)
 *
 * 사용 시점:
 *   - 관리자가 새 기수를 생성할 때 CohortService.createCohort() 에서 저장.
 *   - 각 도메인 API 에서 cohortId 를 파라미터로 받아 해당 기수 데이터를 조회·생성한다.
 */
@Entity
@Table(name = "cohorts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Cohort {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 조직
     * - 지연 로딩(LAZY): 코호트 정보만 필요할 때 조직 쿼리를 방지.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** 기수 레이블 (예: "1기", "2024 봄", "25학번") */
    @Column(nullable = false, length = 50)
    private String label;

    /** 기수 연도 (예: 2024, 2025) */
    @Column(nullable = false)
    private Integer year;

    /** 코호트 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 코호트 정보 수정
     *
     * 동작: label 과 year 를 새 값으로 덮어쓴다.
     * 사용 시점: 관리자가 기수 이름/연도를 변경할 때 CohortService.updateCohort() 에서 호출.
     *
     * @param label 새 기수 레이블
     * @param year  새 연도
     */
    public void update(String label, Integer year) {
        this.label = label;
        this.year = year;
    }
}
