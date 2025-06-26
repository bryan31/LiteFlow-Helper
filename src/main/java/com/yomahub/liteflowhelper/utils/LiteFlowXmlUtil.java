package com.yomahub.liteflowhelper.utils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LiteFlow PSI元素（包括XML和Java类）相关的工具类。
 */
public class LiteFlowXmlUtil {

    private static final Logger LOG = Logger.getInstance(LiteFlowXmlUtil.class);

    //<editor-fold desc="LiteFlow EL关键字">
    /**
     * LiteFlow的EL关键字集合
     * 严格区分大小写
     */
    private static final Set<String> EL_KEYWORDS = Stream.of(
            "THEN", "WHEN", "SER", "PAR", "SWITCH", "PRE", "FINALLY", "IF", "NODE",
            "node", "FOR", "WHILE", "ITERATOR", "CATCH", "AND", "OR", "NOT", "ELSE",
            "ELIF", "TO", "to", "DEFAULT", "tag", "any", "must", "id", "ignoreError",
            "threadPool", "DO", "BREAK", "data", "maxWaitSeconds", "maxWaitMilliseconds",
            "parallel", "retry", "bind"
    ).collect(Collectors.toSet());
    //</editor-fold>

    //<editor-fold desc="LiteFlow相关常量">
    public static final String LITEFLOW_COMPONENT_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowComponent";
    public static final String SPRING_COMPONENT_ANNOTATION = "org.springframework.stereotype.Component";
    public static final String LITEFLOW_METHOD_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowMethod";
    public static final String NODE_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeComponent";

    // LiteFlow NodeTypeEnum 和 LiteFlowMethodEnum 的完全限定名
    public static final String LITEFLOW_NODE_TYPE_ENUM_FQ = "com.yomahub.liteflow.enums.NodeTypeEnum";
    public static final String LITEFLOW_METHOD_ENUM_FQ = "com.yomahub.liteflow.enums.LiteFlowMethodEnum";

    // 用于识别方法级组件的流程类型
    public static final Set<String> PROCESS_METHOD_TYPES = new HashSet<>(Arrays.asList(
            "PROCESS", "PROCESS_SWITCH", "PROCESS_BOOLEAN", "PROCESS_FOR", "PROCESS_ITERATOR"
    ));
    //</editor-fold>

    //<editor-fold desc="EL关键字判断">

    /**
     * 判断一个字符串是否是LiteFlow的EL关键字（大小写敏感）。
     *
     * @param text 要检查的文本
     * @return 如果是关键字则返回 true，否则返回 false
     */
    public static boolean isElKeyword(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        // [修改] 进行严格区分大小写的直接包含检查
        return EL_KEYWORDS.contains(text);
    }

    /**
     * 新增: 返回所有EL关键字的集合，用于代码补全。
     *
     * @return EL关键字集合
     */
    public static Set<String> getElKeywords() {
        return EL_KEYWORDS;
    }

    //</editor-fold>

    //<editor-fold desc="XML处理方法">

    /**
     * 判断一个 XML 文件是否是 LiteFlow 的配置文件。
     * LiteFlow 配置文件的特征是：
     * 1. 根标签是 <flow>
     * 2. 必须包含至少一个 <chain> 标签
     *
     * @param xmlFile 要检查的 XmlFile 对象
     * @return 如果是 LiteFlow XML 文件则返回 true，否则返回 false。
     */
    public static boolean isLiteFlowXml(@Nullable XmlFile xmlFile) {
        if (xmlFile == null) {
            return false;
        }
        XmlDocument document = xmlFile.getDocument();
        if (document == null) {
            return false;
        }
        XmlTag rootTag = document.getRootTag();
        // 检查根标签是否为 "flow" 并且至少包含一个 "chain" 子标签
        return rootTag != null && "flow".equals(rootTag.getName()) && rootTag.findSubTags("chain").length > 0;
    }

    /**
     * 从 LiteFlow 的根 <flow> 标签中获取 <nodes> 标签。
     *
     * @param flowRootTag LiteFlow XML 的根 <flow> 标签。如果为 null，则直接返回 null。
     * @return <nodes> 标签，如果不存在则返回 null。
     */
    @Nullable
    public static XmlTag getNodesTag(@Nullable XmlTag flowRootTag) {
        if (flowRootTag == null) {
            return null;
        }
        if ("flow".equals(flowRootTag.getName())) {
            return flowRootTag.findFirstSubTag("nodes");
        }
        return null;
    }
    //</editor-fold>

    //<editor-fold desc="核心判断逻辑 (Refactored Methods)">

