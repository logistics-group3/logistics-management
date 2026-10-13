package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 검증 규칙은 SA 8.5. */
public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]{4,10}$", message = "4~10자의 소문자와 숫자만 사용할 수 있습니다.")
        String username,

        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,15}$",
                message = "8~15자, 대문자·소문자·숫자·특수문자를 각각 1자 이상 포함해야 합니다.")
        String password,

        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 50) String slackId,
        @NotNull AffiliationType affiliationType,
        @NotBlank @Size(max = 100) String affiliationName
) {

    public SignupCommand toCommand() {
        return new SignupCommand(username, password, name, slackId, affiliationType, affiliationName);
    }
}
