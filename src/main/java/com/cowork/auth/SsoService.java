package com.cowork.auth;

import com.cowork.auth.dto.SsoProfileResponse;
import com.cowork.auth.dto.SsoRegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.cohort.Cohort;
import com.cowork.cohort.CohortMember;
import com.cowork.cohort.CohortMemberRepository;
import com.cowork.cohort.CohortRepository;
import com.cowork.cohort.Department;
import com.cowork.cohort.MemberRole;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.organization.Organization;
import com.cowork.organization.OrganizationDepartmentService;
import com.cowork.organization.OrganizationRepository;
import com.cowork.user.JoinStatus;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    private static final String SAINT_SSO_URL = "https://saint.ssu.ac.kr/webSSO/sso.jsp";
    private static final String SAINT_MAIN_URL = "https://saint.ssu.ac.kr/webSSUMain/main_student.jsp";
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 16;
    private static final String SOONGSIL_MAIL_DOMAIN = "soongsil.ac.kr";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ENCODED_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_+/\\-]{8,}={0,2}");
    private static final int MAX_COOKIE_DECODE_DEPTH = 3;

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CohortRepository cohortRepository;
    private final CohortMemberRepository cohortMemberRepository;
    private final SsoTempTokenRepository ssoTempTokenRepository;
    private final OrganizationDepartmentService organizationDepartmentService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.sso-temp-token-expiry:300}")
    private long tempTokenExpirySeconds;

    @Value("${app.sso-allow-unverified-fallback:false}")
    private boolean allowUnverifiedFallback;

    @Transactional
    public String handleSsoCallback(String sToken, String sIdno) {
        try {
            if (!hasText(sToken) || !hasText(sIdno)) {
                throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
            }

            SaintProfile profile = resolveSaintProfile(sToken, sIdno);
            Optional<User> existingUser = userRepository.findByStudentId(profile.studentId());
            if (existingUser.isPresent()) {
                return redirectExistingUser(existingUser.get(), profile);
            }

            String tempToken = UUID.randomUUID().toString();
            ssoTempTokenRepository.save(SsoTempToken.builder()
                    .token(tempToken)
                    .studentId(profile.studentId())
                    .name(defaultString(profile.name()))
                    .department(profile.department())
                    .email(profile.email())
                    .expiresAt(LocalDateTime.now().plusSeconds(tempTokenExpirySeconds))
                    .build());

            return redirect("/onboarding", Map.of("tempToken", tempToken));
        } catch (Exception e) {
            log.warn("SSO callback failed: {}", e.getMessage());
            return redirect("/login", Map.of("error", "SSO 로그인에 실패했습니다."));
        }
    }

    @Transactional
    public SsoProfileResponse getSsoProfile(String tempToken) {
        SsoTempToken token = findValidTempToken(tempToken);
        return new SsoProfileResponse(token.getStudentId(), token.getName(), token.getDepartment());
    }

    @Transactional
    public TokenResponse ssoRegister(SsoRegisterRequest req) {
        SsoTempToken tempToken = findValidTempToken(req.getTempToken());
        if (userRepository.existsByStudentId(tempToken.getStudentId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_STUDENT_ID);
        }

        String email = resolveEmail(tempToken.getEmail(), tempToken.getStudentId());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        TokenResponse response = req.isCouncilMember()
                ? createCouncil(req, tempToken, email)
                : joinCouncil(req, tempToken, email);
        ssoTempTokenRepository.deleteByToken(tempToken.getToken());
        return response;
    }

    private String redirectExistingUser(User user, SaintProfile profile) {
        if (user.getJoinStatus() == JoinStatus.PENDING) {
            return redirect("/pending", Map.of(
                    "studentId", defaultString(user.getStudentId()),
                    "name", user.getName()
            ));
        }

        TokenResponse token = authService.issueTokens(user);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessToken", token.getAccessToken());
        params.put("refreshToken", token.getRefreshToken());
        params.put("userId", String.valueOf(token.getUserId()));
        params.put("name", token.getName());
        params.put("studentId", defaultString(user.getStudentId()));
        params.put("department", firstText(user.getOrganization().getDepartment(), profile.department()));
        params.put("organizationId", String.valueOf(user.getOrganization().getId()));
        params.put("organizationName", user.getOrganization().getName());
        params.put("joinStatus", token.getJoinStatus());
        params.put("hasCouncil", "true");
        return redirect("/main", params);
    }

    private TokenResponse createCouncil(SsoRegisterRequest req, SsoTempToken tempToken, String email) {
        String department = firstText(req.getDepartment(), tempToken.getDepartment());
        Organization organization = Organization.builder()
                .name(firstText(req.getOrganizationName(), "A:NSWER"))
                .department(department)
                .inviteCode(generateInviteCode())
                .build();
        organizationRepository.save(organization);

        User user = User.builder()
                .organization(organization)
                .studentId(tempToken.getStudentId())
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(firstText(tempToken.getName(), tempToken.getStudentId()))
                .joinStatus(JoinStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Cohort cohort = Cohort.builder()
                .organization(organization)
                .label(firstText(req.getCohortLabel(), "1기"))
                .year(LocalDateTime.now().getYear())
                .build();
        cohortRepository.save(cohort);

        cohortMemberRepository.save(CohortMember.builder()
                .cohort(cohort)
                .user(user)
                .role(MemberRole.ADMIN)
                .department(Department.회장단)
                .build());

        organizationDepartmentService.replaceDepartments(organization.getId(), req.getDepartments());
        return authService.issueTokens(user);
    }

    private TokenResponse joinCouncil(SsoRegisterRequest req, SsoTempToken tempToken, String email) {
        Organization organization = findOrganizationForJoin(req, tempToken);
        User user = User.builder()
                .organization(organization)
                .studentId(tempToken.getStudentId())
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(firstText(tempToken.getName(), tempToken.getStudentId()))
                .joinStatus(JoinStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Cohort cohort = cohortRepository.findByOrganizationIdOrderByYearDesc(organization.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));
        cohortMemberRepository.save(CohortMember.builder()
                .cohort(cohort)
                .user(user)
                .role(MemberRole.EDITOR)
                .build());

        return authService.issueTokens(user);
    }

    private Organization findOrganizationForJoin(SsoRegisterRequest req, SsoTempToken tempToken) {
        if (hasText(req.getInviteCode())) {
            return organizationRepository.findByInviteCode(req.getInviteCode().trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
        }

        throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
    }

    private SsoTempToken findValidTempToken(String tempToken) {
        if (!hasText(tempToken)) {
            throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
        }

        SsoTempToken token = ssoTempTokenRepository.findByToken(tempToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SSO_TOKEN));
        if (token.isExpired()) {
            ssoTempTokenRepository.deleteByToken(tempToken);
            throw new BusinessException(ErrorCode.EXPIRED_SSO_TOKEN);
        }
        return token;
    }

    private SaintProfile resolveSaintProfile(String sToken, String sIdno) throws IOException, InterruptedException {
        try {
            SaintProfile profile = verifySaintAndGetProfile(sToken, sIdno);
            if (!hasText(profile.studentId())) {
                throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
            }
            return profile;
        } catch (Exception e) {
            if (!allowUnverifiedFallback) {
                throw e;
            }
            log.warn("SAINT verification failed, using fallback student id: {}", e.getMessage());
            return new SaintProfile(sIdno, "", null, null);
        }
    }

    private SaintProfile verifySaintAndGetProfile(String sToken, String sIdno) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String ssoUrl = SAINT_SSO_URL + "?sToken=" + encode(sToken) + "&sIdno=" + encode(sIdno);
        HttpResponse<String> ssoResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(ssoUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", "sToken=" + sToken + "; sIdno=" + sIdno)
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        if (ssoResponse.statusCode() < 200 || ssoResponse.statusCode() >= 400) {
            throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
        }

        String cookies = extractCookies(ssoResponse);
        String cookieEmail = extractEmailFromCookies(ssoResponse.headers().allValues("set-cookie"));
        if (!hasText(cookies)) {
            throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
        }

        HttpResponse<String> profileResponse = client.send(HttpRequest.newBuilder()
                .uri(URI.create(SAINT_MAIN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        if (profileResponse.statusCode() < 200 || profileResponse.statusCode() >= 400) {
            throw new BusinessException(ErrorCode.INVALID_SSO_TOKEN);
        }

        return parseSaintProfile(profileResponse.body(), sIdno, cookieEmail);
    }

    private SaintProfile parseSaintProfile(String html, String fallbackStudentId, String fallbackEmail) {
        String text = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String studentId = firstText(match(text, "(?:학번|sIdno)\\s*[:：]?\\s*(\\d{6,12})"), fallbackStudentId);
        String name = firstText(
                match(text, "([가-힣A-Za-z]{2,30})\\s*님\\s*환영"),
                match(text, "(?:성명|이름)\\s*[:：]?\\s*([가-힣A-Za-z]{2,30})")
        );
        String department = match(text, "(?:소속|학과|학부|전공)\\s*[:：]?\\s*([^\\s]{2,100})");
        String email = firstText(fallbackEmail, matchSoongsilEmail(text));

        return new SaintProfile(studentId, defaultString(name), department, email);
    }

    private String extractCookies(HttpResponse<?> response) {
        return response.headers().allValues("set-cookie").stream()
                .map(cookie -> cookie.split(";", 2)[0])
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "; " + right);
    }

    static String extractEmailFromCookies(List<String> setCookies) {
        List<String> candidates = new ArrayList<>();
        for (String setCookie : setCookies) {
            collectEmailCandidates(setCookie, candidates, 0);
        }
        return candidates.stream()
                .filter(SsoService::isSoongsilEmail)
                .findFirst()
                .orElse(null);
    }

    String resolveEmail(String ssoEmail, String studentId) {
        if (isSoongsilEmail(ssoEmail)) {
            return ssoEmail.trim().toLowerCase();
        }
        return (studentId + "@" + SOONGSIL_MAIL_DOMAIN).toLowerCase();
    }

    private static boolean isSoongsilEmail(String email) {
        if (!hasTextStatic(email)) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        return EMAIL_PATTERN.matcher(normalized).matches() && normalized.endsWith("@" + SOONGSIL_MAIL_DOMAIN);
    }

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            builder.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
        }
        String code = builder.toString();
        if (organizationRepository.findByInviteCode(code).isPresent()) {
            return generateInviteCode();
        }
        return code;
    }

    private String redirect(String path, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(frontendUrl.replaceAll("/+$", "")).append(path);
        if (!params.isEmpty()) {
            builder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!hasText(entry.getValue())) continue;
                if (!first) builder.append("&");
                builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
                first = false;
            }
        }
        return builder.toString();
    }

    private String match(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String matchEmail(String text) {
        if (!hasTextStatic(text)) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private static void collectEmailCandidates(String text, List<String> candidates, int depth) {
        if (!hasTextStatic(text) || depth > MAX_COOKIE_DECODE_DEPTH) {
            return;
        }

        String decoded = decodeCookieText(text);
        Matcher emailMatcher = EMAIL_PATTERN.matcher(decoded);
        while (emailMatcher.find()) {
            candidates.add(emailMatcher.group().trim().toLowerCase());
        }

        if (depth == MAX_COOKIE_DECODE_DEPTH) {
            return;
        }

        Matcher tokenMatcher = ENCODED_TOKEN_PATTERN.matcher(decoded);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group();
            for (String decodedToken : decodeBase64Candidates(token)) {
                if (!decodedToken.equals(decoded)) {
                    collectEmailCandidates(decodedToken, candidates, depth + 1);
                }
            }
        }
    }

    private static List<String> decodeBase64Candidates(String value) {
        List<String> decodedValues = new ArrayList<>();
        String normalized = value.trim();
        if (normalized.length() < 8 || normalized.length() > 4096) {
            return decodedValues;
        }

        decodeBase64(normalized, false).ifPresent(decodedValues::add);
        decodeBase64(normalized, true).ifPresent(decoded -> {
            if (!decodedValues.contains(decoded)) {
                decodedValues.add(decoded);
            }
        });
        return decodedValues;
    }

    private static Optional<String> decodeBase64(String value, boolean urlSafe) {
        try {
            String padded = padBase64(value);
            byte[] bytes = urlSafe
                    ? Base64.getUrlDecoder().decode(padded)
                    : Base64.getDecoder().decode(padded);
            if (!isMostlyText(bytes)) {
                return Optional.empty();
            }
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private static boolean isMostlyText(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 4096) {
            return false;
        }
        int control = 0;
        for (byte b : bytes) {
            int value = b & 0xff;
            if (value < 0x09 || (value > 0x0d && value < 0x20)) {
                control++;
            }
        }
        return control <= Math.max(1, bytes.length / 20);
    }

    private static String matchSoongsilEmail(String text) {
        if (!hasTextStatic(text)) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        while (matcher.find()) {
            String email = matcher.group().trim();
            if (isSoongsilEmail(email)) {
                return email;
            }
        }
        return null;
    }

    private static String decodeCookieText(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value;
        for (int i = 0; i < 3; i++) {
            try {
                String next = URLDecoder.decode(decoded.replace("+", "%2B"), StandardCharsets.UTF_8);
                if (next.equals(decoded)) {
                    break;
                }
                decoded = next;
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return decoded;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean hasTextStatic(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String encode(String value) {
        return URLEncoder.encode(defaultString(value), StandardCharsets.UTF_8);
    }

    private record SaintProfile(String studentId, String name, String department, String email) {
    }
}
