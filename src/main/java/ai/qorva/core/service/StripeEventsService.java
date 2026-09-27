package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.handlers.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class StripeEventsService {

	protected final StripeProperties stripeProperties;
	private final StripeEventDispatcher dispatcher;
	protected final UserRepository userRepository;
	protected final TenantService tenantService;

	@Value("${stripe.session.return-url}")
	private String stripeSessionReturnUrl;

	@PostConstruct
	public void init() {
		Stripe.apiKey = stripeProperties.getSecretKey();
	}

	@Autowired
	protected StripeEventsService(
		StripeProperties stripeProperties,
		StripeEventDispatcher dispatcher,
		UserRepository userRepository,
		TenantService tenantService
	) {
		this.stripeProperties = stripeProperties;
		this.dispatcher = dispatcher;
		this.userRepository = userRepository;
		this.tenantService = tenantService;
	}

	/** A verified webhook event: handled once per event id by the handler registered for its type. */
	public String handleEvent(Event event) throws QorvaException {
		return dispatcher.dispatch(event);
	}

	public PortalSession buildStripePortalSessionUrl(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		var user = Optional.ofNullable(userRepository.findByEmail(userDetails.getUsername()))
			.orElseThrow(() -> new QorvaException("User not found"));
		var tenant = tenantService.findOneById(user.getTenantId());

		SessionCreateParams params = SessionCreateParams.builder()
			.setCustomer(tenant.getStripeCustomerId())
			.setReturnUrl(stripeSessionReturnUrl)
			.build();

		try {
			return new PortalSession(Session.create(params).getUrl());
		} catch (StripeException e) {
			log.error("Failed to create Stripe portal session", e);
			throw new QorvaException(QorvaErrorCodes.BILLING_PORTAL_FAILED, e);
		}
	}
}
