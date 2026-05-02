package com.cowork.auth.dto;

import lombok.Getter;

@Getter
public class SsoRegisterRequest {
    private String tempToken;
    private String email;
    private boolean isCouncilMember;
    private String cohortLabel;
    private String department;
}