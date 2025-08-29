package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dto.ScreeningContext;
import io.jsonwebtoken.lang.Strings;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class PromptComposer {
    public String compose(ScreeningContext ctx, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("SYSTEM:\nYou are Qorva AI. Answer strictly from the candidate CV, job post, and resume match analysis.\n\n");
        sb.append("CONTEXT:\n");
        sb.append("CV:\n").append(ctx.cvText()).append("\n\n");
        sb.append("JOB DESCRIPTION:\n").append(ctx.jobText()).append("\n\n");
        if (Strings.hasText(ctx.resumeMatchText())) {
            sb.append("RESUME MATCH ANALYSIS:\n").append(ctx.resumeMatchText()).append("\n\n");
        }

        sb.append("HISTORY:\n");
        for (ChatMessage m : history) {
            sb.append(m.getRole().name()).append(": ").append(m.getContent()).append("\n");
        }
        sb.append("\nASSISTANT:"); // the model continues here
        return sb.toString();
    }
}