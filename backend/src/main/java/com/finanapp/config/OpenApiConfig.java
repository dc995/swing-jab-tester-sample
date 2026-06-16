package com.finanapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finanAppOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinanApp Trading API")
                        .description("Institutional stock trading platform REST API. Supports portfolio management, order execution, and real-time market data.")
                        .version("1.0.0")
                        .contact(new Contact().name("FinanApp Engineering")))
                .servers(List.of(
                        new Server().url("http://localhost:9294").description("Local Development")
                ));
    }
}
