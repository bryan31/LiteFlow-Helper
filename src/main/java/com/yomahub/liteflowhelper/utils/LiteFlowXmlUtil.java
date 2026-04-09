package com.yomahub.liteflowhelper.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LiteFlow PSI 元素相关工具类。
 */
public final class LiteFlowXmlUtil {
    private static final Logger LOG = Logger.getInstance(LiteFlowXmlUtil.class);

    private static final Set<String> EL_KEYWORDS = Stream.of(
            "THEN", "WHEN", "SER", "PAR", "SWITCH", "PRE", "FINALLY", "IF", "NODE",
            "node", "FOR", "WHILE", "ITERATOR", "CATCH", "AND", "OR", "NOT", "ELSE",
            "ELIF", "TO", "to", "DEFAULT", "tag", "any", "must", "id", "ignoreError",
            "threadPool", "DO", "BREAK", "data", "maxWaitSeconds", "maxWaitMilliseconds",
            "parallel", "retry", "bind", "percentage"
    ).collect(Collectors.toSet());

    public static final String LITEFLOW_COMPONENT_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowComponent";
    public static final String SPRING_COMPONENT_ANNOTATION = "org.springframework.stereotype.Component";
    public static final String LITEFLOW_METHOD_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowMethod";
    public static final String NODE_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeComponent";
    public static final String LITEFLOW_NODE_TYPE_ENUM_FQ = "com.yomahub.liteflow.enums.NodeTypeEnum";
    public static final String LITEFLOW_METHOD_ENUM_FQ = "com.yomahub.liteflow.enums.LiteFlowMethodEnum";

    public static final Set<String> PROCESS_METHOD_TYPES = new HashSet<>(Arrays.asList(
            "PROCESS", "PROCESS_SWITCH", "PROCESS_BOOLEAN", "PROCESS_FOR", "PROCESS_ITERATOR"
    ));

    private LiteFlowXmlUtil() {
    }

    public static boolean isElKeyword(@Nullable String text) {
        return !StringUtil.isEmpty(text) && EL_KEYWORDS.contains(text);
    }

    public static Set<String> getElKeywords() {
        return EL_KEYWORDS;
    }

    public static boolean isLiteFlowXml(@Nullable XmlFile xmlFile) {
        if (xmlFile == null) {
            return false;
        }

        XmlDocument document = xmlFile.getDocument();
        return document != null && isLiteFlowXml(document.getRootTag());
    }

    public static boolean isLiteFlowXml(@Nullable XmlTag rootTag) {
        return rootTag != null && "flow".equals(rootTag.getName()) && rootTag.findSubTags("chain").length > 0;
    }

    @Nullable
    public static XmlTag getNodesTag(@Nullable XmlTag flowRootTag) {
        if (flowRootTag == null) {
            return null;
        }
        return "flow".equals(flowRootTag.getName()) ? flowRootTag.findFirstSubTag("nodes") : null;
    }

    public static boolean isInheritanceComponent(@NotNull PsiClass psiClass) {
        Project project = psiClass.getProject();
        PsiClass nodeComponentBaseClass = JavaPsiFacade.getInstance(project)
                .findClass(NODE_COMPONENT_CLASS, GlobalSearchScope.allScope(project));
        return isInheritanceComponent(psiClass, nodeComponentBaseClass);
    }

    public static boolean isInheritanceComponent(@NotNull PsiClass psiClass, @Nullable PsiClass nodeComponentBaseClass) {
        if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        return nodeComponentBaseClass != null && psiClass.isInheritor(nodeComponentBaseClass, true);
    }

    public static boolean isClassDeclarativeComponent(@NotNull PsiClass psiClass) {
        Project project = psiClass.getProject();
        PsiClass nodeComponentBaseClass = JavaPsiFacade.getInstance(project)
                .findClass(NODE_COMPONENT_CLASS, GlobalSearchScope.allScope(project));
        return isClassDeclarativeComponent(psiClass, nodeComponentBaseClass);
    }

