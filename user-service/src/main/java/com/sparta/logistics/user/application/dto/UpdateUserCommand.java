package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.user.domain.AffiliationType;

/** U-06 입력. null인 값은 바꾸지 않는다. */
public record UpdateUserCommand(String name, String slackId, AffiliationType affiliationType, String affiliationName) {
}
