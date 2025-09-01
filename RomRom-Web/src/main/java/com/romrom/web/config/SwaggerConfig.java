package com.romrom.web.config;

import com.romrom.web.properties.SpringDocProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    )
)
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SpringDocProperties.class)
public class SwaggerConfig {

  private final SpringDocProperties properties;

  @Bean
  public OpenAPI openAPI() {
    SecurityScheme apiKey = new SecurityScheme()
        .type(Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .in(In.HEADER)
        .name("Authorization");

    return new OpenAPI()
        .components(new Components().addSecuritySchemes("Bearer Token", apiKey))
        .addSecurityItem(new SecurityRequirement().addList("Bearer Token"));
  }

  @Bean
  public OpenApiCustomizer serverCustomizer() {
    return openApi -> {
      properties.getServers().forEach(server ->
          openApi.addServersItem(new io.swagger.v3.oas.models.servers.Server()
              .url(server.getUrl())
              .description(server.getDescription()))
      );
    };
  }
}
