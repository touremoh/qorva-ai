package ai.qorva.core.service.handlers;

import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static ai.qorva.core.service.handlers.StripeEventLogWriter.Entry;

/** The Stripe events Qorva records without acting on them, and what each one logs. */
@Configuration
public class LoggingStripeHandlers {

	@Bean
	LoggingStripeHandler<Invoice> invoiceCreatedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("invoice.created", Invoice.class, LoggingStripeHandlers::invoice, writer);
	}

	@Bean
	LoggingStripeHandler<Invoice> invoiceFinalizedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("invoice.finalized", Invoice.class, LoggingStripeHandlers::invoice, writer);
	}

	@Bean
	LoggingStripeHandler<Invoice> invoicePaidHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("invoice.paid", Invoice.class, LoggingStripeHandlers::invoice, writer);
	}

	@Bean
	LoggingStripeHandler<Customer> customerCreatedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("customer.created", Customer.class, LoggingStripeHandlers::customer, writer);
	}

	@Bean
	LoggingStripeHandler<Customer> customerUpdatedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("customer.updated", Customer.class, LoggingStripeHandlers::customer, writer);
	}

	@Bean
	LoggingStripeHandler<Customer> customerDeletedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("customer.deleted", Customer.class,
			c -> new Entry(c.getId(), c.getEmail(), null, "deleted"), writer);
	}

	@Bean
	LoggingStripeHandler<SetupIntent> setupIntentCreatedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("setup_intent.created", SetupIntent.class,
			i -> new Entry(i.getCustomer(), null, null, i.getStatus()), writer);
	}

	@Bean
	LoggingStripeHandler<SetupIntent> setupIntentSucceededHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("setup_intent.succeeded", SetupIntent.class,
			i -> new Entry(i.getCustomer(), null, null, i.getStatus()), writer);
	}

	@Bean
	LoggingStripeHandler<PaymentMethod> paymentMethodAttachedHandler(StripeEventLogWriter writer) {
		return new LoggingStripeHandler<>("payment_method.attached", PaymentMethod.class,
			pm -> new Entry(pm.getCustomer(), null, null, pm.getType()), writer);
	}

	private static Entry invoice(Invoice invoice) {
		var subscriptionId = invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null
			? invoice.getParent().getSubscriptionDetails().getSubscription()
			: null;
		return new Entry(invoice.getCustomer(), null, subscriptionId, invoice.getStatus());
	}

	private static Entry customer(Customer customer) {
		return new Entry(customer.getId(), customer.getEmail(), null, customer.getDeleted() != null ? "deleted" : "active");
	}
}
