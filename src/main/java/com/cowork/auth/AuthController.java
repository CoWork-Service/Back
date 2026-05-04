package com.cowork.auth;

import com.cowork.auth.dto.LoginRequest;
import com.cowork.auth.dto.RegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 컨트롤러 (AuthController)
 *
 * 역할:
 *   JWT 기반 회원가입·로그인·토큰 갱신·로그아웃·내 정보 조회 API 를 제공한다.
 *   모든 엔드포인트는 /api/auth 하위에 위치한다.
 *
 * 인증 흐름:
 *   1. POST /register  → Access Token + Refresh Token 발급
 *   2. POST /login     → Access Token + Refresh Token 발급
 *   3. POST /refresh   → Refresh Token 으로 새 Access Token 발급
 *   4. POST /logout    → Refresh Token 무효화
 *   5. GET  /me        → 현재 로그인 사용자 정보 반환
 *
 * 인증 필요 여부:
 *   - /register, /login : 인증 불필요 (SecurityConfig 에서 permitAll)
 *   - /refresh          : 인증 불필요 (Refresh Token 자체가 인증 수단)
 *   - /logout, /me      : JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    /**
     * 회원가입
     *
     * 동작:
     *   1. 초대코드로 Organization 조회
     *   2. 이메일 중복 확인
     *   3. User 생성 (joinStatus = PENDING)
     *   4. Access Token + Refresh Token 발급 후 반환
     *
     * 사용 시점: 신규 사용자가 초대코드를 받아 최초 가입할 때.
     * 인증 필요: 없음
     *
     * @param req 가입 요청 (email, password, name, inviteCode)
     * @return Access Token + Refresh Token
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(req)));
    }

    /**
     * 로그인
     *
     * 동작:
     *   1. 이메일로 User 조회
     *   2. 비밀번호 검증 (BCrypt)
     *   3. 계정 활성 여부 확인 (ACTIVE 상태만 로그인 가능)
     *   4. 기존 Refresh Token 삭제 후 새 토큰 쌍 발급
     *
     * 사용 시점: 로그인 화면에서 이메일/비밀번호 제출 시.
     * 인증 필요: 없음
     *
     * @param req 로그인 요청 (email, password)
     * @return Access Token + Refresh Token
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req)));
    }

    /**
     * Access Token 갱신
     *
     * 동작:
     *   1. Refresh Token 유효성 검증 (DB 조회 + 만료 여부 확인)
     *   2. 새 Access Token + Refresh Token 발급 (기존 Refresh Token 교체)
     *
     * 사용 시점: 클라이언트의 Access Token 이 만료(401) 되었을 때 자동 갱신 처리.
     * 인증 필요: 없음 (Refresh Token 을 자격증명으로 사용)
     *
     * @param req { "refreshToken": "..." }
     * @return 새 Access Token + Refresh Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req.getRefreshToken())));
    }

    /**
     * 로그아웃
     *
     * 동작: 현재 사용자의 Refresh Token 을 DB 에서 삭제하여 재발급 불가 상태로 만든다.
     *       Access Token 은 만료 시까지 유효하므로 클라이언트에서도 삭제해야 한다.
     *
     * 사용 시점: 사용자가 로그아웃 버튼을 클릭할 때.
     * 인증 필요: JWT Access Token
     *
     * @param userDetails Spring Security 가 주입하는 현재 로그인 사용자
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 내 정보 조회
     *
     * 동작: JWT 토큰에서 userId 를 추출하고 DB 에서 User 를 조회하여 기본 프로필을 반환한다.
     *
     * 사용 시점:
     *   - 앱 초기 로드 시 로그인 상태와 사용자 정보 확인.
     *   - 헤더·내비게이션에서 사용자 이름/조직 표시.
     *
     * 인증 필요: JWT Access Token
     *
     * @param userDetails 현재 로그인 사용자
     * @return userId, name, email, organizationId, organizationName
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        User user = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(new MeResponse(user.getId(), user.getName(), user.getEmail(),
                user.getOrganization().getId(), user.getOrganization().getName())));
    }

    /** /api/auth/refresh 요청 바디 — refreshToken 필드 하나만 포함 */
    @lombok.Getter
    static class RefreshRequest {
        private String refreshToken;
    }

    /** GET /api/auth/me 응답 바디 — 로그인 사용자 기본 프로필 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    static class MeResponse {
        private Long userId;
        private String name;
        private String email;
        private Long organizationId;
        private String organizationName;
    }
}
