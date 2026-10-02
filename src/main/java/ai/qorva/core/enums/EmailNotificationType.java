package ai.qorva.core.enums;

public enum EmailNotificationType {
    SUBSCRIPTION_WELCOME,
    SUBSCRIPTION_UPDATED,
    SUBSCRIPTION_RENEWAL,
    USER_ADDED,
    DEMO_WELCOME,
    PASSWORD_RESET,
    /** Copilot rule runs waiting for the owner's approval (at most one per user per hour). */
    AGENT_APPROVAL_DIGEST
}
