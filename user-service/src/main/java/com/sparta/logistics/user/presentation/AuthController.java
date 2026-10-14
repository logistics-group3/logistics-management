package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.AuthService;
import com.sparta.logistics.user.presentation.dto.LoginRequest;
import com.sparta.logistics.user.presentation.dto.LoginResponse;
import com.sparta.logistics.user.presentation.dto.SignupRequest;
import com.sparta.logistics.user.presentation.dto.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없이 호출되는 공개 API (Gateway PUBLIC_PATHS) */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** U-01 회원가입 */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.created(SignupResponse.from(authService.signup(request.toCommand())));
    }

    /** U-02 로그인 (JWT 발급) */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(LoginResponse.from(authService.login(request.username(), request.password())));
    }
}