    /**
     * [重构] 判断一个类是否为 LiteFlow 的继承式组件。
     * 该方法会自行获取 Project 和 NodeComponent 基类。
     * 条件：
     * 1. 是一个具体的类 (非接口、非抽象类)。
     * 2. 直接或间接继承自 com.yomahub.liteflow.core.NodeComponent。
     * @param psiClass 要判断的类。
     * @return 如果是继承式组件则返回 true。
     */
    public static boolean isInheritanceComponent(@NotNull PsiClass psiClass) {
        // 检查是否为具体类
        if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        // 自行获取项目和基类
        Project project = psiClass.getProject();
        PsiClass nodeComponentBaseClass = JavaPsiFacade.getInstance(project).findClass(NODE_COMPONENT_CLASS, GlobalSearchScope.allScope(project));

        // 检查继承关系
        return nodeComponentBaseClass != null && psiClass.isInheritor(nodeComponentBaseClass, true);
    }

    /**
     * [重构] 判断一个类是否为 LiteFlow 的类声明式组件。
     * 条件：
     * 1. 是一个具体的、非继承式的组件类。
     * 2. 必须拥有 @LiteflowComponent 或 @Component 注解。
     * 3. 必须包含至少一个 @LiteflowMethod 注解。
     * 4. 所有 @LiteflowMethod 注解都不能定义 `nodeId` 属性。
     * 5. 所有 @LiteflowMethod 注解的 `nodeType` 属性值必须相同 (或都为默认值)。
     * @param psiClass 要判断的类。
     * @return 如果是类声明式组件则返回 true。
     */
    public static boolean isClassDeclarativeComponent(@NotNull PsiClass psiClass) {
        // 条件1: 必须是具体类，且不能是继承式组件
        if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        // 调用重构后的 isInheritanceComponent 方法
        if (isInheritanceComponent(psiClass)) {
            return false;
        }

        // 条件2: 必须有 @LiteflowComponent 或 @Component 注解
        if (psiClass.getAnnotation(LITEFLOW_COMPONENT_ANNOTATION) == null && psiClass.getAnnotation(SPRING_COMPONENT_ANNOTATION) == null) {
            return false;
        }

        // 条件3, 4, 5: 检查 @LiteflowMethod 注解
        List<PsiAnnotation> liteflowMethodAnnotations = new ArrayList<>();
        for (PsiMethod method : psiClass.getMethods()) {
            PsiAnnotation lfMethodAnnotation = method.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
            if (lfMethodAnnotation != null) {
                liteflowMethodAnnotations.add(lfMethodAnnotation);
            }
        }

        if (liteflowMethodAnnotations.isEmpty()) {
            return false; // 必须有 @LiteflowMethod
        }

        Set<String> nodeTypeValues = new HashSet<>();
        for (PsiAnnotation annotation : liteflowMethodAnnotations) {
            // [新增逻辑] 检查 nodeId 属性，如果存在，则不是类声明式组件
            String nodeId = getAnnotationAttributeValue(annotation, "nodeId");
            if (nodeId != null && !nodeId.trim().isEmpty()) {
                return false;
            }

            // 检查 nodeType 属性
            String currentEnumConstantName = getAnnotationEnumValue(annotation, "nodeType", LITEFLOW_NODE_TYPE_ENUM_FQ);
            if (currentEnumConstantName == null) {
                currentEnumConstantName = "COMMON"; // 注解未指定nodeType时的默认值
            }
            nodeTypeValues.add(currentEnumConstantName);
        }

        if (nodeTypeValues.size() > 1) {
            return false; // 所有 nodeType 必须相同
        }

        return true;
    }

