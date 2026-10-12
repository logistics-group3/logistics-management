package com.sparta.logistics.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.application.dto.LoginCommand;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.TokenResult;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.exception.UserErrorCode;
import com.sparta.logistics.user.domain.model.AffiliationType;
import com.sparta.logistics.user.domain.model.User;
import com.sparta.logistics.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private final SignupCommand signup = new SignupCommand(
            "hong01", "Sparta!2026", "홍길동", "U01ABCDE2FG", AffiliationType.COMPANY, "부산수산도매");

    @Test
    @DisplayName("회원가입하면 USER 권한, 배송담당자 아님으로 저장된다")
    void signup() {
        // given
        given(userRepository.existsByUsernameIncludingDeleted("hong01")).willReturn(false);
        given(passwordEncoder.encode("Sparta!2026")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        UserResult result = authService.signup(signup);

        // then
        assertThat(result.username()).isEqualTo("hong01");
        assertThat(result.role()).isEqualTo(UserRole.USER);
        assertThat(result.deliveryManager()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 사용자의 username으로도 가입할 수 없다 (DUPLICATE_USERNAME)")
    void signupDuplicate() {
        // given
        given(userRepository.existsByUsernameIncludingDeleted("hong01")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(signup))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).hasCode(UserErrorCode.DUPLICATE_USERNAME)).isTrue());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED")
    void loginWrongPassword() {
        // given
        User user = User.signup("hong01", "hashed", "홍길동", "U01", AffiliationType.HUB, "서울특별시 센터");
        given(userRepository.findByUsername("hong01")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(new LoginCommand("hong01", "wrong")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).hasCode(UserErrorCode.LOGIN_FAILED)).isTrue());
    }

    @Test
    @DisplayName("없는(또는 탈퇴한) 사용자도 LOGIN_FAILED")
    void loginUnknownUser() {
        given(userRepository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("nobody", "Sparta!2026")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).hasCode(UserErrorCode.LOGIN_FAILED)).isTrue());
    }

    @Test
    @DisplayName("로그인 성공 시 토큰과 만료 시간(초)을 돌려준다")
    void login() {
        // given
        User user = User.signup("hong01", "hashed", "홍길동", "U01", AffiliationType.HUB, "서울특별시 센터");
        given(userRepository.findByUsername("hong01")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Sparta!2026", "hashed")).willReturn(true);
        given(tokenProvider.createAccessToken(any(), anyString(), any())).willReturn("jwt");
        given(tokenProvider.getExpirationSeconds()).willReturn(3600L);

        // when
        TokenResult result = authService.login(new LoginCommand("hong01", "Sparta!2026"));

        // then
        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.expiresIn()).isEqualTo(3600L);
    }
}
