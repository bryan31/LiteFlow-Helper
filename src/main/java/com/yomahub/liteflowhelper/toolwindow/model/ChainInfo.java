package com.yomahub.liteflowhelper.toolwindow.model;

import com.intellij.psi.PsiFile;

/**
 * 用于存储 LiteFlow Chain 的信息
 */
public class ChainInfo {
    private final String name; // Chain 的名称 (id 或 name)
    private final PsiFile psiFile; // Chain 所在的 PsiFile 对象
    private final int offset; // Chain 标签在文件中的文本偏移量
    private final String fileName; // Chain 所在的文件名

    public ChainInfo(String name, PsiFile psiFile, int offset) {
        this.name = name;
        this.psiFile = psiFile;
        this.offset = offset;
        this.fileName = psiFile.getName(); // 获取文件名
    }

    public String getName() {
        return name;
    }

    public PsiFile getPsiFile() {
        return psiFile;
    }

    public int getOffset() {
        return offset;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * 重写 toString 方法，用于在树节点中显示
     * 格式: chainName (fileName)
     */
    @Override
    public String toString() {
        return name + " (" + fileName + ")";
    }
}