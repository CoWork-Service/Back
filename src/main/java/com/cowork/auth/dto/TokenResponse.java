package com.cowork.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String name;
    private String email;
    private String joinStatus;
}
