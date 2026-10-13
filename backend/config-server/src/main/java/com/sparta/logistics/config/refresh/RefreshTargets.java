package com.sparta.logistics.config.refresh;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 바뀐 설정 파일 → refresh할 서비스 이름
 * <ul>
 *   <li>{@code application.yml}, {@code application-{profile}.yml} → 등록된 모든 서비스</li>
 *   <li>{@code {service}.yml}, {@code {service}-{profile}.yml} → 그 서비스</li>
 * </ul>
 */
final class RefreshTargets {

    private static final String COMMON = "application";

    private RefreshTargets() {
    }

    static Set<String> resolve(Collection<String> changedFiles, Collection<String> registeredServices, String self) {
        Set<String> services = new TreeSet<>();
        for (String service : registeredServices) {
            String name = service.toLowerCase(Locale.ROOT);
            if (!name.equalsIgnoreCase(self)) {
                services.add(name);
            }
        }
        Set<String> targets = new TreeSet<>();
        for (String file : changedFiles) {
            String base = stripExtension(file).toLowerCase(Locale.ROOT);
            if (base.equals(COMMON) || base.startsWith(COMMON + "-")) {
                return services;
            }
            for (String service : services) {
                if (base.equals(service) || base.startsWith(service + "-")) {
                    targets.add(service);
                }
            }
        }
        return targets;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
