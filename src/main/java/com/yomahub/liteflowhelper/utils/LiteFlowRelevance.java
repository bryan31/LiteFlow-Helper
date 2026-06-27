package com.yomahub.liteflowhelper.utils;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * 判断一个文件是否"可能"与 LiteFlow 相关，用于决定是否触发缓存刷新。
 * 这是一个轻量的、基于扩展名/文件名的启发式判断，仅用于减少不必要的全量扫描；
 * 误判（漏判或冗余刷新）只影响刷新时机，不影响正确性。
 */
public final class LiteFlowRelevance {

    private LiteFlowRelevance() {
    }

    public static boolean isRelevant(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        String extension = file.getExtension();

        // Java 文件：可能是组件类
        if ("java".equalsIgnoreCase(extension)) {
            String path = file.getPath();
            // 排除明显不相关的目录
            if (path.contains("/test/")
                    || path.contains("/generated/")
                    || path.contains("/build/")
                    || path.contains("/target/")) {
                return false;
            }
            return true;
        }

        // XML 文件：仅关注文件名疑似 LiteFlow 配置的
        if ("xml".equalsIgnoreCase(extension)) {
            String name = file.getName().toLowerCase();
            return name.contains("flow")
                    || name.contains("liteflow")
                    || name.contains("chain")
                    || name.contains("rule");
        }

        return false;
    }
}