    public static boolean isClassDeclarativeComponent(@NotNull PsiClass psiClass, @Nullable PsiClass nodeComponentBaseClass) {
        if (psiClass.isInterface()
                || psiClass.isAnnotationType()
                || psiClass.isEnum()
                || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        if (isInheritanceComponent(psiClass, nodeComponentBaseClass)) {
            return false;
        }
        if (!hasComponentAnnotation(psiClass)) {
            return false;
        }

        List<PsiAnnotation> liteflowMethodAnnotations = new ArrayList<>();
        for (PsiMethod method : psiClass.getMethods()) {
            PsiAnnotation lfMethodAnnotation = method.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
            if (lfMethodAnnotation != null) {
                liteflowMethodAnnotations.add(lfMethodAnnotation);
            }
        }

        if (liteflowMethodAnnotations.isEmpty()) {
            return false;
        }

        Set<String> nodeTypeValues = new HashSet<>();
        for (PsiAnnotation annotation : liteflowMethodAnnotations) {
            String nodeId = getAnnotationAttributeValue(annotation, "nodeId");
            if (!StringUtil.isEmpty(nodeId)) {
                return false;
            }

            String currentEnumConstantName = getAnnotationEnumValue(annotation, "nodeType", LITEFLOW_NODE_TYPE_ENUM_FQ);
            if (currentEnumConstantName == null) {
                currentEnumConstantName = "COMMON";
            }
            nodeTypeValues.add(currentEnumConstantName);
        }

        return nodeTypeValues.size() <= 1;
    }

    public static boolean hasComponentAnnotation(@NotNull PsiClass psiClass) {
        return psiClass.getAnnotation(LITEFLOW_COMPONENT_ANNOTATION) != null
                || psiClass.getAnnotation(SPRING_COMPONENT_ANNOTATION) != null;
    }

    public static boolean isMethodDeclarativeComponent(@NotNull PsiMethod psiMethod) {
        PsiAnnotation lfMethodAnnotation = psiMethod.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
        if (lfMethodAnnotation == null) {
            return false;
        }

        String methodType = getAnnotationEnumValue(lfMethodAnnotation, "value", LITEFLOW_METHOD_ENUM_FQ);
        if (methodType == null || !PROCESS_METHOD_TYPES.contains(methodType)) {
            return false;
        }

        String nodeId = getAnnotationAttributeValue(lfMethodAnnotation, "nodeId");
        return !StringUtil.isEmpty(nodeId);
    }

    public static boolean isMethodDeclarativeComponent(@NotNull PsiClass psiClass) {
        for (PsiMethod method : psiClass.getMethods()) {
            if (isMethodDeclarativeComponent(method)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String getNodeIdFromComponentAnnotations(@NotNull PsiClass psiClass) {
        PsiAnnotation liteflowAnnotation = psiClass.getAnnotation(LITEFLOW_COMPONENT_ANNOTATION);
        if (liteflowAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(liteflowAnnotation, "value");
            if (StringUtil.isEmpty(nodeId)) {
                nodeId = getAnnotationAttributeValue(liteflowAnnotation, "id");
            }
            if (!StringUtil.isEmpty(nodeId)) {
                return nodeId;
            }
        }

        PsiAnnotation springAnnotation = psiClass.getAnnotation(SPRING_COMPONENT_ANNOTATION);
        if (springAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(springAnnotation, "value");
            if (!StringUtil.isEmpty(nodeId)) {
                return nodeId;
            }
        }
        return null;
    }

    @Nullable
    public static String convertClassNameToCamelCase(@Nullable String className) {
        if (StringUtil.isEmpty(className)) {
            return null;
        }
        if (className.length() > 1
                && Character.isUpperCase(className.charAt(0))
                && Character.isLowerCase(className.charAt(1))) {
            return Character.toLowerCase(className.charAt(0)) + className.substring(1);
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    @Nullable
    public static String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression literalExpression) {
            Object literalValue = literalExpression.getValue();
            return literalValue instanceof String ? (String) literalValue : null;
        }
        return null;
    }

    @Nullable
    public static String getAnnotationEnumValue(
            @NotNull PsiAnnotation annotation,
            @NotNull String attributeName,
            @NotNull String expectedEnumFqName
    ) {
        PsiAnnotationMemberValue attrValue = annotation.findAttributeValue(attributeName);
        if (attrValue instanceof PsiReferenceExpression referenceExpression) {
            PsiElement resolved = referenceExpression.resolve();
            if (resolved instanceof PsiEnumConstant enumConstant) {
                PsiClass containingEnum = enumConstant.getContainingClass();
                if (containingEnum != null && expectedEnumFqName.equals(containingEnum.getQualifiedName())) {
                    return enumConstant.getName();
                }
                LOG.warn("在注解 " + annotation.getQualifiedName() + " 中发现未知的枚举来源: " + attrValue.getText());
            }
        }
        return null;
    }

    public static boolean isChainEL(@Nullable XmlAttributeValue attributeValue) {
        if (attributeValue == null) {
            return false;
        }

        PsiElement attribute = attributeValue.getParent();
        if (!(attribute instanceof XmlAttribute xmlAttribute)) {
            return false;
        }

        PsiElement tag = attribute.getParent();
        if (!(tag instanceof XmlTag xmlTag)) {
            return false;
        }

        String attributeName = xmlAttribute.getName();
        String tagName = xmlTag.getName();

        if ("chain".equals(tagName) && "value".equals(attributeName)) {
            return true;
        }

        if ("node".equals(tagName)) {
            return "then".equals(attributeName)
                    || "when".equals(attributeName)
                    || "for".equals(attributeName)
                    || "while".equals(attributeName)
                    || "if".equals(attributeName);
        }
        return false;
    }
}
