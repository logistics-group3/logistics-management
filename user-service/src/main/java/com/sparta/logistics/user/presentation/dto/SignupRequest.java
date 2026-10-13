package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** U-01 회원가입 요청 (SA 8.5 검증 규칙) */
public record SignupRequest(

        @NotBlank(message = "아이디는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9]{4,10}$", message = "4~10자의 소문자와 숫자만 사용할 수 있습니다.")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,15}$",
                message = "8~15자, 대문자·소문자·숫자·특수문자를 각각 1자 이상 포함해야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "Slack ID는 필수입니다.")
        @Size(max = 50, message = "Slack ID는 50자 이하여야 합니다.")
        String slackId,

        @NotNull(message = "소속 구분은 필수입니다. (COMPANY, HUB)")
        AffiliationType affiliationType,

        @NotBlank(message = "소속명은 필수입니다.")
        @Size(max = 100, message = "소속명은 100자 이하여야 합니다.")
        String affiliationName
) {

    public SignupCommand toCommand() {
        return new SignupCommand(username, password, name, slackId, affiliationType, affiliationName);
    }
}
