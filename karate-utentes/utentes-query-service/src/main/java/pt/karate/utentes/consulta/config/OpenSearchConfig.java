package pt.karate.utentes.consulta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class OpenSearchConfig {

    @Bean(destroyMethod = "close")
    OpenSearchTransport openSearchTransport(@Value("${opensearch.url:http://localhost:9200}") String url,
                                            ObjectMapper objectMapper) throws URISyntaxException {
        return ApacheHttpClient5TransportBuilder
                .builder(HttpHost.create(url))
                // Usa o ObjectMapper do Spring (datas ISO, suporte a records e java.time).
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .build();
    }

    @Bean
    OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    @Bean
    Clock clock(@Value("${utentes.fuso-horario:Europe/Lisbon}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }
}
