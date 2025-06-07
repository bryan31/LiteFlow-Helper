package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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

import java.util.*;

/**
 * 负责扫描项目中的 LiteFlow 节点定义。
 * 包括基于Java类的继承式节点、XML中定义的脚本节点以及声明式注解节点。
 * [重构] 此类现在将具体的判断逻辑委托给 LiteFlowXmlUtil。
 */
public class LiteFlowNodeScanner {

    private static final Logger LOG = Logger.getInstance(LiteFlowNodeScanner.class);

    // LiteFlow核心节点组件的基类，用于查找它们的子类 (继承式)
    private static final String[] LITEFLOW_BASE_CLASSES = {
            LiteFlowXmlUtil.NODE_COMPONENT_CLASS,
            "com.yomahub.liteflow.core.NodeSwitchComponent",
            "com.yomahub.liteflow.core.NodeBooleanComponent",
            "com.yomahub.liteflow.core.NodeForComponent",
            "com.yomahub.liteflow.core.NodeIteratorComponent"
    };

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
            nodeInfos.addAll(findDeclarativeClassNodes(project));
            nodeInfos.addAll(findDeclarativeMethodNodes(project));
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

                    // [重构] 使用工具类进行判断
                    if (LiteFlowXmlUtil.isInheritanceComponent(inheritor)) {
                        String nodeIdFromAnnotation = LiteFlowXmlUtil.getNodeIdFromComponentAnnotations(inheritor);
                        String nodeName = inheritor.getName();
                        String primaryId = nodeIdFromAnnotation != null ? nodeIdFromAnnotation : LiteFlowXmlUtil.convertClassNameToCamelCase(nodeName);

                        if (primaryId == null || primaryId.trim().isEmpty()) {
                            LOG.warn("跳过继承式组件，无法确定Node ID: " + inheritor.getQualifiedName());
                            continue;
                        }

                        NodeType nodeType = determineNodeTypeFromSuperClass(baseClassName);
                        javaNodeInfos.add(new LiteFlowNodeInfo(primaryId, nodeName, nodeType, inheritor, "继承式"));
                    }
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

