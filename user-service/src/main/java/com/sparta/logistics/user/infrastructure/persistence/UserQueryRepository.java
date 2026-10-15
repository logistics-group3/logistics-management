package com.sparta.logistics.user.infrastructure.persistence;

import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * QueryDSL로 구현하는 검색 기능. UserJpaRepository가 이 인터페이스도 상속하면 Spring Data가 이름 뒤에 Impl이 붙은
 * UserQueryRepositoryImpl을 찾아 search 호출을 그 클래스로 보낸다(커스텀 리포지토리 fragment).
 */
public interface UserQueryRepository {

    Page<User> search(UserSearchCondition condition, Pageable pageable);
}
