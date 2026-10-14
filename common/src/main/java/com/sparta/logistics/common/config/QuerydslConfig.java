package com.sparta.logistics.common.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 동적 검색 쿼리용 JPAQueryFactory. 서비스의 infrastructure 계층(예: HubQueryRepositoryImpl)에서 주입받아 쓴다.
 * 라이브러리는 관리가 이어지는 OpenFeign 포크(io.github.openfeign.querydsl)를 쓴다(ADR 0004).
 */
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
