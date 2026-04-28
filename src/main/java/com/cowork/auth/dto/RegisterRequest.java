package com.cowork.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class RegisterRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String name;

    @NotNull
    private RegisterMode mode;

    // mode = CREATE 시 필요
    private String organizationName;
    private String cohortLabel;

    // mode = JOIN 시 필요
    private String inviteCode;

    public enum RegisterMode {
        CREATE, JOIN
    }
}
