package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.LoginCommand;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.SignupResult;
import com.sparta.logistics.user.application.dto.TokenResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    @Transactional
    public SignupResult signup(SignupCommand command) {
        if (userRepository.existsByUsernameIncludingDeleted(command.username())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.signup(command.username(), passwordEncoder.encode(command.password()), command.name(),
                command.slackId(), command.affiliationType(), command.affiliationName());
        try {
            return SignupResult.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // Note: 존재 확인과 저장 사이에 같은 username 가입이 끼어들 수 있다. 최종 방어선은 DB 유니크 제약이고, 그 위반을 같은 409로 바꾼다.
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
    }

    /** 없는 사용자, 탈퇴 사용자, 비밀번호 불일치를 모두 같은 LOGIN_FAILED로 응답해 계정 존재 여부를 노출하지 않는다. */
    @Transactional(readOnly = true)
    public TokenResult login(LoginCommand command) {
        User user = userRepository.findByUsername(command.username())
                .filter(found -> passwordEncoder.matches(command.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_FAILED));
        return TokenResult.bearer(tokenIssuer.issue(user), tokenIssuer.expirationSeconds());
    }
}
