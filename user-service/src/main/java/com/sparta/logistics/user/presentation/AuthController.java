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

/** 인증 API (Public — Gateway 화이트리스트) */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** U-01 회원가입 */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(authService.signup(request.toCommand()));
        return ApiResponse.created("회원가입이 완료되었습니다.", response);
    }

    /** U-02 로그인 (JWT 발급) */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = LoginResponse.from(authService.login(request.username(), request.password()));
        return ApiResponse.ok("로그인에 성공했습니다.", response);
    }
}
