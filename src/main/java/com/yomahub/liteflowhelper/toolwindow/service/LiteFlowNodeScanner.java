package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ide.highlighter.XmlFileType;

/**
 * 负责扫描项目中的 LiteFlow 节点定义。
 * 包括基于Java类的继承式节点、XML中定义的脚本节点以及声明式注解节点。
 */
public class LiteFlowNodeScanner {

    private static final Logger LOG = Logger.getInstance(LiteFlowNodeScanner.class);
    private static final String LITEFLOW_COMPONENT_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowComponent";
    private static final String SPRING_COMPONENT_ANNOTATION = "org.springframework.stereotype.Component";
    private static final String LITEFLOW_METHOD_ANNOTATION = "com.yomahub.liteflow.annotation.LiteflowMethod";
    private static final String NODE_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeComponent";
    // LiteFlow核心节点组件的基类，用于查找它们的子类 (继承式)
    private static final String[] LITEFLOW_BASE_CLASSES = {
            NODE_COMPONENT_CLASS,
            "com.yomahub.liteflow.core.NodeSwitchComponent",
            "com.yomahub.liteflow.core.NodeBooleanComponent",
            "com.yomahub.liteflow.core.NodeForComponent",
            "com.yomahub.liteflow.core.NodeIteratorComponent"
    };

    // LiteFlow NodeTypeEnum 和 LiteFlowMethodEnum 的完全限定名
    private static final String LITEFLOW_NODE_TYPE_ENUM_FQ = "com.yomahub.liteflow.enums.NodeTypeEnum";
    private static final String LITEFLOW_METHOD_ENUM_FQ = "com.yomahub.liteflow.enums.LiteFlowMethodEnum";

    // 用于识别方法级组件的流程类型
    private static final Set<String> PROCESS_METHOD_TYPES = new HashSet<>(Arrays.asList(
            "PROCESS", "PROCESS_SWITCH", "PROCESS_BOOLEAN", "PROCESS_FOR", "PROCESS_ITERATOR"
    ));


    /**
     * 查找项目中的所有LiteFlow节点。
     * @param project 当前项目
     * @return LiteFlowNodeInfo 列表，如果项目处于Dumb Mode则返回空列表。
     */
    public List<LiteFlowNodeInfo> findLiteFlowNodes(@NotNull Project project) {
        if (DumbService.getInstance(project).isDumb()) {
            LOG.info("项目正处于 dumb mode。LiteFlow 节点扫描已推迟。");
            return Collections.emptyList();
        }

        return ApplicationManager.getApplication().runReadAction((Computable<List<LiteFlowNodeInfo>>) () -> {
            if (project.isDisposed()) {
                return Collections.emptyList();
            }
            if (DumbService.getInstance(project).isDumb()) {
                LOG.info("在为LiteFlow节点调度读操作期间，项目进入了 dumb mode。正在跳过。");
                return Collections.emptyList();
            }

            List<LiteFlowNodeInfo> nodeInfos = new ArrayList<>();
            nodeInfos.addAll(findJavaClassInheritanceNodes(project));
            nodeInfos.addAll(findXmlScriptNodes(project));
            nodeInfos.addAll(findDeclarativeClassNodes(project)); // 扫描类声明式组件
            nodeInfos.addAll(findDeclarativeMethodNodes(project)); // 新增：扫描方法声明式组件
            return nodeInfos;
        });
    }

