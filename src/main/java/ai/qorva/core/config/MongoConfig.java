package ai.qorva.core.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.ai.openai.api.OpenAiApi.EmbeddingModel.TEXT_EMBEDDING_3_LARGE;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class MongoConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;


    @Bean
    public EmbeddingModel embeddingModel() {
        var apiModel = OpenAiApi
            .builder()
            .apiKey(apiKey)
            .embeddingsPath("/v1/embeddings")
            .baseUrl("https://api.openai.com")
            .build();
        var options = OpenAiEmbeddingOptions.builder().model(TEXT_EMBEDDING_3_LARGE.getValue()).build();
        return new OpenAiEmbeddingModel(apiModel, MetadataMode.EMBED, options);
    }

    @Bean(name = "auditingDateTimeProvider")
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (Objects.isNull(auth) || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of("qorva");
            }
            return Optional.ofNullable(auth.getName());
        };
    }

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}