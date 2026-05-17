package com.cowork.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String name;
    private String email;
    private String joinStatus;
    private Boolean consentRequired;
    private String termsVersion;
    private String privacyVersion;

    public TokenResponse withoutTokens() {
        return new TokenResponse(null, null, userId, name, email, joinStatus,
                consentRequired, termsVersion, privacyVersion);
    }
}
