package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.enums.ChatUserRole;
import lombok.experimental.UtilityClass;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the resume-chat prompt as real role-typed messages:
 * <pre>
 *   system : rules
 *   system : CONTEXT (CV / job / report JSON)          ← identical every turn → cacheable prefix
 *   system : CONVERSATION SUMMARY SO FAR (optional)    ← changes only after a compaction
 *   user / assistant … the recent-turns window
 * </pre>
 * The stored SYSTEM seed row is deliberately not replayed; the rules message replaces it.
 */
@UtilityClass
public class ResumeChatPromptBuilder {

    private static final String RULES = """
        You are Qorva AI, an assistant helping a recruiter evaluate one candidate for one job.
        Answer strictly from the CONTEXT (candidate CV, job description with its scoring rules, resume match analysis \
        when present), the CONVERSATION SUMMARY and the recent messages. If the information is not there, say so — never invent facts.
        Be concise and concrete; quote the CV or job description when it supports the answer.

        Fit scores: the only fit score you may state is the official screening score given in the RESUME MATCH ANALYSIS, \
        and you must attribute it (for example "the screening report scores this match at 64%%"). Explain it with the \
        analysis's strengths, weaknesses, missing skills and red flags, and with the job's scoring rules (weights, mandatory skills, \
        location and industry strictness). If there is no analysis, assess the fit qualitatively — strong, partial or weak, \
        with evidence from the CV against the scoring rules — and say that no scored screening report has been generated yet. \
        Never estimate, compute or invent a percentage or score of your own, even if asked for one.

        Formatting: Markdown is rendered. Default to a short answer — a few sentences or up to about 8 bullets. Only produce \
        a long structured document (headings, tables, scorecards) when the recruiter explicitly asks for a plan, checklist, \
        report or comparison. Use ### for section headings, bold for the key fact of a bullet, blockquotes for verbatim \
        questions to ask the candidate, and tables only for comparisons with at most 4 columns. No horizontal rules, \
        no headings deeper than ###, no emojis.
        %s""";

    private static final String LANGUAGE_RULE = "Answer in the language of the recruiter's latest message.";
    private static final String LANGUAGE_RULE_FIXED = "Answer in this language: %s.";

    public List<Message> build(ScreeningContext ctx, String summary, List<ChatMessage> window, String language) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(RULES.formatted(
            StringUtils.hasText(language) ? LANGUAGE_RULE_FIXED.formatted(language) : LANGUAGE_RULE)));
        messages.add(new SystemMessage(contextBlock(ctx)));
        if (StringUtils.hasText(summary)) {
            messages.add(new SystemMessage("CONVERSATION SUMMARY SO FAR:\n" + summary));
        }
        for (ChatMessage m : window) {
            if (m.getRole() == ChatUserRole.USER) {
                messages.add(new UserMessage(m.getContent()));
            } else if (m.getRole() == ChatUserRole.ASSISTANT) {
                messages.add(new AssistantMessage(m.getContent()));
            }
        }
        return messages;
    }

    public String contextBlock(ScreeningContext ctx) {
        StringBuilder sb = new StringBuilder("CONTEXT\n\nCV:\n").append(ctx.cvText())
            .append("\n\nJOB DESCRIPTION (includes the scoring rules used by the screening report):\n").append(ctx.jobText());
        sb.append("\n\n").append(reportHeader(ctx));
        if (ctx.hasReport()) {
            sb.append('\n').append(ctx.matchingReportText());
        }
        return sb.toString();
    }

    /** One line the model cannot overlook: the official score to quote, or the instruction not to state one. */
    static String reportHeader(ScreeningContext ctx) {
        if (!ctx.hasReport()) {
            return "RESUME MATCH ANALYSIS: none generated yet. Do not state a fit percentage or score.";
        }
        StringBuilder sb = new StringBuilder("RESUME MATCH ANALYSIS");
        if (ctx.finalScore() != null) {
            sb.append(" (official screening score: ").append(formatScore(ctx.finalScore()))
              .append("% — quote this figure, do not compute your own");
            if (ctx.reportStale()) {
                sb.append("; the CV was updated after this report was generated, say so");
            }
            sb.append(')');
        } else if (ctx.reportStale()) {
            sb.append(" (the CV was updated after this report was generated, say so)");
        }
        return sb.append(':').toString();
    }

    private static String formatScore(Double score) {
        return score == Math.floor(score) ? String.valueOf(score.intValue()) : String.format(java.util.Locale.ROOT, "%.1f", score);
    }
}
