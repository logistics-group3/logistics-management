package com.sparta.logistics.config.refresh;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * native 설정 폴더(config/)를 주기적으로 확인해 바뀐 파일이 있으면 {@link RefreshBroadcaster}로 해당 서비스를 refresh한다.
 * <ul>
 *   <li>파일 내용 해시로 비교 → 저장만 하고 내용이 같으면 무시</li>
 *   <li>변경이 감지된 뒤 한 주기 동안 추가 변경이 없을 때 refresh (에디터 저장·git pull로 여러 파일이 연달아 바뀌는 경우 한 번에)</li>
 *   <li>{@code file:} 위치만 감시. {@code classpath:}(IDE에서 jar 안 설정을 읽는 경우)는 파일이 바뀌지 않으므로 감시하지 않는다</li>
 * </ul>
 * WatchService 대신 폴링을 쓰는 이유: Docker Desktop 바인드 마운트에서는 호스트 파일 변경 이벤트가 컨테이너에 오지 않을 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigFileWatcher {

    private static final Set<String> EXTENSIONS = Set.of(".yml", ".yaml", ".properties");
    private static final String FILE_PREFIX = "file:";

    private final NativeEnvironmentProperties nativeProperties;
    private final ConfigWatchProperties properties;
    private final RefreshBroadcaster broadcaster;

    private final List<Path> directories = new ArrayList<>();
    private final Set<String> pending = new TreeSet<>();
    private Map<Path, String> snapshot;

    @PostConstruct
    void init() {
        for (String location : nativeProperties.getSearchLocations()) {
            if (location.startsWith(FILE_PREFIX)) {
                Path dir = Path.of(location.substring(FILE_PREFIX.length())).toAbsolutePath().normalize();
                if (Files.isDirectory(dir)) {
                    directories.add(dir);
                }
            }
        }
        if (directories.isEmpty()) {
            log.info("설정 자동 refresh 비활성: 감시할 file: 위치가 없습니다 (search-locations={}). "
                    + "CONFIG_LOCATION=file:/경로/config/ 로 실행하면 활성화됩니다.",
                    String.join(",", nativeProperties.getSearchLocations()));
        } else {
            log.info("설정 자동 refresh 활성: {} 감시", directories);
        }
    }

    @Scheduled(fixedDelayString = "${config.watch.interval-ms:2000}", initialDelayString = "${config.watch.interval-ms:2000}")
    public void poll() {
        if (!properties.isEnabled() || directories.isEmpty()) {
            return;
        }
        Map<Path, String> current = scan();
        if (snapshot == null) {
            snapshot = current;
            return;
        }
        Set<String> changed = changedFileNames(snapshot, current);
        snapshot = current;
        if (!changed.isEmpty()) {
            pending.addAll(changed);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        Set<String> files = new TreeSet<>(pending);
        pending.clear();
        broadcaster.refresh(files);
    }

    /** 추가·수정·삭제된 파일 이름 */
    static Set<String> changedFileNames(Map<Path, String> before, Map<Path, String> after) {
        Set<Path> paths = new HashSet<>(before.keySet());
        paths.addAll(after.keySet());
        Set<String> changed = new TreeSet<>();
        for (Path path : paths) {
            if (!Objects.equals(before.get(path), after.get(path))) {
                changed.add(path.getFileName().toString());
            }
        }
        return changed;
    }

    private Map<Path, String> scan() {
        Map<Path, String> hashes = new HashMap<>();
        for (Path dir : directories) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(ConfigFileWatcher::isConfigFile)
                        .forEach(file -> hashes.put(file, hash(file)));
            } catch (IOException | UncheckedIOException e) {
                log.warn("설정 폴더를 읽지 못했습니다 {}: {}", dir, e.getMessage());
            }
        }
        return hashes;
    }

    private static boolean isConfigFile(Path file) {
        String name = file.getFileName().toString();
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String hash(Path file) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
