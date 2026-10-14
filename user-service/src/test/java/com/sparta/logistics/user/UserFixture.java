package com.sparta.logistics.user;

import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.User;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    private UserFixture() {
    }

    public static User user(String username, String encodedPassword) {
        User user = User.signup(username, encodedPassword, "홍길동", "U01ABCDE2FG", AffiliationType.COMPANY, "부산수산도매");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
