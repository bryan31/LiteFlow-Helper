package com.yomahub.liteflowhelper.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 为LiteFlow XML规则文件中的脚本节点注入语言，以实现语法高亮。
 * (已适配较新版本的IntelliJ Platform SDK)
 * @author Bryan.Zhang
 */
public class LiteFlowScriptInjector implements LanguageInjector {

    /**
     * 定义所有脚本组件的type名称
     */
    private final List<String> SCRIPT_TYPES = Arrays.asList("script", "boolean_script", "for_script", "switch_script");

    /**
     * IntelliJ Platform会调用此方法，来确定是否需要以及如何进行语言注入。
     * @param host   当前正在处理的、可能作为语言注入目标的PSI元素。
     * @param places 用于注册注入信息（注入的语言、范围等）的接收器。
     */
    @Override
    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        // 步骤1: 检查当前文件是否为LiteFlow的XML规则文件
        PsiFile containingFile = host.getContainingFile();
        // 首先，必须确保文件是一个XML文件实例
        if (!(containingFile instanceof XmlFile)) {
            return;
        }
        // 然后，进行类型转换并调用isLiteFlowXml进行判断
        if (!LiteFlowXmlUtil.isLiteFlowXml((XmlFile) containingFile)) {
            return;
        }

        // 步骤2: 确认注入目标(host)是XML文本(XmlText)，并且其父节点是XML标签(XmlTag)。
        // 在较新的IntelliJ Platform版本中，CDATA块被解析为XmlText，其父节点就是包含它的XmlTag。
        if (!(host instanceof XmlText) || !(host.getParent() instanceof XmlTag)) {
            return;
        }

        // 步骤3: 获取<node>标签
        XmlTag nodeTag = (XmlTag) host.getParent();

        // 步骤4: 校验标签名是否为 "node"，以及其 "type" 属性是否为脚本类型。
        if (!"node".equals(nodeTag.getName())) {
            return;
        }
        String type = nodeTag.getAttributeValue("type");
        if (type == null || !SCRIPT_TYPES.contains(type)) {
            return;
        }

        // 步骤5: 获取 "language" 属性，并根据其值查找对应的 Language 实例。
        String languageName = nodeTag.getAttributeValue("language");
        if (languageName == null) {
            return;
        }
        Language language = getLanguage(languageName);
        if (language == null) {
            // 如果是不支持的语言，则不进行注入
            return;
        }

        // 步骤6: 注入语言。告诉IDEA将host元素内的全部内容(TextRange(0, host.getTextLength()))作为指定的语言进行处理。
        places.addPlace(language, new TextRange(0, host.getTextLength()), null, null);
    }

    /**
     * 根据语言名称字符串获取对应的Language对象。
     * @param languageName 语言名称 (e.g., "java", "groovy", "js", "python", "lua", "kotlin")
     * @return 对应的Language实例；如果找不到或不支持，则返回null。
     */
    private Language getLanguage(String languageName) {
        // 使用findLanguageByID可以避免硬编码依赖，只有当用户安装了对应语言插件时才会生效。
        switch (languageName.toLowerCase()) {
            case "java":
                return Language.findLanguageByID("JAVA");
            case "groovy":
                return Language.findLanguageByID("Groovy");
            case "js":
            case "javascript":
                return Language.findLanguageByID("JavaScript");
            case "python":
                return Language.findLanguageByID("Python");
            case "lua":
                return Language.findLanguageByID("Lua");
            case "kotlin":
                return Language.findLanguageByID("kotlin");
            default:
                return null;
        }
    }
}
