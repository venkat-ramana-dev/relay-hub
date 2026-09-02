package dev.venkat.relayhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.HttpURLConnection;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient webhookRestClient() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory() {

                    @Override
                    protected void prepareConnection(
                            HttpURLConnection connection,
                            String httpMethod) throws java.io.IOException {

                        super.prepareConnection(connection, httpMethod);

                        // Do not automatically follow redirects.
                        connection.setInstanceFollowRedirects(false);
                    }
                };

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
