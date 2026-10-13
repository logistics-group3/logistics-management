package com.sparta.logistics.config.refresh;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * config.watch.* — 설정 파일 변경 감지 후 서비스 자동 refresh
 * (주기는 {@code config.watch.interval-ms}, {@link ConfigFileWatcher}의 @Scheduled가 직접 읽는다)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "config.watch")
public class ConfigWatchProperties {

    /** false면 감지·refresh를 하지 않는다 (서비스는 재시작해야 새 설정을 받음) */
    private boolean enabled = true;

    /** 서비스 인스턴스 refresh 호출 연결 타임아웃 */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** refresh 응답 대기 (설정을 다시 받고 빈을 재바인딩하는 시간) */
    private Duration readTimeout = Duration.ofSeconds(15);
}
