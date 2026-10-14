package com.sparta.logistics.common.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 실제 PostgreSQL 16 컨테이너로 서비스를 띄우는 통합 테스트의 공통 부모. Docker가 실행 중이어야 한다.
 * config-server와 Eureka는 끄고, 스키마는 테스트마다 Entity로 새로 만든다.
 *
 * Note: 컨테이너를 static 필드로 한 번만 띄워 모든 테스트 클래스가 공유한다(싱글턴 컨테이너 패턴).
 * {@code @ServiceConnection}이 컨테이너의 접속 정보(url, username, password)를 spring.datasource.* 대신 넣어 준다.
 * H2 같은 대체 DB를 쓰지 않는 이유: 부분 인덱스, 네이티브 쿼리, 유니크 제약 동작이 운영 DB와 같아야 검증 의미가 있다.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
