package com.aiplatform.controller;

import com.aiplatform.dto.AuthDtos.LoginRequest;
import com.aiplatform.dto.AuthDtos.SignupRequest;
import com.aiplatform.dto.AuthDtos.JwtResponse;
import com.aiplatform.dto.AuthDtos.MessageResponse;
import com.aiplatform.dto.AuthDtos.TokenRefreshRequest;
import com.aiplatform.dto.AuthDtos.TokenRefreshResponse;
import com.aiplatform.exception.TokenRefreshException;
import com.aiplatform.model.RefreshToken;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.JwtUtils;
import com.aiplatform.security.UserDetailsImpl;
import com.aiplatform.service.RefreshTokenService;
import com.aiplatform.service.EmailService;
import com.aiplatform.service.FraudDetectionService;
import com.aiplatform.util.SecurityUtils;
import com.aiplatform.service.LoginAttemptService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final FraudDetectionService fraudDetectionService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            String jwt = jwtUtils.generateJwtToken(authentication);
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());
            
            // Reset failed login attempts on success
            loginAttemptService.loginSucceeded(loginRequest.getEmail());

            return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken(), userDetails.getId(),
                    userDetails.getEmail(), userDetails.getFullName(), roles));
                    
        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: Invalid email or password!"));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(new MessageResponse("Error: Your account is locked due to multiple failed login attempts. Please try again later."));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Error: Please verify your email address before logging in."));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signUpRequest, HttpServletRequest request) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        String verificationToken = UUID.randomUUID().toString();
        String referralCode = generateReferralCode(signUpRequest.getEmail());
        
        // Capture Security Metadata
        String remoteIp = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String finalIp = (xForwardedFor != null) ? xForwardedFor.split(",")[0] : remoteIp;

        User user = User.builder()
                .email(signUpRequest.getEmail())
                .fullName(signUpRequest.getFullName())
                .password(encoder.encode(signUpRequest.getPassword()))
                .emailVerified(false)
                .verificationToken(verificationToken)
                .referralCode(referralCode)
                .bonusCredits(0.0) // 0 initially
                .registrationIp(finalIp)
                .deviceFingerprint(signUpRequest.getDeviceFingerprint())
                .referredBy(signUpRequest.getReferralCode())
                .build();

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        user.setRoles(roles);
        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user, verificationToken);
        } catch (Exception e) {
            // Log but don't fail registration
        }

        return ResponseEntity.ok(new MessageResponse("User registered! Please check your email to activate your account and claim credits."));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestParam("token") String token) {
        return userRepository.findByVerificationToken(token)
                .map(user -> {
                    user.setEmailVerified(true);
                    user.setVerificationToken(null);
                    
                    if (!Boolean.TRUE.equals(user.getIsFraudFlagged())) {
                        fraudDetectionService.evaluateFraud(user);
                        
                        if (!"REJECTED".equals(user.getReferralStatus())) {
                            user.setBonusCredits(15.0); // Welcome bonus
                            
                            if (user.getReferredBy() != null && !user.getReferredBy().isBlank()) {
                                userRepository.findByReferralCode(user.getReferredBy()).ifPresent(referrer -> {
                                    referrer.setBonusCredits((referrer.getBonusCredits() != null ? referrer.getBonusCredits() : 0.0) + 50.0);
                                    userRepository.save(referrer);
                                    
                                    user.setBonusCredits(user.getBonusCredits() + 15.0); // Referral bonus
                                    user.setReferralStatus("APPROVED");
                                });
                            }
                        }
                    }
                    
                    userRepository.save(user);
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(frontendUrl + "/verify-success"))
                            .build();
                })
                .orElse(ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(frontendUrl + "/login?error=invalid_token"))
                        .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateTokenFromUsername(user.getEmail());
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, "Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logoutUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            refreshTokenService.deleteByUserId(userId);
        }
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }

    private String generateReferralCode(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.length() > 10) base = base.substring(0, 10);
        
        String random;
        String fullCode;
        do {
            random = UUID.randomUUID().toString().substring(0, 4);
            fullCode = base + "_" + random;
        } while (userRepository.existsByReferralCode(fullCode));
        
        return fullCode;
    }
}
