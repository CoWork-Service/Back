package com.cowork.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("Bearer Authentication")
                .description("로그인 후 발급받은 Access Token을 입력하세요. (예: eyJhbGciOiJIUzI1NiJ9...)");

        return new OpenAPI()
                .info(new Info()
                        .title("Cowork API")
                        .description("""
                                ## Cowork 백엔드 API 문서

                                코워크 플랫폼의 REST API 명세입니다.

                                ### 인증 방식
                                - `POST /api/auth/login` 또는 `POST /api/auth/register`로 Access Token과 Refresh Token을 발급받습니다.
                                - 인증이 필요한 API는 요청 헤더에 `Authorization: Bearer {accessToken}`을 포함해야 합니다.
                                - Access Token이 만료(401)되면 `POST /api/auth/refresh`로 재발급하세요.

                                ### 공통 응답 형식
                                ```json
                                {
                                  "success": true,
                                  "data": { ... },
                                  "message": null,
                                  "code": null
                                }
                                ```

                                ### 에러 응답 형식
                                ```json
                                {
                                  "success": false,
                                  "data": null,
                                  "message": "에러 메시지",
                                  "code": "ERROR_CODE"
                                }
                                ```

                                ### 인증 불필요 엔드포인트
                                - `POST /api/auth/register`
                                - `POST /api/auth/login`
                                - `POST /api/auth/refresh`
                                - `POST /api/surveys/{id}/respond`
                                - `POST /api/timetables/{id}/respond`
                                - `GET/POST /api/mobile/sessions/**`
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Cowork Team")
                                .email("cowork@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                        new Server().url("https://api.cowork.example.com").description("운영 서버")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", bearerScheme));
    }
}
