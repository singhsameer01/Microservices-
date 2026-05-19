package com.learn.user.controller;

import com.learn.user.dto.AuthResponse;
import com.learn.user.dto.LoginRequest;
import com.learn.user.dto.RefreshTokenRequest;
import com.learn.user.dto.RegisterRequest;
import com.learn.user.dto.UserResponse;
import com.learn.user.model.RefreshToken;
import com.learn.user.model.User;
import com.learn.user.security.JwtUtil;
import com.learn.user.service.RefreshTokenService;
import com.learn.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

/**
 * Handles user registration and JWT-based authentication.
 *
 * POST /api/v1/auth/register — create a new account
 * POST /api/v1/auth/login    — authenticate, receive access token (15 min) + refresh token (7 days)
 * POST /api/v1/auth/refresh  — exchange a valid refresh token for a new access + refresh token
 * POST /api/v1/auth/logout   — revoke the refresh token (access token expires naturally)
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService,
            RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        User user = userService.findByUsername(request.username());
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String accessToken = jwtUtil.generateToken(userDetails, user.getRole());
        String refreshToken = refreshTokenService.createToken(user).getToken();
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getRole()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.validateToken(request.refreshToken());
        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String newAccessToken = jwtUtil.generateToken(userDetails, user.getRole());
        // rotate: revoke consumed token, issue a new refresh token
        refreshTokenService.revokeToken(request.refreshToken());
        String newRefreshToken = refreshTokenService.createToken(user).getToken();
        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken, user.getUsername(), user.getRole()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
