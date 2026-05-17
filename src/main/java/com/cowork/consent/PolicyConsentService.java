package com.cowork.consent;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PolicyConsentService {

    public static final String CURRENT_TERMS_VERSION = "2026-05-17";
    public static final String CURRENT_PRIVACY_VERSION = "2026-05-17";

    private final UserPolicyConsentRepository consentRepository;

    @Transactional(readOnly = true)
    public ConsentStatus getStatus(Long userId) {
        boolean consented = hasCurrentConsent(userId);
        UserPolicyConsent latest = consentRepository.findFirstByUserIdOrderByAgreedAtDescIdDesc(userId)
                .orElse(null);

        return new ConsentStatus(
                !consented,
                CURRENT_TERMS_VERSION,
                CURRENT_PRIVACY_VERSION,
                latest != null ? latest.getTermsVersion() : null,
                latest != null ? latest.getPrivacyVersion() : null,
                latest != null ? latest.getAgreedAt() : null
        );
    }

    @Transactional(readOnly = true)
    public boolean isConsentRequired(Long userId) {
        return !hasCurrentConsent(userId);
    }

    @Transactional
    public ConsentStatus agree(Long userId, boolean termsAgreed, boolean privacyAgreed,
                               String termsVersion, String privacyVersion, String ipAddress, String userAgent) {
        if (!termsAgreed || !privacyAgreed ||
                !CURRENT_TERMS_VERSION.equals(termsVersion) ||
                !CURRENT_PRIVACY_VERSION.equals(privacyVersion)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        consentRepository.save(UserPolicyConsent.builder()
                .userId(userId)
                .termsVersion(CURRENT_TERMS_VERSION)
                .privacyVersion(CURRENT_PRIVACY_VERSION)
                .termsAgreed(true)
                .privacyAgreed(true)
                .agreedAt(LocalDateTime.now())
                .ipAddress(trimToLength(ipAddress, 45))
                .userAgent(trimToLength(userAgent, 500))
                .build());

        return getStatus(userId);
    }

    private boolean hasCurrentConsent(Long userId) {
        return consentRepository.existsByUserIdAndTermsVersionAndPrivacyVersionAndTermsAgreedIsTrueAndPrivacyAgreedIsTrue(
                userId,
                CURRENT_TERMS_VERSION,
                CURRENT_PRIVACY_VERSION
        );
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public record ConsentStatus(
            boolean consentRequired,
            String termsVersion,
            String privacyVersion,
            String acceptedTermsVersion,
            String acceptedPrivacyVersion,
            LocalDateTime agreedAt
    ) {
    }
}
