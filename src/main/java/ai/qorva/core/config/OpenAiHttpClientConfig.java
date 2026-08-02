package ai.qorva.core.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import org.apache.hc.core5.util.Timeout;

@Configuration
public class OpenAiHttpClientConfig {

	@Bean(destroyMethod = "close")
	public PoolingHttpClientConnectionManager qorvaHttpConnectionManager() {
		var connectionConfig = ConnectionConfig.custom()
			.setConnectTimeout(Timeout.ofSeconds(10))
			.setSocketTimeout(Timeout.ofSeconds(60))
			.setTimeToLive(TimeValue.ofMinutes(2))
			.setValidateAfterInactivity(TimeValue.ofSeconds(10))
			.build();

		return PoolingHttpClientConnectionManagerBuilder.create()
			.setMaxConnTotal(100)
			.setMaxConnPerRoute(50)
			.setDefaultConnectionConfig(connectionConfig)
			.build();
	}

	@Bean(destroyMethod = "close")
	public CloseableHttpClient qorvaHttpClient(PoolingHttpClientConnectionManager connectionManager) {
		var requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(Timeout.ofSeconds(30))
			.setResponseTimeout(Timeout.ofSeconds(60))
			.build();

		return HttpClientBuilder.create()
			.setConnectionManager(connectionManager)
			.setDefaultRequestConfig(requestConfig)
			.evictIdleConnections(TimeValue.ofSeconds(45))
			.evictExpiredConnections()
			.build();
	}

	@Bean
	public RestClientCustomizer qorvaRestClientCustomizer(CloseableHttpClient httpClient) {
		var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
		return builder -> builder.requestFactory(requestFactory);
	}
}