    /**
     * 查找项目中所有基于Java类继承定义的LiteFlow节点。
     */
    private List<LiteFlowNodeInfo> findJavaClassInheritanceNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> javaNodeInfos = new ArrayList<>();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);

        for (String baseClassName : LITEFLOW_BASE_CLASSES) {
            if (project.isDisposed()) return Collections.emptyList();

            PsiClass baseClass = psiFacade.findClass(baseClassName, allScope);
            if (baseClass != null) {
                Collection<PsiClass> inheritors = ClassInheritorsSearch.search(baseClass, projectScope, true).findAll();
                for (PsiClass inheritor : inheritors) {
                    if (project.isDisposed()) return Collections.emptyList();
                    if (inheritor.isInterface() || inheritor.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        continue;
                    }

                    String nodeIdFromAnnotation = getNodeIdFromComponentAnnotations(inheritor);
                    String nodeName = inheritor.getName(); // 类名
                    String primaryId = nodeIdFromAnnotation;

                    if (primaryId == null) { // 如果注解没有提供ID，则使用类名小驼峰
                        primaryId = convertClassNameToCamelCase(inheritor.getName());
                    }

                    if (primaryId == null || primaryId.trim().isEmpty()) {
                        LOG.warn("跳过继承式组件，无法确定Node ID: " + inheritor.getQualifiedName());
                        continue;
                    }

                    NodeType nodeType = determineNodeTypeFromSuperClass(inheritor, baseClassName);
                    javaNodeInfos.add(new LiteFlowNodeInfo(primaryId, nodeName, nodeType, inheritor, "继承式"));
                }
            }
        }
        return javaNodeInfos;
    }

    /**
     * 扫描声明式的类组件
     */
    private List<LiteFlowNodeInfo> findDeclarativeClassNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> declarativeNodeInfos = new ArrayList<>();
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);

        Collection<PsiClass> matchedClasses = new ArrayList<>();

        PsiClass springComponentBaseClass = javaPsiFacade.findClass(SPRING_COMPONENT_ANNOTATION, allScope);
        if (springComponentBaseClass != null){
            matchedClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(springComponentBaseClass, projectScope).findAll());
        }

        PsiClass liteflowComponentBaseClass = javaPsiFacade.findClass(LITEFLOW_COMPONENT_ANNOTATION, allScope);
        if (liteflowComponentBaseClass != null){
            matchedClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(liteflowComponentBaseClass, projectScope).findAll());
        }

        if (CollectionUtils.isEmpty(matchedClasses)){
            return Collections.emptyList();
        }

        PsiClass nodeComponentBaseClass = javaPsiFacade.findClass(NODE_COMPONENT_CLASS, allScope);
        if (nodeComponentBaseClass == null) {
            LOG.warn("无法在项目中找到 NodeComponent 基类，某些扫描可能会受影响。");
        }

        for (PsiClass psiClass : matchedClasses) {
            if (project.isDisposed()) return Collections.emptyList();
            if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            // 条件1: 不直接或间接继承 com.yomahub.liteflow.core.NodeComponent
            if (nodeComponentBaseClass != null && psiClass.isInheritor(nodeComponentBaseClass, true)) {
                continue;
            }

            // 条件2: @LiteflowComponent或者@Component修饰，value即为nodeId
            String nodeId = getNodeIdFromComponentAnnotations(psiClass);
            if (nodeId == null) {
                nodeId = convertClassNameToCamelCase(psiClass.getName());
            }

            if (nodeId == null || nodeId.trim().isEmpty()) {
                continue;
            }

            // 条件3: 存在@LiteflowMethod标注，并且所有的@LiteflowMethod标注的nodeType得相同
            List<PsiAnnotation> liteflowMethodAnnotations = new ArrayList<>();
            for (PsiMethod method : psiClass.getMethods()) {
                PsiAnnotation lfMethodAnnotation = method.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
                if (lfMethodAnnotation != null) {
                    liteflowMethodAnnotations.add(lfMethodAnnotation);
                }
            }

            if (liteflowMethodAnnotations.isEmpty()) {
                continue;
            }

            Set<String> nodeTypeValues = new HashSet<>();
            String firstNodeTypeValue = null;

            for (PsiAnnotation annotation : liteflowMethodAnnotations) {
                String currentEnumConstantName = getAnnotationEnumValue(annotation, "nodeType", LITEFLOW_NODE_TYPE_ENUM_FQ);
                if (currentEnumConstantName == null) {
                    currentEnumConstantName = "COMMON"; // 注解未指定nodeType时的默认值
                }

                if (firstNodeTypeValue == null) {
                    firstNodeTypeValue = currentEnumConstantName;
                }
                nodeTypeValues.add(currentEnumConstantName);
            }

            if (nodeTypeValues.size() > 1) {
                LOG.warn("类 " + psiClass.getQualifiedName() + " 中的 @LiteflowMethod 注解包含不同的 nodeType 值: " + nodeTypeValues + "。作为类声明式组件跳过该类。");
                continue;
            }

            NodeType nodeType = NodeType.fromDeclarativeNodeType(firstNodeTypeValue);

            String nodeName = psiClass.getName();
            declarativeNodeInfos.add(new LiteFlowNodeInfo(nodeId, nodeName, nodeType, psiClass, "类声明式"));
        }

        return declarativeNodeInfos;
    }

    /**
     * [新增] 扫描方法级别的声明式组件。
     * 这种组件在一个类中通过不同的 @LiteflowMethod 定义多个Node。
     */
    private List<LiteFlowNodeInfo> findDeclarativeMethodNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> nodeInfos = new ArrayList<>();
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);

        // 步骤 1: 查找所有可能是组件容器的候选类
        Collection<PsiClass> candidateClasses = new ArrayList<>();
        PsiClass liteflowComponentAnnotation = javaPsiFacade.findClass(LITEFLOW_COMPONENT_ANNOTATION, allScope);
        if (liteflowComponentAnnotation != null) {
            candidateClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(liteflowComponentAnnotation, projectScope).findAll());
        }
        PsiClass springComponentAnnotation = javaPsiFacade.findClass(SPRING_COMPONENT_ANNOTATION, allScope);
        if (springComponentAnnotation != null) {
            candidateClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(springComponentAnnotation, projectScope).findAll());
        }

        if (candidateClasses.isEmpty()) {
            return Collections.emptyList();
        }

        // 步骤 2: 过滤和处理这些类
        PsiClass nodeComponentBaseClass = javaPsiFacade.findClass(NODE_COMPONENT_CLASS, allScope);
        if (nodeComponentBaseClass == null) {
            LOG.warn("无法在项目中找到 NodeComponent 基类，方法级组件扫描可能会受影响。");
        }

        for (PsiClass psiClass : candidateClasses) {
            if (project.isDisposed()) return Collections.emptyList();
            if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            // 条件1: 和类声明式组件一样，不直接或间接继承 NodeComponent
            if (nodeComponentBaseClass != null && psiClass.isInheritor(nodeComponentBaseClass, true)) {
                continue;
            }

            // 步骤 3: 遍历类中的每个方法
            for (PsiMethod method : psiClass.getMethods()) {
                PsiAnnotation lfMethodAnnotation = method.getAnnotation(LITEFLOW_METHOD_ANNOTATION);
                if (lfMethodAnnotation == null) {
                    continue;
                }

                // 条件3: 检查 @LiteflowMethod 的 `value` 属性是否为指定的流程类型
                String methodType = getAnnotationEnumValue(lfMethodAnnotation, "value", LITEFLOW_METHOD_ENUM_FQ);
                if (methodType == null || !PROCESS_METHOD_TYPES.contains(methodType)) {
                    continue;
                }

                // 条件3: 提取 nodeId，这是必须的
                String nodeId = getAnnotationAttributeValue(lfMethodAnnotation, "nodeId");
                if (nodeId == null || nodeId.trim().isEmpty()) {
                    LOG.warn("在 " + psiClass.getQualifiedName() + "." + method.getName() + " 中发现一个 @LiteflowMethod，但缺少 nodeId 属性。已跳过。");
                    continue;
                }

                // 提取可选的 nodeName 和 nodeType
                String nodeName = getAnnotationAttributeValue(lfMethodAnnotation, "nodeName");
                String nodeTypeStr = getAnnotationEnumValue(lfMethodAnnotation, "nodeType", LITEFLOW_NODE_TYPE_ENUM_FQ);
                if (nodeTypeStr == null) {
                    nodeTypeStr = "COMMON"; // 如果未指定，LiteFlow 默认为 COMMON
                }

                NodeType nodeType = NodeType.fromDeclarativeNodeType(nodeTypeStr);

                // 创建节点信息，关键是 PsiElement 指向 PsiMethod
                nodeInfos.add(new LiteFlowNodeInfo(nodeId, nodeName, nodeType, method, "方法声明式"));
            }
        }
        return nodeInfos;
    }


    /**
     * 根据Java类直接继承的LiteFlow基类全限定名来确定节点类型 (用于继承式组件)。
     */
    private NodeType determineNodeTypeFromSuperClass(PsiClass psiClass, String directSuperClassName) {
        if ("com.yomahub.liteflow.core.NodeComponent".equals(directSuperClassName)) return NodeType.COMMON_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeSwitchComponent".equals(directSuperClassName)) return NodeType.SWITCH_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeBooleanComponent".equals(directSuperClassName)) return NodeType.BOOLEAN_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeForComponent".equals(directSuperClassName)) return NodeType.FOR_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeIteratorComponent".equals(directSuperClassName)) return NodeType.ITERATOR_COMPONENT;
        return NodeType.UNKNOWN;
    }

    /**
     * 从 @LiteflowComponent 或 @Component 注解中提取节点ID。
     * 优先使用 @LiteflowComponent("value")，其次是 @Component("value")。
     * 如果注解的 value 未设置或为空字符串，则返回 null。
     */
    @Nullable
    private String getNodeIdFromComponentAnnotations(@NotNull PsiClass psiClass) {
        PsiAnnotation liteflowAnnotation = psiClass.getAnnotation(LITEFLOW_COMPONENT_ANNOTATION);
        if (liteflowAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(liteflowAnnotation, "value");
            if (nodeId != null && !nodeId.trim().isEmpty()) return nodeId;
            if (nodeId != null) return null; // 显式为空""或只有空格
            if (!hasExplicitValueAttribute(liteflowAnnotation)) return null; // 注解存在但没有value属性
        }

        PsiAnnotation springAnnotation = psiClass.getAnnotation(SPRING_COMPONENT_ANNOTATION);
        if (springAnnotation != null) {
            String nodeId = getAnnotationAttributeValue(springAnnotation, "value");
            if (nodeId != null && !nodeId.trim().isEmpty()) return nodeId;
            if (nodeId != null) return null;
            if (!hasExplicitValueAttribute(springAnnotation)) return null;
        }
        return null;
    }

    private boolean hasExplicitValueAttribute(PsiAnnotation annotation) {
        for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            if ("value".equals(pair.getName()) || pair.getName() == null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String convertClassNameToCamelCase(@Nullable String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.length() > 1 && Character.isUpperCase(className.charAt(0)) && Character.isLowerCase(className.charAt(1))) {
            return Character.toLowerCase(className.charAt(0)) + className.substring(1);
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    @Nullable
    private String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression) {
            Object literalValue = ((PsiLiteralExpression) value).getValue();
            return literalValue instanceof String ? (String) literalValue : null;
        }
        return null;
    }

    /**
     * [辅助方法] 解析注解中指定的枚举类型属性值。
     * @param annotation 要解析的注解
     * @param attributeName 属性名 (如 "value", "nodeType")
     * @param expectedEnumFqName 期望的枚举类的完全限定名
     * @return 枚举常量的名称 (String)，如果未找到或类型不匹配则返回 null
     */
    @Nullable
    private String getAnnotationEnumValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, @NotNull String expectedEnumFqName) {
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

    private List<LiteFlowNodeInfo> findXmlScriptNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> xmlNodeInfos = new ArrayList<>();
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : virtualFiles) {
            if (project.isDisposed()) break;
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof XmlFile) {
                XmlFile xmlFile = (XmlFile) psiFile;
                XmlTag flowRootTag = LiteFlowXmlUtil.getLiteFlowRootTag(xmlFile);
                if (flowRootTag != null) {
                    XmlTag nodesTag = LiteFlowXmlUtil.getNodesTag(flowRootTag);
                    if (nodesTag != null) {
                        for (XmlTag nodeTag : nodesTag.findSubTags("node")) {
                            if (project.isDisposed()) return Collections.emptyList();

                            String xmlId = nodeTag.getAttributeValue("id");
                            String xmlName = nodeTag.getAttributeValue("name");
                            String typeAttr = nodeTag.getAttributeValue("type");

                            String primaryDisplayId = xmlId;
                            String displayName = xmlName;

                            if ((primaryDisplayId == null || primaryDisplayId.trim().isEmpty()) && (displayName != null && !displayName.trim().isEmpty())) {
                                primaryDisplayId = displayName;
                            }

                            if (primaryDisplayId == null || primaryDisplayId.trim().isEmpty()){
                                LOG.warn("跳过XML节点，无法确定Node ID: " + nodeTag.getName() + " in " + xmlFile.getName());
                                continue;
                            }

                            NodeType nodeType = NodeType.fromXmlType(typeAttr);
                            xmlNodeInfos.add(new LiteFlowNodeInfo(primaryDisplayId, displayName, nodeType, nodeTag, "XML脚本"));
                        }
                    }
                }
            }
        }
        return xmlNodeInfos;
    }
}
