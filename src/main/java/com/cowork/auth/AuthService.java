package com.cowork.auth;

import com.cowork.auth.dto.LoginRequest;
import com.cowork.auth.dto.RegisterRequest;
import com.cowork.auth.dto.TokenResponse;
import com.cowork.cohort.*;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.organization.Organization;
import com.cowork.organization.OrganizationRepository;
import com.cowork.user.JoinStatus;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CohortRepository cohortRepository;
    private final CohortMemberRepository cohortMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 16;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (req.getMode() == RegisterRequest.RegisterMode.CREATE) {
            return registerAndCreateOrg(req);
        } else {
            return registerAndJoinOrg(req);
        }
    }

    private TokenResponse registerAndCreateOrg(RegisterRequest req) {
        Organization org = Organization.builder()
                .name(req.getOrganizationName())
                .inviteCode(generateInviteCode())
                .build();
        organizationRepository.save(org);

        User user = User.builder()
                .organization(org)
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .joinStatus(JoinStatus.ACTIVE)
                .build();
        userRepository.save(user);

        String cohortLabel = req.getCohortLabel() != null ? req.getCohortLabel() : "1기";
        int year = LocalDateTime.now().getYear();
        Cohort cohort = Cohort.builder()
                .organization(org)
                .label(cohortLabel)
                .year(year)
                .build();
        cohortRepository.save(cohort);

        CohortMember member = CohortMember.builder()
                .cohort(cohort)
                .user(user)
                .role(MemberRole.ADMIN)
                .build();
        cohortMemberRepository.save(member);

        return issueTokens(user);
    }

    private TokenResponse registerAndJoinOrg(RegisterRequest req) {
        Organization org = organizationRepository.findByInviteCode(normalizeInviteCode(req.getInviteCode()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        User user = User.builder()
                .organization(org)
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .joinStatus(JoinStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Cohort cohort = cohortRepository.findByOrganizationIdOrderByYearDesc(org.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));
        cohortMemberRepository.save(CohortMember.builder()
                .cohort(cohort)
                .user(user)
                .role(MemberRole.EDITOR)
                .build());

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getJoinStatus() == JoinStatus.PENDING) {
            throw new BusinessException(ErrorCode.PENDING_APPROVAL);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = refreshToken.getUser();
        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.deleteByUser(user);
    }

    public TokenResponse issueTokens(User user) {
        // 기존 refresh token 제거
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

        return new TokenResponse(accessToken, refreshTokenStr, user.getId(), user.getName(), user.getEmail(),
                user.getJoinStatus().name());
    }

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
        }
        String code = sb.toString();
        // 중복 체크
        if (organizationRepository.findByInviteCode(code).isPresent()) {
            return generateInviteCode();
        }
        return code;
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
        }
        return inviteCode.trim().toUpperCase();
    }
}
