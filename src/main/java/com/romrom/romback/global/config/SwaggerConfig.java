package com.romrom.romback.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "📱 롬롬 : ROM-ROM 📱",
        description = """
            ### 🌐 롬롬 웹사이트 🌐 : romrom.co.kr
            [**웹사이트 바로가기**](http://suh-project.synology.me:8085)

            ### 💻 **GitHub 저장소**
            - **[백엔드 소스코드](https://github.com/TEAM-ROMROM/RomRom-BE)**
              백엔드 개발에 관심이 있다면 저장소를 방문해보세요.
            """,
        version = "1.0v"
    ),
    servers = {
        @Server(url = "https://api.romrom.xyz", description = "메인 서버"),
        @Server(url = "https://api.test.romrom.xyz", description = "테스트 서버"),
        @Server(url = "http://suh-project.synology.me:8085", description = "HTTP 메인 서버"),
        @Server(url = "http://suh-project.synology.me:8086", description = "HTTP 테스트 서버"),
        @Server(url = "http://localhost:8080", description = "로컬 서버")
    }
)
@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    SecurityScheme apiKey = new SecurityScheme()
        .type(Type.HTTP)
        .in(In.HEADER)
        .name("Authorization")
        .scheme("bearer")
        .bearerFormat("JWT");

    SecurityRequirement securityRequirement = new SecurityRequirement()
        .addList("Bearer Token");

    return new OpenAPI()
        .components(new Components().addSecuritySchemes("Bearer Token", apiKey))
        .addSecurityItem(securityRequirement)
        .servers(List.of(
                new io.swagger.v3.oas.models.servers.Server()
                    .url("http://localhost:8080")
                    .description("로컬 서버"),
                new io.swagger.v3.oas.models.servers.Server()
                    .url("https://api.test.romrom.xyz")
                    .description("테스트 서버"),
                new io.swagger.v3.oas.models.servers.Server()
                    .url("https://api.romrom.xyz")
                    .description("메인 서버")
//                new io.swagger.v3.oas.models.servers.Server()
//                    .url("http://suh-project.synology.me:8086")
//                    .description("테스트 서버"),
//                new io.swagger.v3.oas.models.servers.Server()
//                    .url("http://suh-project.synology.me:8085")
//                    .description("메인 서버")
            )
        );
  }
}
