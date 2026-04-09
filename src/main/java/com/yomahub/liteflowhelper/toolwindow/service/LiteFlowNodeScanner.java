package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * 负责扫描 LiteFlow 节点定义。
 */
public class LiteFlowNodeScanner {
    private static final Logger LOG = Logger.getInstance(LiteFlowNodeScanner.class);

    private static final String NODE_SWITCH_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeSwitchComponent";
    private static final String NODE_BOOLEAN_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeBooleanComponent";
    private static final String NODE_FOR_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeForComponent";
    private static final String NODE_ITERATOR_COMPONENT_CLASS = "com.yomahub.liteflow.core.NodeIteratorComponent";

    public List<LiteFlowNodeInfo> findLiteFlowNodes(@NotNull Project project) {
        return findLiteFlowNodes(project, new LiteFlowXmlScanner().scan(project));
    }

    public List<LiteFlowNodeInfo> findLiteFlowNodes(@NotNull Project project, @NotNull LiteFlowXmlScanResult xmlScanResult) {
        return ScannerUtil.runInReadAction(project, "LiteFlow 节点", () -> {
            List<LiteFlowNodeInfo> nodeInfos = new ArrayList<>();

            List<LiteFlowNodeInfo> inheritanceNodes = findJavaClassInheritanceNodes(project);
            nodeInfos.addAll(inheritanceNodes);
            LOG.info("继承式组件: " + inheritanceNodes.size() + " 个");

            List<LiteFlowNodeInfo> xmlNodes = xmlScanResult.getXmlNodes();
            nodeInfos.addAll(xmlNodes);
            LOG.info("XML 脚本组件: " + xmlNodes.size() + " 个");

            List<LiteFlowNodeInfo> declarativeNodes = findDeclarativeNodes(project);
            nodeInfos.addAll(declarativeNodes);
            LOG.info("声明式组件: " + declarativeNodes.size() + " 个");

            nodeInfos.sort(Comparator.comparing(LiteFlowNodeInfo::getNodeId));
            return nodeInfos;
        });
    }

    private List<LiteFlowNodeInfo> findJavaClassInheritanceNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> javaNodeInfos = new ArrayList<>();
        Set<String> seenClasses = new HashSet<>();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
        NodeBaseClasses baseClasses = resolveNodeBaseClasses(psiFacade, allScope);
        if (baseClasses.nodeComponent() == null) {
            LOG.warn("未找到基类: " + LiteFlowXmlUtil.NODE_COMPONENT_CLASS);
            return Collections.emptyList();
        }

        Collection<PsiClass> inheritors =
                ClassInheritorsSearch.search(baseClasses.nodeComponent(), projectScope, true).findAll();
        LOG.debug("开始扫描继承式组件，NodeComponent 子类数量: " + inheritors.size());

        for (PsiClass inheritor : inheritors) {
            if (project.isDisposed()) {
                return Collections.emptyList();
            }

            if (!LiteFlowXmlUtil.isInheritanceComponent(inheritor, baseClasses.nodeComponent())) {
                continue;
            }

            String nodeIdFromAnnotation = LiteFlowXmlUtil.getNodeIdFromComponentAnnotations(inheritor);
            String nodeName = inheritor.getName();
            String primaryId = nodeIdFromAnnotation != null
                    ? nodeIdFromAnnotation
                    : LiteFlowXmlUtil.convertClassNameToCamelCase(nodeName);

            if (StringUtil.isEmpty(primaryId)) {
                LOG.warn("跳过继承式组件，无法确定 Node ID: " + inheritor.getQualifiedName());
                continue;
            }

            String dedupeKey = StringUtil.notNullize(inheritor.getQualifiedName(), nodeName);
            if (!seenClasses.add(dedupeKey)) {
                LOG.debug("跳过重复组件: " + dedupeKey);
                continue;
            }

            NodeType nodeType = determineNodeType(inheritor, baseClasses);
            javaNodeInfos.add(new LiteFlowNodeInfo(primaryId, nodeName, nodeType, inheritor, "继承式"));
        }

