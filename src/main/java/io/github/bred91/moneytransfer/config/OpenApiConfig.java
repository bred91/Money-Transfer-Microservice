package io.github.bred91.moneytransfer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI moneyTransferOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Money Transfer Service API")
                .version("v1")
                .description("""
                        REST API for account management.

                        Money transfers are not exposed over REST: they are consumed asynchronously \
                        from the `money-transfers` Kafka topic.

                        The API is versioned through the path segment after `/api` (e.g. `/api/v1/...`). \
                        Errors are returned as RFC 9457 Problem Details (`application/problem+json`).""")
                .contact(new Contact()
                        .name("Raffaele Pane")
                        .url("https://github.com/bred91")));
    }
}
