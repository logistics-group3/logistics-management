package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.application.dto.LoginResult;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.SignupResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import com.sparta.logistics.user.infrastructure.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** U-01 회원가입 */
    @Transactional
    public SignupResult signup(SignupCommand command) {
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

        try {
            // 동시에 같은 username 으로 가입하는 경우 UNIQUE 제약 위반을 409 로 변환
            return SignupResult.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
    }

    /** U-02 로그인. 아이디 없음·비밀번호 불일치·탈퇴 사용자 모두 같은 401 LOGIN_FAILED (계정 존재 여부 노출 방지) */
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .filter(found -> passwordEncoder.matches(password, found.getPassword()))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.LOGIN_FAILED));

        return new LoginResult(jwtProvider.createAccessToken(user), TOKEN_TYPE, jwtProvider.getExpirationSeconds());
    }
}
