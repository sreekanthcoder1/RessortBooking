package com.cottageai.service;

import com.cottageai.dto.response.AuthResponse;
import com.cottageai.entity.OtpToken;
import com.cottageai.entity.User;
import com.cottageai.exception.AuthException;
import com.cottageai.repository.OtpRepository;
import com.cottageai.repository.UserRepository;
import com.cottageai.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.otp.expiration-minutes:10}")
    private int otpExpirationMinutes;

    // ─── LOGIN ────────────────────────────────────────────────
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new AuthException("Invalid email or password");
        if (!user.getIsActive())
            throw new AuthException("Account is deactivated.");
        return buildAuthResponse(user);
    }

    // ─── SEND OTP (registration) ──────────────────────────────
    @Transactional
    public void sendOtp(String email) {
        otpRepository.deleteByEmail(email);
        String otp = generateOtp();
        saveOtp(email, otp, "REGISTER");
        emailService.sendOtpEmail(email, otp);
        log.info("OTP sent to: {}", email);
    }

    // ─── VERIFY OTP ───────────────────────────────────────────
    public boolean verifyOtp(String email, String otp) {
        OtpToken token = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new AuthException("No OTP found. Please request a new one."));
        if (token.isExpired()) throw new AuthException("OTP has expired.");
        if (!token.getOtp().equals(otp)) throw new AuthException("Invalid OTP.");
        return true;
    }

    // ─── COMPLETE REGISTRATION ────────────────────────────────
    @Transactional
    public AuthResponse completeRegistration(String email, String otp, String password, String name) {
        verifyOtp(email, otp);
        if (userRepository.existsByEmail(email))
            throw new AuthException("Email already registered.");
        markOtpUsed(email);
        User user = User.builder()
                .email(email).password(passwordEncoder.encode(password))
                .name(name).role(User.Role.USER)
                .authProvider(User.AuthProvider.LOCAL).build();
        user = userRepository.save(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());
        log.info("New user registered: {}", email);
        return buildAuthResponse(user);
    }

    // ─── FORGOT PASSWORD - Send OTP ───────────────────────────
    @Transactional
    public void sendForgotPasswordOtp(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("No account found with this email."));
        otpRepository.deleteByEmail(email);
        String otp = generateOtp();
        saveOtp(email, otp, "RESET");
        emailService.sendPasswordResetOtpEmail(email, otp);
        log.info("Password reset OTP sent to: {}", email);
    }

    // ─── RESET PASSWORD ───────────────────────────────────────
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        verifyOtp(email, otp);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found."));
        markOtpUsed(email);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        emailService.sendPasswordChangedEmail(email, user.getName());
        log.info("Password reset for: {}", email);
    }

    // ─── ADMIN REGISTER ───────────────────────────────────────
    @Transactional
    public AuthResponse registerAdmin(String email, String password, String name) {
        if (userRepository.existsByEmail(email))
            throw new AuthException("Email already registered.");
        User admin = User.builder()
                .email(email).password(passwordEncoder.encode(password))
                .name(name).role(User.Role.ADMIN)
                .authProvider(User.AuthProvider.LOCAL).build();
        admin = userRepository.save(admin);
        log.info("New admin registered: {}", email);
        return buildAuthResponse(admin);
    }

    // ─── SUPER ADMIN SETUP - Send OTP (one-time) ─────────────
    @Transactional
    public void sendSuperAdminSetupOtp(String email) {
        // Only allow if no super admin exists yet
        boolean superAdminExists = userRepository.existsByRole(User.Role.SUPER_ADMIN);
        if (superAdminExists)
            throw new AuthException("Super admin already exists. Setup can only be done once.");
        otpRepository.deleteByEmail(email);
        String otp = generateOtp();
        saveOtp(email, otp, "SUPER_SETUP");
        emailService.sendSuperAdminSetupOtpEmail(email, otp);
        log.info("Super admin setup OTP sent to: {}", email);
    }

    // ─── SUPER ADMIN SETUP - Complete ────────────────────────
    @Transactional
    public void setupSuperAdmin(String email, String otp, String password, String name, String phone) {
        boolean superAdminExists = userRepository.existsByRole(User.Role.SUPER_ADMIN);
        if (superAdminExists)
            throw new AuthException("Super admin already exists.");
        verifyOtp(email, otp);
        markOtpUsed(email);
        User superAdmin = User.builder()
                .email(email).password(passwordEncoder.encode(password))
                .name(name).phone(phone).role(User.Role.SUPER_ADMIN)
                .authProvider(User.AuthProvider.LOCAL).build();
        userRepository.save(superAdmin);
        emailService.sendWelcomeEmail(email, name);
        log.info("Super admin setup complete: {}", email);
    }

    // ─── GOOGLE AUTH ──────────────────────────────────────────
    @Transactional
    public AuthResponse processGoogleAuth(String googleId, String email, String name) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.findByEmail(email).orElse(null));
        if (user == null) {
            user = User.builder().email(email).name(name).googleId(googleId)
                    .role(User.Role.USER).authProvider(User.AuthProvider.GOOGLE).build();
            user = userRepository.save(user);
            emailService.sendWelcomeEmail(email, name);
        } else if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            userRepository.save(user);
        }
        return buildAuthResponse(user);
    }

    // ─── CLEANUP ──────────────────────────────────────────────
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredOtps() {
        otpRepository.deleteExpiredTokens(LocalDateTime.now());
    }

    // ─── HELPERS ──────────────────────────────────────────────
    private void saveOtp(String email, String otp, String purpose) {
        OtpToken token = OtpToken.builder()
                .email(email).otp(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .build();
        otpRepository.save(token);
    }

    private void markOtpUsed(String email) {
        otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(t -> { t.setIsUsed(true); otpRepository.save(t); });
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user))
                .user(AuthResponse.UserInfo.from(user))
                .build();
    }

    private String generateOtp() {
        return String.valueOf(100000 + new SecureRandom().nextInt(900000));
    }
}
