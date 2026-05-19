package com.learn.user.dto;

public record AuthResponse(
        String token,
        String refreshToken,
        String username,
        String role
) {}
