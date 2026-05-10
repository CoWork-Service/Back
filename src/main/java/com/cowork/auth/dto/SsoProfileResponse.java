package com.cowork.auth.dto;

public record SsoProfileResponse(
        String studentId,
        String name,
        String department,
        String email
) {
}
