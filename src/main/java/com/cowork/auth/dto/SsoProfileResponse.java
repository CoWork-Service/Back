package com.cowork.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SsoProfileResponse {
    private String studentId;
    private String name;
    private String department;
    private String email;
}