        // 查找所有被 @LiteflowComponent 或 @Component 标注的类
        Set<PsiClass> matchedClasses = new HashSet<>();
        PsiClass liteflowComponentAnnotation = javaPsiFacade.findClass(LiteFlowXmlUtil.LITEFLOW_COMPONENT_ANNOTATION, allScope);
        if (liteflowComponentAnnotation != null){
            matchedClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(liteflowComponentAnnotation, projectScope).findAll());
        }
        PsiClass springComponentAnnotation = javaPsiFacade.findClass(LiteFlowXmlUtil.SPRING_COMPONENT_ANNOTATION, allScope);
        if (springComponentAnnotation != null){
            matchedClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(springComponentAnnotation, projectScope).findAll());
        }

        if (CollectionUtils.isEmpty(matchedClasses)){
            return Collections.emptyList();
        }

        for (PsiClass psiClass : matchedClasses) {
            if (project.isDisposed()) return Collections.emptyList();

            // [重构] 使用工具类进行判断
            if (LiteFlowXmlUtil.isClassDeclarativeComponent(psiClass)) {
                String nodeId = LiteFlowXmlUtil.getNodeIdFromComponentAnnotations(psiClass);
                if (nodeId == null) {
                    nodeId = LiteFlowXmlUtil.convertClassNameToCamelCase(psiClass.getName());
                }

                if (nodeId == null || nodeId.trim().isEmpty()) {
                    continue;
                }

                // 因为 isClassDeclarativeComponent 已确保所有 @LiteflowMethod 的 nodeType 相同，我们可以安全地取第一个
                String nodeTypeStr = "COMMON"; // 默认值
                for(PsiMethod method : psiClass.getMethods()){
                    PsiAnnotation annotation = method.getAnnotation(LiteFlowXmlUtil.LITEFLOW_METHOD_ANNOTATION);
                    if(annotation != null){
                        nodeTypeStr = LiteFlowXmlUtil.getAnnotationEnumValue(annotation, "nodeType", LiteFlowXmlUtil.LITEFLOW_NODE_TYPE_ENUM_FQ);
                        if(nodeTypeStr == null) nodeTypeStr = "COMMON";
                        break; // 找到第一个就够了
                    }
                }

                NodeType nodeType = NodeType.fromDeclarativeNodeType(nodeTypeStr);
                String nodeName = psiClass.getName();
                declarativeNodeInfos.add(new LiteFlowNodeInfo(nodeId, nodeName, nodeType, psiClass, "类声明式"));
            }
        }

        return declarativeNodeInfos;
    }

    /**
     * 扫描方法级别的声明式组件。
     */
    private List<LiteFlowNodeInfo> findDeclarativeMethodNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> nodeInfos = new ArrayList<>();
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);

        // 查找所有可能是组件容器的候选类
        Set<PsiClass> candidateClasses = new HashSet<>();
        PsiClass liteflowComponentAnnotation = javaPsiFacade.findClass(LiteFlowXmlUtil.LITEFLOW_COMPONENT_ANNOTATION, allScope);
        if (liteflowComponentAnnotation != null) {
            candidateClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(liteflowComponentAnnotation, projectScope).findAll());
        }
        PsiClass springComponentAnnotation = javaPsiFacade.findClass(LiteFlowXmlUtil.SPRING_COMPONENT_ANNOTATION, allScope);
        if (springComponentAnnotation != null) {
            candidateClasses.addAll(AnnotatedElementsSearch.searchPsiClasses(springComponentAnnotation, projectScope).findAll());
        }

        if (candidateClasses.isEmpty()) {
            return Collections.emptyList();
        }

        for (PsiClass psiClass : candidateClasses) {
            if (project.isDisposed()) return Collections.emptyList();

            // 检查是否为合法的声明式组件容器 (非接口、非抽象、非继承式)
            if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
                    || LiteFlowXmlUtil.isInheritanceComponent(psiClass)) {
                continue;
            }

            for (PsiMethod method : psiClass.getMethods()) {
                // [重构] 使用工具类进行判断
                if (LiteFlowXmlUtil.isMethodDeclarativeComponent(method)) {
                    PsiAnnotation lfMethodAnnotation = method.getAnnotation(LiteFlowXmlUtil.LITEFLOW_METHOD_ANNOTATION);
                    String nodeId = LiteFlowXmlUtil.getAnnotationAttributeValue(lfMethodAnnotation, "nodeId");
                    String nodeName = LiteFlowXmlUtil.getAnnotationAttributeValue(lfMethodAnnotation, "nodeName");
                    String nodeTypeStr = LiteFlowXmlUtil.getAnnotationEnumValue(lfMethodAnnotation, "nodeType", LiteFlowXmlUtil.LITEFLOW_NODE_TYPE_ENUM_FQ);
                    if (nodeTypeStr == null) {
                        nodeTypeStr = "COMMON";
                    }

                    NodeType nodeType = NodeType.fromDeclarativeNodeType(nodeTypeStr);
                    nodeInfos.add(new LiteFlowNodeInfo(nodeId, nodeName, nodeType, method, "方法声明式"));
                }
            }
        }
        return nodeInfos;
    }


    /**
     * 根据Java类直接继承的LiteFlow基类全限定名来确定节点类型 (用于继承式组件)。
     */
    private NodeType determineNodeTypeFromSuperClass(String directSuperClassName) {
        if ("com.yomahub.liteflow.core.NodeComponent".equals(directSuperClassName)) return NodeType.COMMON_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeSwitchComponent".equals(directSuperClassName)) return NodeType.SWITCH_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeBooleanComponent".equals(directSuperClassName)) return NodeType.BOOLEAN_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeForComponent".equals(directSuperClassName)) return NodeType.FOR_COMPONENT;
        if ("com.yomahub.liteflow.core.NodeIteratorComponent".equals(directSuperClassName)) return NodeType.ITERATOR_COMPONENT;
        return NodeType.UNKNOWN;
    }

    /**
     * 查找XML中定义的脚本节点。
     */
    private List<LiteFlowNodeInfo> findXmlScriptNodes(@NotNull Project project) {
        List<LiteFlowNodeInfo> xmlNodeInfos = new ArrayList<>();
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile virtualFile : virtualFiles) {
            if (project.isDisposed()) break;
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof XmlFile) {
                XmlFile xmlFile = (XmlFile) psiFile;
                // [核心修改] 先使用 isLiteFlowXml 进行判断
                if (LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
                    // 判断通过后，可以安全地获取根标签进行处理
                    XmlTag flowRootTag = xmlFile.getDocument().getRootTag();
                    if (flowRootTag == null) continue; // 添加一个防御性检查

                    XmlTag nodesTag = LiteFlowXmlUtil.getNodesTag(flowRootTag);
                    if (nodesTag != null) {
                        for (XmlTag nodeTag : nodesTag.findSubTags("node")) {
                            if (project.isDisposed()) return Collections.emptyList();

                            String xmlId = nodeTag.getAttributeValue("id");
                            String xmlName = nodeTag.getAttributeValue("name");
                            String typeAttr = nodeTag.getAttributeValue("type");

                            if (xmlId == null || xmlId.trim().isEmpty()) {
                                LOG.warn("跳过XML节点，缺少'id'属性: " + nodeTag.getName() + " in " + xmlFile.getName());
                                continue;
                            }

                            NodeType nodeType = NodeType.fromXmlType(typeAttr);
                            xmlNodeInfos.add(new LiteFlowNodeInfo(xmlId, xmlName, nodeType, nodeTag, "XML脚本"));
                        }
                    }
                }
            }
        }
        return xmlNodeInfos;
    }
}
