package com.cowork.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPolicyConsentRepository extends JpaRepository<UserPolicyConsent, Long> {

    Optional<UserPolicyConsent> findFirstByUserIdOrderByAgreedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndTermsVersionAndPrivacyVersionAndTermsAgreedIsTrueAndPrivacyAgreedIsTrue(
            Long userId,
            String termsVersion,
            String privacyVersion
    );
}
