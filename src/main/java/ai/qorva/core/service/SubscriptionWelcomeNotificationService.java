package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionWelcomeNotificationService extends AbstractEmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.of("UTC"));

    private static final Map<String, String[]> BILLING_CYCLE_LABELS = Map.of(
        "en", new String[]{"Monthly", "Annual"},
        "fr", new String[]{"Mensuel", "Annuel"},
        "de", new String[]{"Monatlich", "Jährlich"},
        "es", new String[]{"Mensual", "Anual"},
        "it", new String[]{"Mensile", "Annuale"},
        "nl", new String[]{"Maandelijks", "Jaarlijks"},
        "pt", new String[]{"Mensal", "Anual"}
    );

    @Value("${qorva.assets.logo-url}")
    private String logoUrl;

    @Autowired
    public SubscriptionWelcomeNotificationService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
        super(oauth2TokenService, senderResolver);
    }

    @Override
    protected EmailCategory getCategory() {
        return EmailCategory.BILLING;
    }

    @Override
    public void send(UserDTO user, String languageCode) throws QorvaException {
        throw new QorvaException("Not implemented");
    }

    public void send(UserDTO user, SubscriptionInfo sub, String languageCode) throws QorvaException {
        String lang = languageCode != null ? languageCode : "en";
        try {
            String wrapper = loadHtmlTemplate("templates/emails/subscription-welcome-template.html");
            String content = loadHtmlTemplate("templates/emails/" + lang + "_subscription_welcome_content.html");

            String billingCycle = resolveBillingCycleLabel(sub.getBillingCycle(), lang);
            String nextBillingDate = sub.getCurrentPeriodEnd() != null
                ? DATE_FMT.format(sub.getCurrentPeriodEnd()) : "-";

            log.info("Sending subscription welcome notification to user: {}", logoUrl);

            content = content
                .replace("{{logo_url}}", logoUrl)
                .replace("{{first_name}}", user.getFirstName())
                .replace("{{app_name}}", "Qorva AI")
                .replace("{{plan_name}}", sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan() : "")
                .replace("{{billing_cycle}}", billingCycle)
                .replace("{{next_billing_date}}", nextBillingDate)
                .replace("{{support_email}}", supportEmail())
                .replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));

            String html = wrapper.replace("{{template_content}}", content);

            sendEmail(user.getEmail(), EmailTitlesEnum.getEmailTitle(lang, "subscription"), html);
            log.info("Subscription welcome email sent to tenantId={} email={}", user.getTenantId(), user.getEmail());
        } catch (IOException | MailException e) {
            log.error("Failed to send subscription welcome email to {}", user.getEmail(), e);
            throw new QorvaException(
                "Failed to send subscription welcome notification",
                e,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String resolveBillingCycleLabel(String billingCycle, String lang) {
        String[] labels = BILLING_CYCLE_LABELS.getOrDefault(lang, BILLING_CYCLE_LABELS.get("en"));
        return "year".equalsIgnoreCase(billingCycle) ? labels[1] : labels[0];
    }
}
