package com.cowork.auth;

import com.cowork.auth.dto.LoginRequest;
import com.cowork.auth.dto.RegisterRequest;
import com.cowork.auth.dto.SsoProfileResponse;
import com.cowork.auth.dto.SsoRegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.common.ApiResponse;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.consent.PolicyConsentService;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API — 회원가입, 로그인, 토큰 갱신, 로그아웃, 내 정보 조회")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SsoService ssoService;
    private final UserRepository userRepository;
    private final AuthCookieService authCookieService;
    private final PolicyConsentService policyConsentService;

    @Operation(summary = "숭실대 SSO 콜백", description = "SmartID SSO 인증 성공 후 sToken/sIdno를 받아 프론트로 리다이렉트합니다.")
    @GetMapping("/sso/callback")
    public void ssoCallback(
            @RequestParam(required = false) String sToken,
            @RequestParam(required = false) String sIdno,
            HttpServletResponse response) throws java.io.IOException {
        SsoService.SsoCallbackResult result = ssoService.handleSsoCallback(sToken, sIdno);
        if (result.tokenResponse() != null) {
            authCookieService.addAuthCookies(response, result.tokenResponse());
        }
        response.sendRedirect(result.redirectUrl());
    }

    @Operation(summary = "SSO 신규 사용자 프로필 조회", description = "SSO 콜백에서 발급된 tempToken으로 학생 프로필을 조회합니다.")
    @GetMapping("/sso/profile")
    public ResponseEntity<ApiResponse<SsoProfileResponse>> ssoProfile(@RequestParam String tempToken) {
        return ResponseEntity.ok(ApiResponse.ok(ssoService.getSsoProfile(tempToken)));
    }

    @Operation(summary = "SSO 온보딩 가입", description = "SSO 신규 사용자가 학생회를 생성하거나 기존 학생회에 가입 신청합니다.")
    @PostMapping("/sso/register")
    public ResponseEntity<ApiResponse<TokenResponse>> ssoRegister(
            @RequestBody SsoRegisterRequest req,
            HttpServletResponse response) {
        TokenResponse token = ssoService.ssoRegister(req);
        authCookieService.addAuthCookies(response, token);
        return ResponseEntity.ok(ApiResponse.ok(token.withoutTokens()));
    }

    @Operation(
            summary = "회원가입",
            description = """
                    초대코드를 통해 신규 회원을 등록합니다.

                    **사용 시점:** 관리자로부터 초대코드를 받은 신규 사용자가 최초 가입할 때.

                    **처리 순서:**
                    1. 초대코드로 조직(Organization) 조회
                    2. 이메일 중복 확인
                    3. 사용자 생성 (joinStatus = PENDING, 관리자 승인 대기)
                    4. Access Token + Refresh Token을 HttpOnly 쿠키로 발급

                    **주의:** 가입 직후에는 joinStatus가 PENDING 상태입니다.
                    관리자가 `POST /api/org/pending/{userId}/approve`로 승인해야 정상 사용 가능합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "userId": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "joinStatus": "PENDING"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 초대코드 또는 이메일 중복",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "이미 사용중인 이메일입니다.",
                                      "code": "EMAIL_DUPLICATE"
                                    }
                                    """)))
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "회원가입 요청 정보",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "email": "hong@example.com",
                              "password": "password123!",
                              "name": "홍길동",
                              "inviteCode": "XK9M2P7Q4R8T1AZ6"
                            }
                            """)))
            @Valid @RequestBody RegisterRequest req,
            HttpServletResponse response) {
        TokenResponse token = authService.register(req);
        authCookieService.addAuthCookies(response, token);
        return ResponseEntity.ok(ApiResponse.ok(token.withoutTokens()));
    }

    @Operation(
            summary = "로그인",
            description = """
                    이메일과 비밀번호로 로그인합니다.

                    **사용 시점:** 로그인 화면에서 이메일·비밀번호 제출 시.

                    **처리 순서:**
                    1. 이메일로 사용자 조회
                    2. 비밀번호 검증 (BCrypt)
                    3. 계정 활성 여부 확인 (ACTIVE 상태만 로그인 가능)
                    4. 기존 Refresh Token 삭제 후 새 Access Token + Refresh Token을 HttpOnly 쿠키로 발급

                    **인증 쿠키:** 응답의 `Set-Cookie`로 내려간 HttpOnly 쿠키가 이후 API 요청에 자동 포함됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "userId": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "joinStatus": "ACTIVE"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
                                      "code": "INVALID_CREDENTIALS"
                                    }
                                    """)))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "로그인 요청 정보",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "email": "hong@example.com",
                              "password": "password123!"
                            }
                            """)))
            @Valid @RequestBody LoginRequest req,
            HttpServletResponse response) {
        TokenResponse token = authService.login(req);
        authCookieService.addAuthCookies(response, token);
        return ResponseEntity.ok(ApiResponse.ok(token.withoutTokens()));
    }

    @Operation(
            summary = "Access Token 갱신",
            description = """
                    Refresh Token으로 새 Access Token을 발급받습니다.

                    **사용 시점:** 클라이언트의 Access Token이 만료되어 401 응답을 받았을 때 자동으로 갱신 처리.

                    **처리 순서:**
                    1. Refresh Token 유효성 검증 (DB 조회 + 만료 여부 확인)
                    2. 새 Access Token + Refresh Token을 HttpOnly 쿠키로 발급 (기존 Refresh Token 교체)

                    **인증 헤더 불필요** — HttpOnly Refresh 쿠키가 인증 수단입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "userId": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "joinStatus": "ACTIVE"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token 만료 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "만료된 토큰입니다.",
                                      "code": "TOKEN_EXPIRED"
                                    }
                                    """)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "레거시 호환용 Refresh Token 본문입니다. 브라우저 클라이언트는 HttpOnly Refresh 쿠키를 사용합니다.",
                    required = false,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE1MzI4MDAwLCJleHAiOjE3MTU5MzI4MDB9.def456"
                            }
                            """)))
            @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = authCookieService.resolveRefreshToken(request);
        if (!StringUtils.hasText(refreshToken) && req != null) {
            refreshToken = req.getRefreshToken();
        }

        TokenResponse token = authService.refresh(refreshToken);
        authCookieService.addAuthCookies(response, token);
        return ResponseEntity.ok(ApiResponse.ok(token.withoutTokens()));
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    현재 사용자의 Refresh Token을 무효화합니다.

                    **사용 시점:** 사용자가 로그아웃 버튼을 클릭할 때.

                    **주의:** 서버 Refresh Token을 삭제하고 인증 쿠키를 만료 처리합니다.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": null,
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (userDetails != null) {
            authService.logout(Long.parseLong(userDetails.getUsername()));
        } else {
            authService.logoutByRefreshToken(authCookieService.resolveRefreshToken(request));
        }
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "내 정보 조회",
            description = """
                    현재 로그인한 사용자의 기본 프로필 정보를 반환합니다.

                    **사용 시점:**
                    - 앱 초기 로드 시 로그인 상태와 사용자 정보 확인
                    - 헤더·내비게이션에서 사용자 이름·조직 표시
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정보 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "userId": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "organizationId": 10,
                                        "organizationName": "멋쟁이사자처럼"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = Long.parseLong(userDetails.getUsername());
        User user = userRepository.findWithOrganizationById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        PolicyConsentService.ConsentStatus consentStatus = policyConsentService.getStatus(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(new MeResponse(user.getId(), user.getName(), user.getEmail(),
                user.getOrganization().getId(), user.getOrganization().getName(),
                consentStatus.consentRequired(), consentStatus.termsVersion(), consentStatus.privacyVersion())));
    }

    @lombok.Getter
    @Schema(description = "Access Token 갱신 요청")
    static class RefreshRequest {
        @Schema(description = "기존 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        private String refreshToken;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    @Schema(description = "내 정보 응답")
    static class MeResponse {
        @Schema(description = "사용자 ID", example = "1")
        private Long userId;
        @Schema(description = "이름", example = "홍길동")
        private String name;
        @Schema(description = "이메일", example = "hong@example.com")
        private String email;
        @Schema(description = "소속 조직 ID", example = "10")
        private Long organizationId;
        @Schema(description = "소속 조직명", example = "멋쟁이사자처럼")
        private String organizationName;
        @Schema(description = "필수 약관 및 개인정보 동의 필요 여부", example = "true")
        private boolean consentRequired;
        @Schema(description = "현재 서비스 이용약관 버전", example = "2026-05-17")
        private String termsVersion;
        @Schema(description = "현재 개인정보 수집·이용 동의 버전", example = "2026-05-17")
        private String privacyVersion;
    }
}
