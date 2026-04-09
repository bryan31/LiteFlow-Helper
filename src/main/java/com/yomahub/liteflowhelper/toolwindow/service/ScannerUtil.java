package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 扫描器工具类，提供通用的读操作封装。
 */
public final class ScannerUtil {

    public static <T> List<T> runInReadAction(
            @NotNull Project project,
            @NotNull String taskName,
            @NotNull Supplier<List<T>> scanner) {
        return runInReadAction(project, taskName, Collections.emptyList(), scanner);
    }

    public static <T> T runInReadAction(
            @NotNull Project project,
            @NotNull String taskName,
            @NotNull T fallbackValue,
            @NotNull Supplier<T> scanner) {
        Logger log = Logger.getInstance(ScannerUtil.class);

        if (DumbService.getInstance(project).isDumb()) {
            log.info("项目处于 dumb mode，跳过 " + taskName + " 扫描");
            return fallbackValue;
        }

        log.info("========== 开始扫描 " + taskName + " ==========");

        return ApplicationManager.getApplication().runReadAction((Computable<T>) () -> {
            if (project.isDisposed()) {
                return fallbackValue;
            }
            if (DumbService.getInstance(project).isDumb()) {
                log.info("调度 " + taskName + " 读操作期间，项目进入 dumb mode，跳过本次扫描");
                return fallbackValue;
            }

            T result = scanner.get();
            if (result instanceof List<?>) {
                log.info("========== 扫描完成，共找到 " + ((List<?>) result).size() + " 个 " + taskName + " ==========");
            } else {
                log.info("========== 扫描完成: " + taskName + " ==========");
            }
            return result;
        });
    }

    private ScannerUtil() {
    }
}
