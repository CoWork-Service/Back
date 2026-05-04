package com.cowork.cohort;

import com.cowork.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 코호트 멤버(CohortMember) 엔티티
 *
 * 역할:
 *   User 와 Cohort 의 다대다 관계를 중간 테이블로 풀어낸 엔티티.
 *   한 사용자가 특정 코호트에서 어떤 역할(role) 과 부서(department) 를 가지는지 표현한다.
 *
 * 관계:
 *   - CohortMember N : 1 Cohort
 *   - CohortMember N : 1 User
 *   - (cohort_id, user_id) 복합 유니크 → 동일 코호트에 같은 사용자 중복 불가.
 *
 * 역할 구분 (MemberRole):
 *   - ADMIN  : 코호트 관리 권한 (멤버 승인/거절, 코호트 수정 등)
 *   - EDITOR : 일반 편집 권한 (행사, 파일 등 생성/수정)
 *   - VIEWER : 읽기 전용
 *
 * 사용 시점:
 *   - 관리자가 가입 신청을 승인하면 해당 코호트의 CohortMember 레코드를 생성.
 *   - 멤버 역할/부서 변경 시 update() 호출.
 *   - 멤버 제거 시 레코드 삭제.
 */
@Entity
@Table(name = "cohort_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cohort_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CohortMember {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 코호트
     * - 지연 로딩: 멤버 목록 조회 시 코호트 정보를 즉시 로딩하지 않음.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    /**
     * 해당 사용자
     * - 지연 로딩: 멤버 정보만 필요한 경우 User 쿼리를 방지.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 코호트 내 역할
     * 기본값: EDITOR (일반 편집 권한)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemberRole role = MemberRole.EDITOR;

    /**
     * 소속 부서
     * - null 허용: 부서가 지정되지 않은 경우 전체 부서로 간주.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Department department;

    /** 멤버 등록 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 역할/부서 변경
     *
     * 동작: 멤버의 역할과 부서를 새 값으로 교체한다.
     * 사용 시점: 관리자가 특정 멤버의 권한이나 부서를 수정할 때
     *            CohortService.updateMember() 에서 호출.
     *
     * @param role       새 역할 (ADMIN / EDITOR / VIEWER)
     * @param department 새 부서 (null 이면 전체)
     */
    public void update(MemberRole role, Department department) {
        this.role = role;
        this.department = department;
    }
}
