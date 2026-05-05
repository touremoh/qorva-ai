package ai.qorva.core.security;

/**
 * Thread-local holder for the Accept-Language value of the current request.
 * Populated by controllers on write operations and cleared after the filter chain completes.
 */
public final class LanguageContextHolder {

    private static final ThreadLocal<String> LANGUAGE = new ThreadLocal<>();

    private LanguageContextHolder() {}

    public static void setLanguage(String language) {
        LANGUAGE.set(language);
    }

    public static String getLanguage() {
        String lang = LANGUAGE.get();
        return (lang != null && !lang.isBlank()) ? lang : "en";
    }

    public static void clear() {
        LANGUAGE.remove();
    }
}
