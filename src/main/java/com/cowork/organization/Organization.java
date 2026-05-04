package com.cowork.organization;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 조직(Organization) 엔티티
 *
 * 역할:
 *   코워크 서비스의 최상위 단위. 하나의 조직은 여러 코호트(기수)와 여러 사용자를 포함한다.
 *   예를 들어 "OO대학 총학생회" 또는 "OO 동아리 운영팀" 하나가 하나의 Organization이 된다.
 *
 * 관계:
 *   - Organization 1 : N User       (users 테이블의 organization_id 컬럼이 FK)
 *   - Organization 1 : N Cohort     (cohorts 테이블의 organization_id 컬럼이 FK)
 *
 * 사용 시점:
 *   - 회원가입 시 초대코드(inviteCode)로 Organization 을 찾아 사용자를 소속시킨다.
 *   - 관리자가 초대코드를 재발급할 때 regenerateInviteCode() 를 호출한다.
 */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Organization {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 조직명 (예: "OO대학 총학생회") */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 초대 코드
     * - 신규 사용자가 회원가입할 때 이 코드를 입력하면 해당 조직에 소속된다.
     * - 유니크 제약: 코드가 겹치지 않아야 함.
     * - 보안상 필요하면 관리자가 재발급할 수 있다.
     */
    @Column(name = "invite_code", nullable = false, unique = true, length = 10)
    private String inviteCode;

    /** 조직 생성 일시 (최초 저장 시점에만 기록, 이후 변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 자동 호출 — createdAt 을 현재 시각으로 설정 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 초대 코드 재발급
     *
     * 동작: 기존 inviteCode 를 새 코드로 교체한다.
     * 사용 시점: 관리자가 코드 유출 등의 이유로 새 코드를 발급해야 할 때.
     *
     * @param newCode 새로 발급된 초대 코드
     */
    public void regenerateInviteCode(String newCode) {
        this.inviteCode = newCode;
    }
}
