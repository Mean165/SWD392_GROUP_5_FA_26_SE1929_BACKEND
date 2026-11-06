package com.swd392.aiviva.question.infrastructure.ai;

public class GeminiApiException extends RuntimeException {
    private final int statusCode;
    private final long retryAfterSeconds;
    private final String userMessage;
    private final boolean quotaExceeded;

    public GeminiApiException(int statusCode, long retryAfterSeconds, String providerMessage) {
        super("Gemini API returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        String detail = providerMessage == null ? "" : providerMessage.toLowerCase(java.util.Locale.ROOT);
        this.quotaExceeded = detail.contains("quota") || detail.contains("billing");
        this.userMessage = messageFor(statusCode, providerMessage, retryAfterSeconds);
    }

    public int statusCode() { return statusCode; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
    public String userMessage() { return userMessage; }
    public boolean retryable() { return statusCode == 429 && !quotaExceeded; }

    private static String messageFor(int status, String providerMessage, long retryAfterSeconds) {
        String detail = providerMessage == null ? "" : providerMessage.toLowerCase(java.util.Locale.ROOT);
        if (status == 429) {
            String retry = retryAfterSeconds > 0 ? " Thử lại sau " + retryAfterSeconds + " giây." : " Hãy thử lại sau.";
            if (detail.contains("quota") || detail.contains("billing"))
                return "Gemini đã hết quota hoặc cần kiểm tra billing." + retry;
            return "Gemini đang giới hạn tốc độ gọi API." + retry;
        }
        if (status == 401 || status == 403) return "Gemini từ chối API key hoặc key chưa được cấp quyền.";
        if (status == 404) return "Không tìm thấy model Gemini đã cấu hình; hãy kiểm tra GEMINI_MODEL hoặc GEMINI_EMBEDDING_MODEL.";
        if (status == 400) return "Gemini từ chối yêu cầu; hãy kiểm tra nội dung hoặc cấu hình model.";
        if (status >= 500) return "Gemini đang gặp sự cố dịch vụ. Hãy thử lại sau.";
        return "Gemini API gặp lỗi HTTP " + status + ".";
    }
}
