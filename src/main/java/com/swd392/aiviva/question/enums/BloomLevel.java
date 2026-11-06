package com.swd392.aiviva.question.enums;

import java.util.Arrays;
import java.util.Locale;

public enum BloomLevel {
    REMEMBER("Nhớ", "Remember", 1, "Nhận diện và nêu lại kiến thức, định nghĩa chính xác."),
    UNDERSTAND("Hiểu", "Understand", 2, "Giải thích ý nghĩa và mối quan hệ giữa các khái niệm."),
    APPLY("Vận dụng", "Apply", 3, "Áp dụng kiến thức vào tình huống hoặc bài toán cụ thể."),
    ANALYZE("Phân tích", "Analyze", 4, "Tách thành phần, tìm quan hệ và lập luận từ dữ liệu."),
    EVALUATE("Đánh giá", "Evaluate", 5, "Đánh giá giải pháp bằng tiêu chí và bằng chứng phù hợp."),
    CREATE("Sáng tạo", "Create", 6, "Thiết kế giải pháp mới có tính khả thi và lập luận rõ ràng.");

    private final String labelVi;
    private final String labelEn;
    private final int order;
    private final String description;

    BloomLevel(String labelVi, String labelEn, int order, String description) {
        this.labelVi = labelVi;
        this.labelEn = labelEn;
        this.order = order;
        this.description = description;
    }

    public String getLabelVi() { return labelVi; }
    public String getLabelEn() { return labelEn; }
    public int getOrder() { return order; }
    public String getDescription() { return description; }

    public static BloomLevel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bloom level is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(level -> level.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid bloom level: " + value + ". Valid values: " +
                                Arrays.toString(values())));
    }

    public static String normalizeLegacyValue(String value) {
        if (value == null) return null;
        String normalized = parse(value).name();
        // Kept only for compatibility with the pre-six-level workflow helper.
        return "EVALUATE".equals(normalized) ? ANALYZE.name() : normalized;
    }
}

