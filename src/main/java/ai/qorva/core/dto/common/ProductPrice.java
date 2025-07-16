package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.Decimal128;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductPrice {

    private String monthlyPriceId;
    private String annualPriceId;
    private Decimal128 monthlyPrice;
    private Decimal128 annuallyPrice;
}
