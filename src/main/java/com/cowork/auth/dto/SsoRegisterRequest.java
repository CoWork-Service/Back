package com.cowork.auth.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class SsoRegisterRequest {

    private String tempToken;
    private String email;
    private boolean councilMember;
    private String cohortLabel;
    private String department;
    private String organizationName;
    private String inviteCode;
    private List<String> departments;
}
