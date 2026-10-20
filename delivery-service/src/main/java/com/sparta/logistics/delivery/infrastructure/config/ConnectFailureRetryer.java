package com.sparta.logistics.delivery.infrastructure.config;

import feign.RetryableException;
import feign.Retryer;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 연결 실패(연결 거부, 연결 타임아웃, 주소를 찾지 못함)만 재시도하는 Feign Retryer.
 *
 * <p>Note: 읽기 타임아웃은 재시도하지 않는다. 요청은 상대에 닿았고 상대가 느린 것이라, 다시 보내면 느린 상대에 일만 더 얹고
 * 우리 스레드는 그만큼 더 오래 묶인다(2026-10-19 S1에서 read 20초 × 3번 = 최대 60초 묶임). 연결 실패는 요청이 상대에 닿지
 * 않았으므로 다시 보내도 안전하고, 로드밸런서가 다른 인스턴스를 고를 수 있다.
 */
public class ConnectFailureRetryer implements Retryer {

    private final int maxAttempts;
    private final long backoffMillis;
    private int attempt = 1;

    public ConnectFailureRetryer(int maxAttempts, long backoffMillis) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (!isConnectFailure(e) || attempt++ >= maxAttempts) {
            throw e;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Note: Feign은 연결 타임아웃과 읽기 타임아웃을 같은 RetryableException으로 감싼다. 원인 예외로 구분한다.
     * 기본 클라이언트(HttpURLConnection)는 연결 타임아웃을 메시지 "Connect timed out"인 SocketTimeoutException으로 던진다.
     */
    static boolean isConnectFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof NoRouteToHostException || t instanceof UnknownHostException) {
                return true;
            }
            if (t instanceof SocketTimeoutException && t.getMessage() != null
                    && t.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Retryer clone() {
        return new ConnectFailureRetryer(maxAttempts, backoffMillis);
    }
}
