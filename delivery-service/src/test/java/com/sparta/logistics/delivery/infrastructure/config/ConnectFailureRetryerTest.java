package com.sparta.logistics.delivery.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 연결 실패만 재시도 대상이고 읽기 타임아웃은 아니다(결정 로그 26). Feign은 둘 다 RetryableException으로 감싸므로 원인으로 구분한다. */
class ConnectFailureRetryerTest {

    @Test
    @DisplayName("연결 거부와 연결 타임아웃은 재시도한다")
    void connectFailures() {
        assertThat(ConnectFailureRetryer.isConnectFailure(new RuntimeException(new ConnectException("Connection refused")))).isTrue();
        assertThat(ConnectFailureRetryer.isConnectFailure(new RuntimeException(new SocketTimeoutException("Connect timed out")))).isTrue();
    }

    @Test
    @DisplayName("읽기 타임아웃은 재시도하지 않는다(상대가 느린 것이라 다시 보내면 스레드만 더 묶인다)")
    void readTimeoutIsNotRetried() {
        assertThat(ConnectFailureRetryer.isConnectFailure(new RuntimeException(new SocketTimeoutException("Read timed out")))).isFalse();
        assertThat(ConnectFailureRetryer.isConnectFailure(new RuntimeException("boom"))).isFalse();
    }
}