        LOG.debug("继承式组件扫描完成，共找到 " + javaNodeInfos.size() + " 个组件");
        return javaNodeInfos;
    }

    private Set<PsiClass> findCandidateComponentClasses(@NotNull Project project) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);

        Set<PsiClass> candidates = new LinkedHashSet<>();

        PsiClass liteflowMethodAnnotation = javaPsiFacade.findClass(LiteFlowXmlUtil.LITEFLOW_METHOD_ANNOTATION, allScope);
        if (liteflowMethodAnnotation != null) {
            Collection<PsiMethod> liteflowMethods =
                    AnnotatedElementsSearch.searchPsiMethods(liteflowMethodAnnotation, projectScope).findAll();
            for (PsiMethod method : liteflowMethods) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                    candidates.add(containingClass);
                }
            }
            LOG.debug("找到 " + liteflowMethods.size() + " 个 @LiteflowMethod 方法，候选类数量: " + candidates.size());
        } else {
            LOG.warn("未找到 @LiteflowMethod 注解类");
        }

        return candidates;
    }

    private List<LiteFlowNodeInfo> findDeclarativeNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> nodeInfos = new ArrayList<>();
        Set<PsiClass> candidateClasses = findCandidateComponentClasses(project);

        if (candidateClasses.isEmpty()) {
            LOG.warn("未找到任何带组件注解的类");
            return Collections.emptyList();
        }

        PsiClass nodeComponentBaseClass = JavaPsiFacade.getInstance(project)
                .findClass(LiteFlowXmlUtil.NODE_COMPONENT_CLASS, GlobalSearchScope.allScope(project));

        for (PsiClass psiClass : candidateClasses) {
            if (project.isDisposed()) {
                return Collections.emptyList();
            }

            if (psiClass.isInterface()
                    || psiClass.isAnnotationType()
                    || psiClass.isEnum()
                    || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            if (!LiteFlowXmlUtil.hasComponentAnnotation(psiClass)) {
                continue;
            }

            if (LiteFlowXmlUtil.isInheritanceComponent(psiClass, nodeComponentBaseClass)) {
                continue;
            }

            boolean isClassDeclarative = false;
            if (LiteFlowXmlUtil.isClassDeclarativeComponent(psiClass, nodeComponentBaseClass)) {
                String nodeId = LiteFlowXmlUtil.getNodeIdFromComponentAnnotations(psiClass);
                if (nodeId == null) {
                    nodeId = LiteFlowXmlUtil.convertClassNameToCamelCase(psiClass.getName());
                }

                if (!StringUtil.isEmpty(nodeId)) {
                    String nodeTypeStr = "COMMON";
                    for (PsiMethod method : psiClass.getMethods()) {
                        PsiAnnotation annotation = method.getAnnotation(LiteFlowXmlUtil.LITEFLOW_METHOD_ANNOTATION);
                        if (annotation != null) {
                            nodeTypeStr = LiteFlowXmlUtil.getAnnotationEnumValue(
                                    annotation,
                                    "nodeType",
                                    LiteFlowXmlUtil.LITEFLOW_NODE_TYPE_ENUM_FQ
                            );
                            if (nodeTypeStr == null) {
                                nodeTypeStr = "COMMON";
                            }
                            break;
                        }
                    }

                    NodeType nodeType = NodeType.fromDeclarativeNodeType(nodeTypeStr);
                    nodeInfos.add(new LiteFlowNodeInfo(nodeId, psiClass.getName(), nodeType, psiClass, "类声明式"));
                    isClassDeclarative = true;
                }
            }

            if (isClassDeclarative) {
                continue;
            }

            for (PsiMethod method : psiClass.getMethods()) {
                if (!LiteFlowXmlUtil.isMethodDeclarativeComponent(method)) {
                    continue;
                }

                PsiAnnotation lfMethodAnnotation = method.getAnnotation(LiteFlowXmlUtil.LITEFLOW_METHOD_ANNOTATION);
                String nodeId = LiteFlowXmlUtil.getAnnotationAttributeValue(lfMethodAnnotation, "nodeId");
                String nodeName = LiteFlowXmlUtil.getAnnotationAttributeValue(lfMethodAnnotation, "nodeName");
                String nodeTypeStr = LiteFlowXmlUtil.getAnnotationEnumValue(
                        lfMethodAnnotation,
                        "nodeType",
                        LiteFlowXmlUtil.LITEFLOW_NODE_TYPE_ENUM_FQ
                );
                if (nodeTypeStr == null) {
                    nodeTypeStr = "COMMON";
                }

                NodeType nodeType = NodeType.fromDeclarativeNodeType(nodeTypeStr);
                nodeInfos.add(new LiteFlowNodeInfo(nodeId, nodeName, nodeType, method, "方法声明式"));
            }
        }

        LOG.debug("声明式组件扫描完成，共找到 " + nodeInfos.size() + " 个组件");
        return nodeInfos;
    }

    private NodeType determineNodeType(@NotNull PsiClass psiClass, @NotNull NodeBaseClasses baseClasses) {
        if (baseClasses.switchComponent() != null && psiClass.isInheritor(baseClasses.switchComponent(), true)) {
            return NodeType.SWITCH_COMPONENT;
        }
        if (baseClasses.booleanComponent() != null && psiClass.isInheritor(baseClasses.booleanComponent(), true)) {
            return NodeType.BOOLEAN_COMPONENT;
        }
        if (baseClasses.forComponent() != null && psiClass.isInheritor(baseClasses.forComponent(), true)) {
            return NodeType.FOR_COMPONENT;
        }
        if (baseClasses.iteratorComponent() != null && psiClass.isInheritor(baseClasses.iteratorComponent(), true)) {
            return NodeType.ITERATOR_COMPONENT;
        }
        return NodeType.COMMON_COMPONENT;
    }

    private NodeBaseClasses resolveNodeBaseClasses(@NotNull JavaPsiFacade psiFacade, @NotNull GlobalSearchScope scope) {
        return new NodeBaseClasses(
                psiFacade.findClass(LiteFlowXmlUtil.NODE_COMPONENT_CLASS, scope),
                psiFacade.findClass(NODE_SWITCH_COMPONENT_CLASS, scope),
                psiFacade.findClass(NODE_BOOLEAN_COMPONENT_CLASS, scope),
                psiFacade.findClass(NODE_FOR_COMPONENT_CLASS, scope),
                psiFacade.findClass(NODE_ITERATOR_COMPONENT_CLASS, scope)
        );
    }

    private record NodeBaseClasses(
            PsiClass nodeComponent,
            PsiClass switchComponent,
            PsiClass booleanComponent,
            PsiClass forComponent,
            PsiClass iteratorComponent
    ) {
    }
}
