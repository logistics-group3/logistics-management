package com.sparta.logistics.config.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTargetsTest {

    private final List<String> registered = List.of("GATEWAY-SERVICE", "USER-SERVICE", "ORDER-SERVICE", "CONFIG-SERVER");

    @Test
    @DisplayName("서비스 설정 파일이 바뀌면 그 서비스만 refresh한다 (프로파일 파일 포함)")
    void resolve_serviceFile() {
        assertThat(RefreshTargets.resolve(List.of("order-service.yml"), registered, "config-server"))
                .containsExactly("order-service");
        assertThat(RefreshTargets.resolve(List.of("gateway-service-docker.yml"), registered, "config-server"))
                .containsExactly("gateway-service");
    }

    @Test
    @DisplayName("공통 설정(application*.yml)이 바뀌면 config-server를 뺀 모든 서비스를 refresh한다")
    void resolve_commonFile() {
        assertThat(RefreshTargets.resolve(List.of("application-docker.yml"), registered, "config-server"))
                .containsExactly("gateway-service", "order-service", "user-service");
    }

    @Test
    @DisplayName("실행 중이 아닌 서비스의 설정이 바뀌면 대상이 없다")
    void resolve_notRunning() {
        assertThat(RefreshTargets.resolve(List.of("hub-service.yml", "README.md"), registered, "config-server"))
                .isEmpty();
    }

    @Test
    @DisplayName("내용 해시가 다르거나 추가·삭제된 파일만 변경으로 본다")
    void changedFileNames() {
        Map<Path, String> before = Map.of(Path.of("/c/a.yml"), "1", Path.of("/c/b.yml"), "2", Path.of("/c/c.yml"), "3");
        Map<Path, String> after = Map.of(Path.of("/c/a.yml"), "1", Path.of("/c/b.yml"), "X", Path.of("/c/d.yml"), "4");

        assertThat(ConfigFileWatcher.changedFileNames(before, after)).containsExactly("b.yml", "c.yml", "d.yml");
    }
}
