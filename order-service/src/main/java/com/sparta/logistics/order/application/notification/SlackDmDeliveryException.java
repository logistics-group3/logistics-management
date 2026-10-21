package com.sparta.logistics.order.application.notification;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;

/** 외부 응답 본문·토큰·이메일을 예외 메시지에 포함하지 않는다. */
public class SlackDmDeliveryException extends BusinessException {
    public enum Reason { HTTP_FAILURE, TRANSPORT_FAILURE, USER_NOT_FOUND, RECIPIENT_UNAVAILABLE,
        WORKSPACE_MISMATCH, PERMISSION_DENIED, RATE_LIMITED, INVALID_RESPONSE, SLACK_API_ERROR }
    private final Reason reason;

    public SlackDmDeliveryException(Reason reason) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, "Slack DM 발송 실패: " + reason);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
