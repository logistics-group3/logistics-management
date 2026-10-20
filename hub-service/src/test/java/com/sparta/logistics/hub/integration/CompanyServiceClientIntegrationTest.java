package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.CompanyReader;
import com.sparta.logistics.hub.application.ProductReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * company-service I-07·I-08 응답 JSON을 hub의 DTO가 실제로 읽는지 확인한다. 서비스 단위 테스트는 포트를 mock으로 대체하므로
 * company 응답의 필드 이름이 어긋나도 잡지 못한다(어긋나면 값이 null이 되어 업체 관리자 입고가 막힌다).
 * Note: 응답 형식은 company-service의 InternalProductResponse·InternalCompanyResponse(감싸지 않은 DTO, ADR 0010)를 따른다.
 */
class CompanyServiceClientIntegrationTest extends PostgresIntegrationTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID MISSING_ID = UUID.randomUUID();
    private static final UUID BROKEN_COMPANY_ID = UUID.randomUUID();

    private static final HttpServer COMPANY_SERVER = startCompanyServer();

    @Autowired
    private ProductReader productReader;
    @Autowired
    private CompanyReader companyReader;

    @DynamicPropertySource
    static void companyUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.company-service.url",
                () -> "http://localhost:" + COMPANY_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        COMPANY_SERVER.stop(0);
    }

    @Test
    @DisplayName("I-08 응답에서 상품이 속한 업체 ID를 읽는다")
    void companyIdOfProduct() {
        assertThat(productReader.getCompanyId(PRODUCT_ID)).isEqualTo(COMPANY_ID);
    }

    @Test
    @DisplayName("I-07 응답에서 업체의 소속 허브와 관리자를 읽는다")
    void companyInfo() {
        assertThat(companyReader.getCompany(COMPANY_ID)).isEqualTo(new CompanyReader.CompanyInfo(HUB_ID, MANAGER_ID));
    }

    @Test
    @DisplayName("company의 404 응답은 코드 그대로 BusinessException으로 올라온다")
    void notFoundKeepsCode() {
        assertThatThrownBy(() -> productReader.getCompanyId(MISSING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().code()).isEqualTo("PRODUCT_NOT_FOUND"));
        assertThatThrownBy(() -> companyReader.getCompany(MISSING_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().code()).isEqualTo("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("company가 5xx를 반환하면 503 SERVICE_UNAVAILABLE이다")
    void serverErrorIsUnavailable() {
        assertThatThrownBy(() -> companyReader.getCompany(BROKEN_COMPANY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    private static HttpServer startCompanyServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/products/", exchange -> {
                if (lastSegment(exchange).equals(PRODUCT_ID.toString())) {
                    respond(exchange, 200, "{\"productId\":\"" + PRODUCT_ID + "\",\"companyId\":\"" + COMPANY_ID
                            + "\",\"name\":\"상품\",\"price\":1000}");
                } else {
                    respond(exchange, 404, "{\"status\":404,\"code\":\"PRODUCT_NOT_FOUND\",\"message\":\"상품을 찾을 수 없습니다.\"}");
                }
            });
            server.createContext("/internal/companies/", exchange -> {
                String id = lastSegment(exchange);
                if (id.equals(COMPANY_ID.toString())) {
                    respond(exchange, 200, "{\"companyId\":\"" + COMPANY_ID + "\",\"name\":\"업체\",\"hubId\":\"" + HUB_ID
                            + "\",\"address\":\"주소\",\"managerId\":\"" + MANAGER_ID + "\"}");
                } else if (id.equals(BROKEN_COMPANY_ID.toString())) {
                    respond(exchange, 500, "{}");
                } else {
                    respond(exchange, 404, "{\"status\":404,\"code\":\"COMPANY_NOT_FOUND\",\"message\":\"업체를 찾을 수 없습니다.\"}");
                }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lastSegment(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
