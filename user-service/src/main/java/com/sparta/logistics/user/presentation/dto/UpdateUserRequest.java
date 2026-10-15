package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.UpdateUserCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * U-06 요청. 보낸 필드만 수정한다(PATCH).
 * Note: 필드를 보내지 않으면 null이라 바꾸지 않는다. @NotBlank는 null도 거절하므로 쓸 수 없고, 대신 @Pattern으로 "보냈다면 공백만은 안 된다"를
 * 검사한다. Bean Validation의 @Pattern·@Size는 null을 통과시킨다.
 */
public record UpdateUserRequest(
        @Size(max = 50) @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE) String name,
        @Size(max = 50) @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE) String slackId,
        AffiliationType affiliationType,
        @Size(max = 100) @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE) String affiliationName
) {

    private static final String NOT_BLANK = "(?s).*\\S.*";
    private static final String BLANK_MESSAGE = "빈 값으로 바꿀 수 없습니다.";

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(name, slackId, affiliationType, affiliationName);
    }
}
