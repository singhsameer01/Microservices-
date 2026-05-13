package com.learn.user.controller;

import com.learn.user.dto.AuthResponse;
import com.learn.user.dto.LoginRequest;
import com.learn.user.dto.RegisterRequest;
import com.learn.user.dto.UserResponse;
import com.learn.user.model.User;
import com.learn.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
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
        // JWT generation goes here — wire in JwtUtil once you implement it
        String token = "implement-jwt-here";
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole()));
    }
}
