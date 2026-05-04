package com.cowork.auth.dto;

import lombok.Getter;

@Getter
public class SsoRegisterRequest {
    private String tempToken;
    private String email;
    private boolean councilMember;
    private String cohortLabel;
    private String department;
}