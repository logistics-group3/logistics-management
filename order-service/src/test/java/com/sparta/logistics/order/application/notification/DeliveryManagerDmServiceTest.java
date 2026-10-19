package com.sparta.logistics.order.application.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.application.event.DeliveryManagerDmEventListener;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryManagerDmServiceTest {
    private final NotificationDirectory directory = mock(NotificationDirectory.class);
    private final SlackDirectMessageGateway messages = mock(SlackDirectMessageGateway.class);
    private final DeliveryManagerDmService service = new DeliveryManagerDmService(directory, messages);
    private final OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), "공급", "수령", "상품", 2, "요청", UUID.randomUUID());

    @Test @DisplayName("동일 담당자 여러 구간과 같은 이메일은 이벤트당 한 번만 시도한다")
    void deduplicates() {
        UUID one = UUID.randomUUID(); UUID two = UUID.randomUUID();
        targets("PICKUP_WAITING", List.of(one, one, two));
        when(directory.findContact(one)).thenReturn(new NotificationDirectory.Contact(one, true, "driver@example.com"));
        when(directory.findContact(two)).thenReturn(new NotificationDirectory.Contact(two, true, "DRIVER@example.com"));
        service.notifyManagers(event);
        verify(messages, times(1)).send(anyString(), contains(event.orderId().toString()));
    }

    @Test @DisplayName("연락처 미설정·해제·조회 실패는 다른 담당자의 발송을 막지 않는다")
    void isolatesRecipientFailures() {
        UUID one = UUID.randomUUID(); UUID two = UUID.randomUUID(); UUID three = UUID.randomUUID();
        targets("PICKUP_WAITING", List.of(one, two, three));
        when(directory.findContact(one)).thenThrow(new RuntimeException("private-email@example.com"));
        when(directory.findContact(two)).thenReturn(new NotificationDirectory.Contact(two, false, "released@example.com"));
        when(directory.findContact(three)).thenReturn(new NotificationDirectory.Contact(three, true, "active@example.com"));
        service.notifyManagers(event);
        verify(messages).send(eq("active@example.com"), anyString());
        verifyNoMoreInteractions(messages);
    }

    @Test @DisplayName("이메일이 없는 담당자는 알림만 생략한다")
    void skipsAbsentEmail() {
        UUID one = UUID.randomUUID(); targets("PICKUP_WAITING", List.of(one));
        when(directory.findContact(one)).thenReturn(new NotificationDirectory.Contact(one, true, null));
        service.notifyManagers(event); verifyNoInteractions(messages);
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELED", "DELIVERED", "UNKNOWN"})
    @DisplayName("취소·완료·알 수 없는 배송 상태에는 새 배정 알림을 보내지 않는다")
    void skipsInactive(String status) {
        targets(status, List.of(UUID.randomUUID())); service.notifyManagers(event); verifyNoInteractions(messages);
        verify(directory, never()).findContact(any());
    }

    @Test @DisplayName("다른 주문의 배송 응답은 수신자로 사용하지 않는다")
    void validatesAssociation() {
        when(directory.findTargets(event.deliveryId())).thenReturn(new NotificationDirectory.Targets(event.deliveryId(), UUID.randomUUID(), "PICKUP_WAITING", List.of(UUID.randomUUID())));
        assertThatThrownBy(() -> service.notifyManagers(event)).isInstanceOf(SlackDmDeliveryException.class);
        verifyNoInteractions(messages);
    }

    @Test @DisplayName("작업 제출 거절을 리스너 밖으로 전파하지 않는다")
    void handlesQueueRejection() {
        DeliveryManagerDmSender sender = mock(DeliveryManagerDmSender.class);
        doThrow(new RejectedExecutionException()).when(sender).send(event);
        new DeliveryManagerDmEventListener(sender).onOrderCreated(event);
    }
    private void targets(String status, List<UUID> ids) {
        when(directory.findTargets(event.deliveryId())).thenReturn(new NotificationDirectory.Targets(event.deliveryId(), event.orderId(), status, ids));
    }
}
