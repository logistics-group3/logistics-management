package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.user.domain.AffiliationType;

public record SignupCommand(
        String username,
        String password,
        String name,
        String slackId,
        AffiliationType affiliationType,
        String affiliationName
) {
}
