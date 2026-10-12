package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.LoginCommand;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.TokenResult;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.exception.UserErrorCode;
import com.sparta.logistics.user.domain.model.User;
import com.sparta.logistics.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 (U-01 회원가입, U-02 로그인) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    /** U-01. 가입 즉시 활성화, 권한 USER. username은 탈퇴 사용자 것도 재사용 불가 */
    @Transactional
    public UserResult signup(SignupCommand command) {
        if (userRepository.existsByUsernameIncludingDeleted(command.username())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.signup(
                command.username(),
                passwordEncoder.encode(command.password()),
                command.name(),
                command.slackId(),
                command.affiliationType(),
                command.affiliationName()
        );
        return UserResult.from(userRepository.save(user));
    }

    /** U-02. 없는 사용자·탈퇴 사용자·비밀번호 불일치는 모두 LOGIN_FAILED (어떤 이유인지 노출하지 않음) */
    public TokenResult login(LoginCommand command) {
        User user = userRepository.findByUsername(command.username())
                .filter(found -> passwordEncoder.matches(command.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_FAILED));
        String token = tokenProvider.createAccessToken(user.getUserId(), user.getUsername(), user.getRole());
        return new TokenResult(token, tokenProvider.getExpirationSeconds());
    }
}
