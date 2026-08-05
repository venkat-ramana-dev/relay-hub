package dev.venkat.relayhub.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().replaceWithClass(JsonNode.class, Object.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Relay Hub API")
                        .version("1.0")
                        .description("API documentation for the Relay Hub Webhook Dispatcher")
                        .contact(new Contact().name("Venkat Ramana").email("venkatramanareddy1734@gmail.com")));
    }
}
