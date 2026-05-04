package com.cowork.user;

import com.cowork.organization.Organization;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자(User) 엔티티
 *
 * 역할:
 *   코워크 서비스에 가입한 개별 사용자를 나타낸다.
 *   이메일/비밀번호 기반 인증을 사용하며, 반드시 하나의 Organization 에 속해야 한다.
 *
 * 관계:
 *   - User N : 1 Organization  (organization_id FK)
 *   - User 1 : N RefreshToken  (refresh_tokens 테이블)
 *   - User 1 : N CohortMember  (cohort_members 테이블)
 *
 * 가입 흐름:
 *   1. 사용자가 초대코드와 함께 회원가입 → joinStatus = PENDING
 *   2. 관리자가 승인 → joinStatus = ACTIVE (activate() 호출)
 *   3. 관리자가 거절 → User 레코드 삭제
 *
 * 사용 시점:
 *   - Spring Security 인증 주체 (UserDetails) 로 로드된다.
 *   - 컨트롤러에서 @AuthenticationPrincipal 로 현재 로그인 사용자 정보를 꺼낼 때 사용한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 조직
     * - 지연 로딩(LAZY): 조직 정보가 필요한 시점에만 쿼리 실행.
     * - 조직 없이 사용자를 만들 수 없다 (nullable = false).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** 로그인 이메일 (고유값, 중복 불가) */
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    /** BCrypt 등으로 암호화된 비밀번호 (평문 저장 금지) */
    @Column(nullable = false, length = 255)
    private String password;

    /** 사용자 이름 (실명 또는 닉네임) */
    @Column(nullable = false, length = 50)
    private String name;

    /** 프로필 이미지 파일의 저장 경로 또는 URL (없으면 null) */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    /**
     * 가입 상태
     * - PENDING: 가입 신청 후 관리자 승인 대기 중
     * - ACTIVE : 승인 완료, 정상 이용 가능
     * 기본값은 ACTIVE (Builder.Default).
     * 실제 서비스 로직에서 가입 시 PENDING 으로 설정 후 승인 처리를 거친다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", nullable = false, length = 20)
    @Builder.Default
    private JoinStatus joinStatus = JoinStatus.ACTIVE;

    /** 레코드 최초 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 레코드 마지막 수정 일시 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 최초 저장 직전 — createdAt, updatedAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** JPA 업데이트 직전 — updatedAt 갱신 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 비밀번호 변경
     *
     * 동작: 암호화된 새 비밀번호로 교체한다.
     * 사용 시점: 비밀번호 재설정 기능에서 AuthService 가 호출.
     *
     * @param encodedPassword BCrypt 등으로 암호화된 비밀번호
     */
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 계정 활성화
     *
     * 동작: joinStatus 를 ACTIVE 로 변경한다.
     * 사용 시점: 관리자가 가입 신청을 승인할 때 CohortService.approveUser() 에서 호출.
     */
    public void activate() {
        this.joinStatus = JoinStatus.ACTIVE;
    }

    /**
     * 활성 사용자 여부 확인
     *
     * @return true 이면 정상 이용 가능, false 이면 승인 대기 또는 비활성 상태
     */
    public boolean isActive() {
        return this.joinStatus == JoinStatus.ACTIVE;
    }
}
