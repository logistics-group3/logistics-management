package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** U-01. 형식 규칙은 SA 기준(username 4~10자)만 적용, 세부 규칙은 추후 결정. */
public record SignupRequest(
        @NotBlank @Size(min = 4, max = 10) String username,
        @NotBlank String password,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 50) String slackId,
        AffiliationType affiliationType,
        @Size(max = 100) String affiliationName
) {

    public SignupCommand toCommand() {
        return new SignupCommand(username, password, name, slackId, affiliationType, affiliationName);
    }
}
