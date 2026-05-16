package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripePrice {

    private String stripePriceId;
    private String currency;
    private String interval;       // month, year, week, day; null for one-time
    private Integer intervalCount;
    private Long unitAmount;       // in cents
    private String nickname;
    private boolean active;
}
