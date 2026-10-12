package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.AuthService;
import com.sparta.logistics.user.presentation.dto.LoginRequest;
import com.sparta.logistics.user.presentation.dto.SignupRequest;
import com.sparta.logistics.user.presentation.dto.SignupResponse;
import com.sparta.logistics.user.presentation.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입·로그인 (Public)")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "U-01 회원가입", description = "가입 즉시 활성화, 권한 USER")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(authService.signup(request.toCommand()));
        return ApiResponse.created("회원가입이 완료되었습니다.", response);
    }

    @Operation(summary = "U-02 로그인", description = "JWT(HS256, 60분) 발급")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = TokenResponse.from(authService.login(request.toCommand()));
        return ApiResponse.ok("로그인에 성공했습니다.", response);
    }
}
