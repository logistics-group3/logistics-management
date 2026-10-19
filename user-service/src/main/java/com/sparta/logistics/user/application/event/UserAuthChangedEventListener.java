package com.sparta.logistics.user.application.event;

import com.sparta.logistics.user.application.UserAuthCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserAuthChangedEventListener {

    private final UserAuthCache userAuthCache;

    /**
     * Note: 커밋 뒤에 지운다. 커밋 전에 지우면 그 사이 Gateway가 아직 바뀌지 않은 DB 값을 다시 읽어 캐시에 넣을 수 있다.
     * 롤백되면 지우지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAuthChanged(UserAuthChangedEvent event) {
        userAuthCache.evict(event.userId());
    }
}
