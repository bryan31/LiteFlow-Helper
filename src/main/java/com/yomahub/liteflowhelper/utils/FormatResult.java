package com.yomahub.liteflowhelper.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EL 格式化结果：成功时携带格式化文本，失败时携带原因（用于 UI 提示）。
 */
public final class FormatResult {

    public final boolean success;
    public final @Nullable String formatted;
    public final @Nullable String reason;

    private FormatResult(boolean success, @Nullable String formatted, @Nullable String reason) {
        this.success = success;
        this.formatted = formatted;
        this.reason = reason;
    }

    public static @NotNull FormatResult success(@NotNull String formatted) {
        return new FormatResult(true, formatted, null);
    }

    public static @NotNull FormatResult failure(@NotNull String reason) {
        return new FormatResult(false, null, reason);
    }
}
