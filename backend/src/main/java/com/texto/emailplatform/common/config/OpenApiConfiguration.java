package com.texto.emailplatform.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI emailPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Texto Email Platform API")
                        .description("Foundation API for the Texto ESP modular monolith")
                        .version("v1"));
    }
}
