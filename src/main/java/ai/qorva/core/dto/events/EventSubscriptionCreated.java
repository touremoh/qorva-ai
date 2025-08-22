package ai.qorva.core.dto.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventSubscriptionCreated {

	private String id;
	private String object;
	private String application;

	@JsonProperty("application_fee_percent")
	private Double applicationFeePercent;

	@JsonProperty("automatic_tax")
	private AutomaticTax automaticTax;

	@JsonProperty("billing_cycle_anchor")
	private Long billingCycleAnchor;

	@JsonProperty("billing_cycle_anchor_config")
	private Object billingCycleAnchorConfig;

	@JsonProperty("billing_mode")
	private BillingMode billingMode;

	@JsonProperty("billing_thresholds")
	private Object billingThresholds;

	@JsonProperty("cancel_at")
	private Long cancelAt;

	@JsonProperty("cancel_at_period_end")
	private boolean cancelAtPeriodEnd;

	@JsonProperty("canceled_at")
	private Long canceledAt;

	@JsonProperty("cancellation_details")
	private CancellationDetails cancellationDetails;

	@JsonProperty("collection_method")
	private String collectionMethod;

	private Long created;
	private String currency;

	@JsonProperty("current_period_end")
	private Long currentPeriodEnd;

	@JsonProperty("current_period_start")
	private Long currentPeriodStart;

	private String customer;

	@JsonProperty("days_until_due")
	private Integer daysUntilDue;

	@JsonProperty("default_payment_method")
	private String defaultPaymentMethod;

	@JsonProperty("default_source")
	private String defaultSource;

	@JsonProperty("default_tax_rates")
	private List<Object> defaultTaxRates;

	private String description;
	private Object discount;
	private List<Object> discounts;

	@JsonProperty("ended_at")
	private Long endedAt;

	@JsonProperty("invoice_settings")
	private InvoiceSettings invoiceSettings;

	private Items items;

	@JsonProperty("latest_invoice")
	private String latestInvoice;

	private boolean livemode;
	private Map<String, Object> metadata;

	@JsonProperty("next_pending_invoice_item_invoice")
	private Object nextPendingInvoiceItemInvoice;

	@JsonProperty("on_behalf_of")
	private Object onBehalfOf;

	@JsonProperty("pause_collection")
	private Object pauseCollection;

	@JsonProperty("pending_invoice_item_interval")
	private Object pendingInvoiceItemInterval;

	@JsonProperty("pending_setup_intent")
	private Object pendingSetupIntent;

	@JsonProperty("pending_update")
	private Object pendingUpdate;

	private Plan plan;
	private Integer quantity;
	private Object schedule;

	@JsonProperty("start_date")
	private Long startDate;

	private String status;

	@JsonProperty("test_clock")
	private Object testClock;

	@JsonProperty("transfer_data")
	private Object transferData;

	@JsonProperty("trial_end")
	private Long trialEnd;

	@JsonProperty("trial_start")
	private Long trialStart;

	// ====== NESTED CLASSES ======

	@Data
	public static class AutomaticTax {
		@JsonProperty("disabled_reason")
		private String disabledReason;
		private boolean enabled;
		private Object liability;
	}

	@Data
	public static class BillingMode {
		private String type;
	}

	@Data
	public static class CancellationDetails {
		private String comment;
		private String feedback;
		private String reason;
	}

	@Data
	public static class InvoiceSettings {
		@JsonProperty("account_tax_ids")
		private Object accountTaxIds;
		private Issuer issuer;
	}

	@Data
	public static class Issuer {
		private String type;
	}

	@Data
	public static class Items {
		private String object;
		private List<ItemData> data;

		@JsonProperty("has_more")
		private boolean hasMore;

		@JsonProperty("total_count")
		private int totalCount;

		private String url;
	}

	@Data
	public static class ItemData {
		private String id;
		private String object;

		@JsonProperty("billing_thresholds")
		private Object billingThresholds;

		private Long created;

		@JsonProperty("current_period_end")
		private Long currentPeriodEnd;

		@JsonProperty("current_period_start")
		private Long currentPeriodStart;

		private List<Object> discounts;
		private Map<String, Object> metadata;
		private Plan plan;
		private Price price;
		private Integer quantity;
		private String subscription;

		@JsonProperty("tax_rates")
		private List<Object> taxRates;
	}

	@Data
	public static class Plan {
		private String id;
		private String object;
		private boolean active;

		@JsonProperty("aggregate_usage")
		private Object aggregateUsage;

		private Long amount;

		@JsonProperty("amount_decimal")
		private String amountDecimal;

		@JsonProperty("billing_scheme")
		private String billingScheme;

		private Long created;
		private String currency;
		private String interval;

		@JsonProperty("interval_count")
		private Integer intervalCount;

		private boolean livemode;
		private Map<String, Object> metadata;
		private Object meter;
		private String nickname;
		private String product;

		@JsonProperty("tiers_mode")
		private Object tiersMode;

		@JsonProperty("transform_usage")
		private Object transformUsage;

		@JsonProperty("trial_period_days")
		private Object trialPeriodDays;

		@JsonProperty("usage_type")
		private String usageType;
	}

	@Data
	public static class Price {
		private String id;
		private String object;
		private boolean active;

		@JsonProperty("billing_scheme")
		private String billingScheme;

		private Long created;
		private String currency;

		@JsonProperty("custom_unit_amount")
		private Object customUnitAmount;

		private boolean livemode;

		@JsonProperty("lookup_key")
		private Object lookupKey;

		private Map<String, Object> metadata;
		private String nickname;
		private String product;
		private Recurring recurring;

		@JsonProperty("tax_behavior")
		private String taxBehavior;

		@JsonProperty("tiers_mode")
		private Object tiersMode;

		@JsonProperty("transform_quantity")
		private Object transformQuantity;

		private String type;

		@JsonProperty("unit_amount")
		private Long unitAmount;

		@JsonProperty("unit_amount_decimal")
		private String unitAmountDecimal;
	}

	@Data
	public static class Recurring {
		@JsonProperty("aggregate_usage")
		private Object aggregateUsage;

		private String interval;

		@JsonProperty("interval_count")
		private Integer intervalCount;

		private Object meter;

		@JsonProperty("trial_period_days")
		private Object trialPeriodDays;

		@JsonProperty("usage_type")
		private String usageType;
	}

	@Data
	public static class PaymentMethodOptions {
		@JsonProperty("acss_debit")
		private Object acssDebit;

		@JsonProperty("customer_balance")
		private Object customerBalance;

		private Object konbini;

		@JsonProperty("sepa_debit")
		private Object sepaDebit;

		@JsonProperty("us_bank_account")
		private Object usBankAccount;
	}
}
