package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionWelcomeNotificationServiceTest {

    @Mock
    private OAuth2TokenService oauth2TokenService;

    private SubscriptionWelcomeNotificationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = spy(new SubscriptionWelcomeNotificationService(oauth2TokenService));
        ReflectionTestUtils.setField(service, "logoUrl", "https://example.com/logo.svg");
        ReflectionTestUtils.setField(service, "senderEmail", "sender@qorva.ai");
        ReflectionTestUtils.setField(service, "fromEmail", "support@qorva.ai");
        doNothing().when(service).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void send_englishMonthly_callsSendEmailWithCorrectSubject() throws Exception {
        service.send(user("en"), sub("month", Instant.parse("2026-07-28T00:00:00Z")), "en");

        verify(service).sendEmail(
            eq("alice@example.com"),
            eq("Your Qorva AI subscription is active"),
            anyString()
        );
    }

    @ParameterizedTest(name = "{0} monthly subject = {1}")
    @CsvSource({
        "en, Your Qorva AI subscription is active",
        "fr, Votre abonnement Qorva AI est actif",
        "de, Ihr Qorva AI-Abonnement ist aktiv",
        "es, Tu suscripción a Qorva AI está activa",
        "it, Il tuo abbonamento Qorva AI è attivo",
        "nl, Uw Qorva AI-abonnement is actief",
        "pt, A sua subscrição Qorva AI está ativa"
    })
    void send_localizedSubject_matchesLanguage(String lang, String expectedSubject) throws Exception {
        service.send(user(lang), sub("month", Instant.parse("2026-07-28T00:00:00Z")), lang);

        verify(service).sendEmail(anyString(), eq(expectedSubject), anyString());
    }

    @ParameterizedTest(name = "{0} annual billing label = {1}")
    @CsvSource({
        "en, Annual",
        "fr, Annuel",
        "de, Jährlich",
        "es, Anual",
        "it, Annuale",
        "nl, Jaarlijks",
        "pt, Anual"
    })
    void send_annualCycle_usesCorrectLocalizedLabel(String lang, String expectedLabel) throws Exception {
        var htmlCaptor = ArgumentCaptor.forClass(String.class);

        service.send(user(lang), sub("year", Instant.parse("2027-06-28T00:00:00Z")), lang);

        verify(service).sendEmail(anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue()).contains(expectedLabel);
    }

    @ParameterizedTest(name = "{0} monthly billing label = {1}")
    @CsvSource({
        "en, Monthly",
        "fr, Mensuel",
        "de, Monatlich",
        "es, Mensual",
        "it, Mensile",
        "nl, Maandelijks",
        "pt, Mensal"
    })
    void send_monthlyCycle_usesCorrectLocalizedLabel(String lang, String expectedLabel) throws Exception {
        var htmlCaptor = ArgumentCaptor.forClass(String.class);

        service.send(user(lang), sub("month", Instant.parse("2026-07-28T00:00:00Z")), lang);

        verify(service).sendEmail(anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue()).contains(expectedLabel);
    }

    @Test
    void send_nullPeriodEnd_rendersDashForDate() throws Exception {
        var htmlCaptor = ArgumentCaptor.forClass(String.class);

        service.send(user("en"), sub("month", null), "en");

        verify(service).sendEmail(anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue())
            .doesNotContain("{{next_billing_date}}")
            .contains("-");
    }

    @Test
    void send_nullLanguage_defaultsToEnglish() throws Exception {
        service.send(user("en"), sub("month", Instant.parse("2026-07-28T00:00:00Z")), null);

        verify(service).sendEmail(anyString(), eq("Your Qorva AI subscription is active"), anyString());
    }

    @Test
    void send_logoUrlPlaceholderIsReplaced() throws Exception {
        var htmlCaptor = ArgumentCaptor.forClass(String.class);

        service.send(user("en"), sub("month", Instant.parse("2026-07-28T00:00:00Z")), "en");

        verify(service).sendEmail(anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue())
            .contains("https://example.com/logo.svg")
            .doesNotContain("{{logo_url}}");
    }

    @Test
    void send_recipientEmailMatchesUser() throws Exception {
        service.send(user("en"), sub("month", Instant.parse("2026-07-28T00:00:00Z")), "en");

        verify(service).sendEmail(eq("alice@example.com"), anyString(), anyString());
    }

    @Test
    void sendUserLang_throwsNotImplemented() {
        assertThatThrownBy(() -> service.send(user("en"), "en"))
            .isInstanceOf(QorvaException.class);
    }

    // --- builders ---

    private UserDTO user(String lang) {
        var u = new UserDTO();
        u.setEmail("alice@example.com");
        u.setFirstName("Alice");
        u.setTenantId("tenant-001");
        u.setCommunicationLanguage(lang);
        return u;
    }

    private SubscriptionInfo sub(String billingCycle, Instant periodEnd) {
        var s = new SubscriptionInfo();
        s.setSubscriptionPlan("Pro");
        s.setBillingCycle(billingCycle);
        s.setCurrentPeriodEnd(periodEnd);
        return s;
    }
}
