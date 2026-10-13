package com.sparta.logistics.common.config;

import com.sparta.logistics.common.auth.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 각 서비스의 OpenAPI 문서 공통 설정. 문서는 Gateway Swagger UI(http://localhost:8080/swagger-ui.html)에서 서비스별로 본다.
 * Note: 서버 주소를 상대 경로 "/"로 두면 Swagger UI의 Try it out 요청이 문서를 연 주소(Gateway)로 간다. 서비스 내부 IP가
 * 문서에 찍혀 외부에서 호출할 수 없는 문제를 피한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // @CurrentUser 파라미터는 Gateway 헤더에서 채워지므로 문서의 요청 파라미터로 보이지 않게 한다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI openAPI(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info().title(applicationName + " API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
