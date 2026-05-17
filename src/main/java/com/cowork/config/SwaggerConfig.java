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
                .description("레거시 호환용 Bearer 토큰입니다. 브라우저 클라이언트는 HttpOnly 쿠키를 사용합니다.");
        SecurityScheme cookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("cowork_access_token")
                .description("로그인/회원가입/SSO/refresh 응답의 Set-Cookie로 발급되는 HttpOnly Access 쿠키입니다.");

        return new OpenAPI()
                .info(new Info()
                        .title("Cowork API")
                        .description("""
                                ## Cowork 백엔드 API 문서

                                코워크 플랫폼의 REST API 명세입니다.

                                ### 인증 방식
                                - `POST /api/auth/login`, `POST /api/auth/register`, SSO 콜백/가입에서 Access/Refresh JWT를 HttpOnly 쿠키로 발급합니다.
                                - 인증이 필요한 브라우저 API 요청은 쿠키가 포함되도록 `credentials: include`로 호출합니다.
                                - Access 쿠키가 만료(401)되면 `POST /api/auth/refresh`로 쿠키를 재발급하세요.

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
                .addSecurityItem(new SecurityRequirement().addList("Auth Cookie"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Auth Cookie", cookieScheme)
                        .addSecuritySchemes("Bearer Authentication", bearerScheme));
    }
}
