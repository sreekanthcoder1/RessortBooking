package com.cottageai.controller;

import com.cottageai.dto.response.AuthResponse;
import com.cottageai.entity.User;
import com.cottageai.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class AuthController {

    private final AuthService authService;

    // ─── USER LOGIN ──────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.getEmail(), req.getPassword()));
    }

    // ─── SEND OTP (registration step 1) ─────────────────────
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody EmailRequest req) {
        authService.sendOtp(req.getEmail());
        return ResponseEntity.ok(Map.of("message", "OTP sent to " + req.getEmail(), "email", req.getEmail()));
    }

    // ─── VERIFY OTP ──────────────────────────────────────────
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        boolean valid = authService.verifyOtp(req.getEmail(), req.getOtp());
        return ResponseEntity.ok(Map.of("valid", valid, "email", req.getEmail()));
    }

    // ─── COMPLETE REGISTRATION ───────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.completeRegistration(
                req.getEmail(), req.getOtp(), req.getPassword(), req.getName()));
    }

    // ─── FORGOT PASSWORD - Send OTP ─────────────────────────
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, String>> forgotPasswordSendOtp(@Valid @RequestBody EmailRequest req) {
        authService.sendForgotPasswordOtp(req.getEmail());
        return ResponseEntity.ok(Map.of("message", "Password reset OTP sent to " + req.getEmail()));
    }

    // ─── FORGOT PASSWORD - Reset ─────────────────────────────
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getEmail(), req.getOtp(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    // ─── ADMIN REGISTER ──────────────────────────────────────
    @PostMapping("/admin/register")
    public ResponseEntity<AuthResponse> adminRegister(@Valid @RequestBody AdminRegisterRequest req) {
        return ResponseEntity.ok(authService.registerAdmin(req.getEmail(), req.getPassword(), req.getName()));
    }

    // ─── ADMIN LOGIN ─────────────────────────────────────────
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req.getEmail(), req.getPassword());
        if (response.getUser().getRole().equals("USER")) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(response);
    }

    // ─── SUPER ADMIN SETUP (one-time only) ──────────────────
    @PostMapping("/super-admin/setup")
    public ResponseEntity<Map<String, String>> superAdminSetup(@Valid @RequestBody SuperAdminSetupRequest req) {
        authService.setupSuperAdmin(req.getEmail(), req.getOtp(), req.getPassword(), req.getName(), req.getPhone());
        return ResponseEntity.ok(Map.of("message", "Super admin setup complete. Please login."));
    }

    // ─── SUPER ADMIN - Send setup OTP ───────────────────────
    @PostMapping("/super-admin/send-otp")
    public ResponseEntity<Map<String, String>> superAdminSendOtp(@Valid @RequestBody EmailRequest req) {
        authService.sendSuperAdminSetupOtp(req.getEmail());
        return ResponseEntity.ok(Map.of("message", "Setup OTP sent to " + req.getEmail()));
    }

    // ─── SUPER ADMIN LOGIN ───────────────────────────────────
    @PostMapping("/super-admin/login")
    public ResponseEntity<AuthResponse> superAdminLogin(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req.getEmail(), req.getPassword());
        if (!response.getUser().getRole().equals("SUPER_ADMIN")) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(response);
    }

    // ─── HEALTH ──────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }

    // ─── REQUEST CLASSES ─────────────────────────────────────
    @Data static class LoginRequest {
        @NotBlank @Email private String email;
        @NotBlank private String password;
    }
    @Data static class EmailRequest {
        @NotBlank @Email private String email;
    }
    @Data static class OtpVerifyRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min=6,max=6) private String otp;
    }
    @Data static class RegisterRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min=6,max=6) private String otp;
        @NotBlank @Size(min=8) private String password;
        @NotBlank private String name;
    }
    @Data static class ResetPasswordRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min=6,max=6) private String otp;
        @NotBlank @Size(min=8) private String newPassword;
    }
    @Data static class AdminRegisterRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min=8) private String password;
        @NotBlank private String name;
    }
    @Data static class SuperAdminSetupRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min=6,max=6) private String otp;
        @NotBlank @Size(min=8) private String password;
        @NotBlank private String name;
        private String phone;
    }
}
