package com.learn.user.controller;

import com.learn.user.dto.AuthResponse;
import com.learn.user.dto.LoginRequest;
import com.learn.user.dto.RegisterRequest;
import com.learn.user.dto.UserResponse;
import com.learn.user.model.User;
import com.learn.user.security.JwtUtil;
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
 * <p>POST /api/v1/auth/register — create a new account</p>
 * <p>POST /api/v1/auth/login   — authenticate and receive a JWT</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Registers a new user. Returns 201 Created with the user profile on success.
     * Returns 409 Conflict if username or email is already taken.
     *
     * @param request validated registration payload
     * @return the created user's profile (never includes the password)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    /**
     * Authenticates a user and issues a signed JWT token.
     * The token is valid for 24 hours and carries the user's role as a claim.
     *
     * @param request validated login credentials
     * @return an {@link AuthResponse} containing the JWT, username, and role
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Throws AuthenticationException (→ 401) if credentials are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userService.findByUsername(request.username());
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtUtil.generateToken(userDetails, user.getRole());

        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole()));
    }
}
