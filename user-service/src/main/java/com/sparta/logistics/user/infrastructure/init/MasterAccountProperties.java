package com.sparta.logistics.user.infrastructure.init;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 초기 MASTER 계정. 값은 config-repo/user-service.yml이 환경 변수 MASTER_USERNAME, MASTER_PASSWORD에서 받는다. */
@ConfigurationProperties(prefix = "master")
public record MasterAccountProperties(String username, String password, @DefaultValue("U0MASTER") String slackId) {
}
