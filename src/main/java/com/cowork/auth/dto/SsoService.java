package com.cowork.auth;

import com.cowork.auth.dto.SsoProfileResponse;
import com.cowork.auth.dto.SsoRegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.cohort.CohortMember;
import com.cowork.cohort.CohortMemberRepository;
import com.cowork.cohort.MemberRole;
import com.cowork.user.JoinStatus;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.cowork.cohort.Cohort;
import com.cowork.cohort.CohortRepository;
import com.cowork.organization.Organization;
import com.cowork.organization.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CohortMemberRepository cohortMemberRepository;
    private final SsoTempTokenRepository ssoTempTokenRepository;
    private final JwtUtil jwtUtil;
    private final OrganizationRepository organizationRepository;
    private final CohortRepository cohortRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.sso-temp-token-expiry:300}")
    private long tempTokenExpiry;

    private static final String SAINT_SSO_URL = "https://saint.ssu.ac.kr/webSSO/sso.jsp";
    private static final String SAINT_MAIN_URL = "https://saint.ssu.ac.kr/webSSUMain/main_student.jsp";

    @Transactional
    public String handleSsoCallback(String sToken, String sIdno) {
        // 1. SAINT 서버에 sToken 재검증
        SaintProfile profile = null;
        try {
            profile = verifySaintAndGetProfile(sToken, sIdno);
            log.info("SAINT 검증 성공 - 학번: {}, 이름: {}", profile.studentId, profile.name);
        } catch (Exception e) {
            log.warn("SAINT 검증 실패, sIdno로 fallback: {}", e.getMessage());
        }

        String studentId = profile != null ? profile.studentId : sIdno;
        Optional<User> existingUser = userRepository.findByStudentId(studentId);

        // 2. 기존 유저면 JWT 발급 후 메인으로
        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (user.getJoinStatus() == JoinStatus.PENDING) {
                return frontendUrl + "/pending";
            }
            if (user.getJoinStatus() == JoinStatus.ACTIVE) {
                TokenResponse token = issueTokens(user);
                return frontendUrl + "/main?accessToken=" + token.getAccessToken()
                        + "&refreshToken=" + token.getRefreshToken();
            }
            return frontendUrl + "/rejected";
        }

        // 3. 신규 유저 → 임시 토큰 발급 후 온보딩으로
        String tempToken = UUID.randomUUID().toString();
        SsoTempToken ssoTempToken = SsoTempToken.builder()
                .token(tempToken)
                .studentId(studentId)
                .name(profile != null ? profile.name : "")
                .department(profile != null ? profile.department : null)
                .email(profile != null ? profile.email : null)
                .expiresAt(LocalDateTime.now().plusSeconds(tempTokenExpiry))
                .build();
        ssoTempTokenRepository.save(ssoTempToken);

        return frontendUrl + "/onboarding?tempToken=" + tempToken;
    }

    @Transactional
    public SsoProfileResponse getSsoProfile(String tempToken) {
        SsoTempToken ssoTempToken = ssoTempTokenRepository.findByToken(tempToken)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 tempToken"));

        if (ssoTempToken.isExpired()) {
            ssoTempTokenRepository.deleteByToken(tempToken);
            throw new RuntimeException("만료된 tempToken");
        }

        return new SsoProfileResponse(
                ssoTempToken.getStudentId(),
                ssoTempToken.getName(),
                ssoTempToken.getDepartment(),
                ssoTempToken.getEmail()
        );
    }

    private SaintProfile verifySaintAndGetProfile(String sToken, String sIdno)
            throws IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String ssoUrl = SAINT_SSO_URL + "?sToken=" + sToken + "&sIdno=" + sIdno;
        HttpRequest ssoRequest = HttpRequest.newBuilder()
                .uri(URI.create(ssoUrl))
                .header("Cookie", "sToken=" + sToken + "; sIdno=" + sIdno)
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> ssoResponse = client.send(ssoRequest, HttpResponse.BodyHandlers.ofString());

        if (ssoResponse.statusCode() != 200 || !ssoResponse.body().contains("location.href = \"/irj/portal\"")) {
            throw new RuntimeException("SAINT SSO 재검증 실패");
        }

        String cookies = ssoResponse.headers().allValues("set-cookie").stream()
                .map(c -> c.split(";")[0])
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);

        HttpRequest profileRequest = HttpRequest.newBuilder()
                .uri(URI.create(SAINT_MAIN_URL))
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> profileResponse = client.send(profileRequest, HttpResponse.BodyHandlers.ofString());
        log.info("SAINT HTML: {}", profileResponse.body());
        return parseStudentProfile(profileResponse.body(), sIdno);
    }

    private SaintProfile parseStudentProfile(String html, String fallbackStudentId) {
        Document doc = Jsoup.parse(html);
        SaintProfile profile = new SaintProfile();

        // 이름 파싱 - "배성찬님 환영합니다." → "배성찬"
        Element nameEl = doc.selectFirst(".main_box09 .box_top .main_title span");
        if (nameEl != null) {
            String nameText = nameEl.text().trim();
            // "님 환영합니다." 제거
            nameText = nameText.replaceAll("님.*$", "").trim();
            profile.name = nameText;
        }

        // 학번, 소속 파싱
        Elements items = doc.select(".main_box09_con li dl");
        for (Element item : items) {
            Element dt = item.selectFirst("dt");
            Element dd = item.selectFirst("dd strong");
            if (dt == null || dd == null) continue;

            String label = dt.text().trim();
            String value = dd.text().trim();

            if (label.equals("학번")) profile.studentId = value;
            else if (label.equals("소속")) profile.department = value;
        }

        if (profile.studentId == null) profile.studentId = fallbackStudentId;
        return profile;
    }

    static class SaintProfile {
        String studentId;
        String name;
        String department;
        String email;
    }

    private TokenResponse issueTokens(User user) {
        refreshTokenRepository.deleteByUser(user);

        List<CohortMember> memberships = cohortMemberRepository.findByUserId(user.getId());
        String role = memberships.stream()
                .filter(m -> m.getRole() == MemberRole.ADMIN)
                .findFirst()
                .map(m -> "ADMIN")
                .orElse("EDITOR");

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), role);
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpiry()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken, refreshTokenStr, user.getId(),
                user.getName(), user.getEmail(), user.getJoinStatus().name());
    }
    @Transactional
    public TokenResponse ssoRegister(SsoRegisterRequest req) {
        // tempToken 검증
        SsoTempToken ssoTempToken = ssoTempTokenRepository.findByToken(req.getTempToken())
                .orElseThrow(() -> new RuntimeException("유효하지 않은 tempToken"));

        if (ssoTempToken.isExpired()) {
            ssoTempTokenRepository.deleteByToken(req.getTempToken());
            throw new RuntimeException("만료된 tempToken");
        }

        // 이미 가입된 학번인지 확인
        if (userRepository.findByStudentId(ssoTempToken.getStudentId()).isPresent()) {
            throw new RuntimeException("이미 가입된 학번입니다");
        }

        Organization org;
        MemberRole role;

        if (req.isCouncilMember()) {
            // 학과로 기존 Organization 조회
            org = organizationRepository.findByDepartment(req.getDepartment())
                    .orElseThrow(() -> new RuntimeException("해당 학과 학생회가 없습니다. 학생회장이 먼저 가입해야 합니다."));
            role = MemberRole.EDITOR;
        } else {
            // 학생회장 → Organization 생성
            org = Organization.builder()
                    .name(req.getDepartment() + " 학생회")
                    .department(req.getDepartment())
                    .inviteCode(generateInviteCode())
                    .build();
            organizationRepository.save(org);
            role = MemberRole.ADMIN;
        }

        // User 생성 (PENDING)
        User user = User.builder()
                .organization(org)
                .studentId(ssoTempToken.getStudentId())
                .email(req.getEmail())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(ssoTempToken.getName())
                .joinStatus(JoinStatus.PENDING)
                .build();
        userRepository.save(user);

        // Cohort 처리
        int year = LocalDateTime.now().getYear();
        String cohortLabel = req.getCohortLabel() != null ? req.getCohortLabel() : "1기";
        Cohort cohort = cohortRepository.findByOrganizationAndLabel(org, cohortLabel)
                .orElseGet(() -> cohortRepository.save(Cohort.builder()
                        .organization(org)
                        .label(cohortLabel)
                        .year(year)
                        .build()));

        CohortMember member = CohortMember.builder()
                .cohort(cohort)
                .user(user)
                .role(role)
                .build();
        cohortMemberRepository.save(member);

        // tempToken 삭제
        ssoTempTokenRepository.deleteByToken(req.getTempToken());

        return new TokenResponse(null, null, user.getId(), user.getName(),
                user.getEmail(), user.getJoinStatus().name());
    }

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".charAt(random.nextInt(36)));
        }
        String code = sb.toString();
        if (organizationRepository.findByInviteCode(code).isPresent()) {
            return generateInviteCode();
        }
        return code;
    }
}