    /**
     * 判断一个方法是否为 LiteFlow 的方法声明式组件。
     * 注意：这个方法只检查方法本身，其所属的类是否符合容器条件需要在外部判断。
     * 条件:
     * 1. 方法被 @LiteflowMethod 标注。
     * 2. @LiteflowMethod 的 `value` 属性必须是指定的流程类型 (PROCESS, PROCESS_SWITCH 等)。
     * 3. @LiteflowMethod 必须指定 `nodeId` 属性。
     * @param psiMethod 要判断的方法。
     * @return 如果是方法声明式组件则返回 true。
     */
    public static boolean isMethodDeclarativeComponent(@NotNull PsiMethod psiMethod) {
        PsiAnnotation lfMethodAnnotation = psiMethod.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
        if (lfMethodAnnotation == null) {
            return false;
        }

        // 检查 `value` 属性是否为指定的流程类型
        String methodType = getAnnotationEnumValue(lfMethodAnnotation, "value", LITEFLOW_METHOD_ENUM_FQ);
        if (methodType == null || !PROCESS_METHOD_TYPES.contains(methodType)) {
            return false;
        }

        // 检查 `nodeId` 属性是否存在且非空
        String nodeId = getAnnotationAttributeValue(lfMethodAnnotation, "nodeId");
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * [新增重载方法] 判断一个类是否包含任何方法声明式组件。
     *
     * @param psiClass 要判断的类。
     * @return 如果该类中至少有一个方法是方法声明式组件，则返回 true。
     */
    public static boolean isMethodDeclarativeComponent(@NotNull PsiClass psiClass) {
        for (PsiMethod method : psiClass.getMethods()) {
            if (isMethodDeclarativeComponent(method)) {
                return true;
            }
        }
        return false;
    }
    //</editor-fold>

    //<editor-fold desc="PSI辅助方法">
    /**
     * 从 @LiteflowComponent 或 @Component 注解中提取节点ID。
     * 优先使用 @LiteflowComponent("value")，其次是 @Component("value")。
     */
    @Nullable
    public static String getNodeIdFromComponentAnnotations(@NotNull PsiClass psiClass) {
        PsiAnnotation liteflowAnnotation = psiClass.getAnnotation(LITEFLOW_COMPONENT_ANNOTATION);
        if (liteflowAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(liteflowAnnotation, "value");
            if (StringUtils.isBlank(nodeId)) {
                nodeId = getAnnotationAttributeValue(liteflowAnnotation, "id");
            }
            if (nodeId != null && !nodeId.trim().isEmpty()) {
                return nodeId;
            }
        }

        PsiAnnotation springAnnotation = psiClass.getAnnotation(SPRING_COMPONENT_ANNOTATION);
        if (springAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(springAnnotation, "value");
            if (nodeId != null && !nodeId.trim().isEmpty()) {
                return nodeId;
            }
        }
        return null;
    }

    /**
     * 将类名转换为小驼峰命名法。
     */
    @Nullable
    public static String convertClassNameToCamelCase(@Nullable String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.length() > 1 && Character.isUpperCase(className.charAt(0)) && Character.isLowerCase(className.charAt(1))) {
            return Character.toLowerCase(className.charAt(0)) + className.substring(1);
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * 获取注解中指定属性的值 (仅限字符串类型)。
     */
    @Nullable
    public static String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression) {
            Object literalValue = ((PsiLiteralExpression) value).getValue();
            return literalValue instanceof String ? (String) literalValue : null;
        }
        return null;
    }

    /**
     * 解析注解中指定的枚举类型属性值。
     * @return 枚举常量的名称 (String)，如果未找到或类型不匹配则返回 null。
     */
    @Nullable
    public static String getAnnotationEnumValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, @NotNull String expectedEnumFqName) {
        PsiAnnotationMemberValue attrValue = annotation.findAttributeValue(attributeName);
        if (attrValue instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) attrValue).resolve();
            if (resolved instanceof PsiEnumConstant) {
                PsiEnumConstant enumConstant = (PsiEnumConstant) resolved;
                PsiClass containingEnum = enumConstant.getContainingClass();
                if (containingEnum != null && expectedEnumFqName.equals(containingEnum.getQualifiedName())) {
                    return enumConstant.getName();
                } else {
                    LOG.warn("在注解 " + annotation.getQualifiedName() + " 中发现未知的枚举来源: " + attrValue.getText());
                }
            }
        }
        return null;
    }

    /**
     * [新增方法]
     * 判断一个XML属性值是否为LiteFlow的EL表达式。
     * 主要用于限定括号匹配、代码补全等功能的生效范围。
     *
     * @param attributeValue 要检查的XML属性值
     * @return 如果是EL表达式则返回true，否则返回false
     */
    public static boolean isChainEL(@Nullable XmlAttributeValue attributeValue) {
        if (attributeValue == null) {
            return false;
        }
        PsiElement attribute = attributeValue.getParent();
        if (!(attribute instanceof XmlAttribute)) {
            return false;
        }
        String attributeName = ((XmlAttribute) attribute).getName();

        PsiElement tag = attribute.getParent();
        if (!(tag instanceof XmlTag)) {
            return false;
        }
        String tagName = ((XmlTag) tag).getName();

        // 场景1: <chain value="...">
        if ("chain".equals(tagName) && "value".equals(attributeName)) {
            return true;
        }

        // 场景2: <node ... then="..." when="..." for="..." while="..." if="...">
        if ("node".equals(tagName)) {
            return "then".equals(attributeName) ||
                    "when".equals(attributeName) ||
                    "for".equals(attributeName) ||
                    "while".equals(attributeName) ||
                    "if".equals(attributeName);
        }
        return false;
    }
    //</editor-fold>
}
