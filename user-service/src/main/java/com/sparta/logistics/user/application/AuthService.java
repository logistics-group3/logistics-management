package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.LoginResult;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.UserResult;
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

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    /** U-01 회원가입. 권한은 항상 USER로 생성된다. */
    @Transactional
    public UserResult signup(SignupCommand command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.signUp(
                command.username(),
                passwordEncoder.encode(command.password()),
                command.name(),
                command.slackId(),
                command.affiliationType(),
                command.affiliationName()
        );
        try {
            return UserResult.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 username으로 가입한 경우 부분 유니크 인덱스에서 걸린다.
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
    }

    /** U-02 로그인. 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않는다. */
    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .filter(found -> passwordEncoder.matches(password, found.getPassword()))
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_FAILED));
        return new LoginResult(tokenIssuer.issue(user), TOKEN_TYPE, tokenIssuer.expiresInSeconds());
    }
}
