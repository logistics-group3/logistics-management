package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.LoginCommand;
import jakarta.validation.constraints.NotBlank;

/** U-02 로그인 요청 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(username, password);
    }
}
