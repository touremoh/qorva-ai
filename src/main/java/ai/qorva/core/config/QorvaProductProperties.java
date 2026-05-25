package ai.qorva.core.config;

import ai.qorva.core.dto.common.ProductFeatures;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qorva.products")
@NoArgsConstructor
@AllArgsConstructor
public class QorvaProductProperties {

    private ProductPlanConfig starter = new ProductPlanConfig();
    private ProductPlanConfig pro = new ProductPlanConfig();
    private ProductPlanConfig scale = new ProductPlanConfig();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPlanConfig {
        private String stripeProductName;
        private ProductFeatures features;
    }
}
