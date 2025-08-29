package ai.qorva.core.dto.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventCheckoutSessionCompleted {
	private String id;
	private String object;

	@JsonProperty("adaptive_pricing")
	@JsonIgnore
	private Object adaptivePricing;

	@JsonProperty("after_expiration")
	private Object afterExpiration;

	@JsonProperty("allow_promotion_codes")
	private boolean allowPromotionCodes;

	@JsonProperty("amount_subtotal")
	private Long amountSubtotal;

	@JsonProperty("amount_total")
	private Long amountTotal;

	@JsonProperty("automatic_tax")
	private AutomaticTax automaticTax;

	@JsonProperty("billing_address_collection")
	private String billingAddressCollection;

	@JsonProperty("cancel_url")
	private String cancelUrl;

	@JsonProperty("client_reference_id")
	private String clientReferenceId;

	@JsonProperty("client_secret")
	private Object clientSecret;

	@JsonProperty("collected_information")
	private CollectedInformation collectedInformation;

	private Object consent;

	@JsonProperty("consent_collection")
	private ConsentCollection consentCollection;

	private Long created;
	private String currency;

	@JsonProperty("currency_conversion")
	private Object currencyConversion;

	@JsonProperty("custom_fields")
	private List<Object> customFields;

	@JsonProperty("custom_text")
	private CustomText customText;

	private String customer;

	@JsonProperty("customer_creation")
	private String customerCreation;

	@JsonProperty("customer_details")
	private CustomerDetails customerDetails;

	@JsonProperty("customer_email")
	private String customerEmail;

	private List<Object> discounts;

	@JsonProperty("expires_at")
	private Long expiresAt;

	@JsonProperty("invalid_payment_methods_hash")
	private Map<String, PaymentMethodError> invalidPaymentMethodsHash;

	private String invoice;

	@JsonProperty("invoice_creation")
	private Object invoiceCreation;

	private boolean livemode;
	private String locale;
	private Map<String, Object> metadata;
	private String mode;

	@JsonProperty("origin_context")
	private Object originContext;

	@JsonProperty("payment_intent")
	private Object paymentIntent;

	@JsonProperty("payment_link")
	private Object paymentLink;

	@JsonProperty("payment_method_collection")
	private String paymentMethodCollection;

	@JsonProperty("payment_method_configuration_details")
	private PaymentMethodConfigurationDetails paymentMethodConfigurationDetails;

	@JsonProperty("payment_method_options")
	private PaymentMethodOptions paymentMethodOptions;

	@JsonProperty("payment_method_types")
	private List<String> paymentMethodTypes;

	@JsonProperty("payment_status")
	private String paymentStatus;

	private Object permissions;

	@JsonProperty("phone_number_collection")
	private PhoneNumberCollection phoneNumberCollection;

	@JsonProperty("recovered_from")
	private Object recoveredFrom;

	@JsonProperty("saved_payment_method_options")
	private SavedPaymentMethodOptions savedPaymentMethodOptions;

	@JsonProperty("setup_intent")
	private Object setupIntent;

	@JsonProperty("shipping_address_collection")
	private Object shippingAddressCollection;

	@JsonProperty("shipping_cost")
	private Object shippingCost;

	@JsonProperty("shipping_details")
	private Object shippingDetails;

	@JsonProperty("shipping_options")
	private List<Object> shippingOptions;

	private String status;

	@JsonProperty("submit_type")
	private Object submitType;

	private String subscription;

	@JsonProperty("success_url")
	private String successUrl;

	@JsonProperty("total_details")
	private TotalDetails totalDetails;

	@JsonProperty("ui_mode")
	private String uiMode;

	private Object url;

	@JsonProperty("wallet_options")
	private Object walletOptions;

	// ==== NESTED CLASSES ====

	@Data
	public static class AutomaticTax {
		private boolean enabled;
		private Object liability;
		private Object provider;
		private Object status;
	}

	@Data
	public static class CollectedInformation {
		@JsonProperty("shipping_details")
		private Object shippingDetails;
	}

	@Data
	public static class ConsentCollection {
		@JsonProperty("payment_method_reuse_agreement")
		private Object paymentMethodReuseAgreement;
		private String promotions;
		@JsonProperty("terms_of_service")
		private String termsOfService;
	}

	@Data
	public static class CustomText {
		@JsonProperty("after_submit")
		private Object afterSubmit;
		@JsonProperty("shipping_address")
		private Object shippingAddress;
		private Object submit;
		@JsonProperty("terms_of_service_acceptance")
		private Object termsOfServiceAcceptance;
	}

	@Data
	public static class CustomerDetails {
		private Address address;
		private String email;
		private String name;
		private Object phone;
		@JsonProperty("tax_exempt")
		private String taxExempt;
		@JsonProperty("tax_ids")
		private List<Object> taxIds;
	}

	@Data
	public static class Address {
		private Object city;
		private String country;
		private Object line1;
		private Object line2;
		@JsonProperty("postal_code")
		private Object postalCode;
		private Object state;
	}

	@Data
	public static class PaymentMethodError {
		private String message;
	}

	@Data
	public static class PaymentMethodConfigurationDetails {
		private String id;
		private Object parent;
	}

	@Data
	public static class PaymentMethodOptions {
		private Card card;
	}

	@Data
	public static class Card {
		@JsonProperty("request_three_d_secure")
		private String requestThreeDSecure;
	}

	@Data
	public static class PhoneNumberCollection {
		private boolean enabled;
	}

	@Data
	public static class SavedPaymentMethodOptions {
		@JsonProperty("allow_redisplay_filters")
		private List<String> allowRedisplayFilters;
		@JsonProperty("payment_method_remove")
		private String paymentMethodRemove;
		@JsonProperty("payment_method_save")
		private Object paymentMethodSave;
	}

	@Data
	public static class TotalDetails {
		@JsonProperty("amount_discount")
		private Long amountDiscount;
		@JsonProperty("amount_shipping")
		private Long amountShipping;
		@JsonProperty("amount_tax")
		private Long amountTax;
	}
}
