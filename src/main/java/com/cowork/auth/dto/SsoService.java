package com.cowork.auth;

import com.cowork.auth.dto.TokenResponse;
import com.cowork.cohort.CohortMember;
import com.cowork.cohort.CohortMemberRepository;
import com.cowork.cohort.MemberRole;
import com.cowork.user.JoinStatus;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SsoService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CohortMemberRepository cohortMemberRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public String handleSsoCallback(String sToken, String sIdno) {
        // sIdno = 학번
        Optional<User> existingUser = userRepository.findByStudentId(sIdno);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // 상태에 따라 분기
            if (user.getJoinStatus() == JoinStatus.PENDING) {
                return frontendUrl + "/pending";
            }
            if (user.getJoinStatus() == JoinStatus.ACTIVE) {
                TokenResponse token = issueTokens(user);
                return frontendUrl + "/main?accessToken=" + token.getAccessToken()
                        + "&refreshToken=" + token.getRefreshToken();
            }
            // REJECTED 등
            return frontendUrl + "/rejected";
        }

        // 신규 유저 → 온보딩으로 이동 (sToken, sIdno 전달)
        return frontendUrl + "/onboarding?sToken=" + sToken + "&sIdno=" + sIdno;
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
}