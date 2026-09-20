package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.yomahub.liteflowhelper.highlight.LiteFlowChainAnnotator;
import com.yomahub.liteflowhelper.highlight.LiteFlowElValidationAnnotator;
import com.yomahub.liteflowhelper.highlight.LiteFlowHighlightColorSettings;
import com.yomahub.liteflowhelper.icon.LiteFlowComponentIconProvider;
import com.yomahub.liteflowhelper.inspection.LiteFlowMissingComponentInspection;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.service.LiteFlowElementResolver;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeCategory;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.Icon;

public class LiteFlowNodeScannerTest extends BasePlatformTestCase {
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addJava("java.lang.Object", "public class Object {}");
        addJava("java.lang.String", "public final class String {}");
        addJava("org.springframework.context.annotation.Bean", """
                public @interface Bean { String[] value() default {}; String[] name() default {}; }
                """);
        addJava("org.springframework.context.annotation.Configuration", "public @interface Configuration {}");
        addJava("org.springframework.stereotype.Component", "public @interface Component { String value() default \"\"; }");
        addJava("com.yomahub.liteflow.annotation.LiteflowComponent", """
                public @interface LiteflowComponent { String value() default ""; String id() default ""; }
                """);
        addJava("com.yomahub.liteflow.core.NodeComponent", "public abstract class NodeComponent { public abstract void process(); }");
        for (String type : List.of("Switch", "Boolean", "For", "Iterator")) {
            addJava("com.yomahub.liteflow.core.Node" + type + "Component",
                    "public abstract class Node" + type + "Component extends NodeComponent {}");
        }
        addJava("com.yomahub.liteflow.enums.NodeTypeEnum", "public enum NodeTypeEnum { COMMON, SWITCH, BOOLEAN, FOR, ITERATOR }");
        addJava("com.yomahub.liteflow.enums.LiteFlowMethodEnum", "public enum LiteFlowMethodEnum { PROCESS, PROCESS_SWITCH, BEFORE_PROCESS }");
        addJava("com.yomahub.liteflow.annotation.LiteflowMethod", """
                import com.yomahub.liteflow.enums.*;
                public @interface LiteflowMethod {
                    LiteFlowMethodEnum value();
                    String nodeId() default "";
                    String nodeName() default "";
                    NodeTypeEnum nodeType() default NodeTypeEnum.COMMON;
                }
                """);
    }

    public void testCustomDecisionBeanTargetsResolveFromEl() {
        addJava("example.CustomDecisionRouter", """
                import com.yomahub.liteflow.annotation.LiteflowComponent;
                import com.yomahub.liteflow.core.NodeSwitchComponent;
                @LiteflowComponent("customDecisionRouter")
                public class CustomDecisionRouter extends NodeSwitchComponent { public void process() {} }
                """);
        StringBuilder methods = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            methods.append("@Bean(\"customOption").append(i).append("\") public NodeComponent option")
                    .append(i).append("() { return new SelectedOption(); }\n");
        }
        addJava("example.CustomDecisionTargets", """
                import org.springframework.context.annotation.*;
                import com.yomahub.liteflow.core.NodeComponent;
                @Configuration
                public class CustomDecisionTargets {
                """ + methods + """
                    @Bean("customUndecided") public NodeComponent undecided() {
                        return new NodeComponent() { public void process() {} };
                    }
                    public static class SelectedOption extends NodeComponent { public void process() {} }
                }
                """);
        PsiFile flow = myFixture.addFileToProject("flow.el.xml", """
                <flow><chain name="customDecision">
                    SWITCH(customDecisionRouter)
                        .to(customOption1, customOption2, customOption3, customOption4,
                            customOption5, customOption6, customOption7, customOption8)
                        .DEFAULT(customUndecided);
                </chain></flow>
                """);
        Map<String, LiteFlowNodeInfo> nodes = scan();
        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(getProject(), flow);
        for (int i = 1; i <= 8; i++) {
            assertBeanNode(nodes, "customOption" + i, "option" + i, NodeType.COMMON_COMPONENT);
            assertTrue(resolver.isNode("customOption" + i));
            assertSame(nodes.get("customOption" + i).getPsiElement(), resolver.resolveNode("customOption" + i));
        }
        assertBeanNode(nodes, "customUndecided", "undecided", NodeType.COMMON_COMPONENT);
        assertTrue(resolver.isNode("customUndecided"));
        assertSame(nodes.get("customUndecided").getPsiElement(), resolver.resolveNode("customUndecided"));
        assertFalse(resolver.isNode("missingTarget"));

        myFixture.enableInspections(new LiteFlowMissingComponentInspection());
        myFixture.configureFromExistingVirtualFile(flow.getVirtualFile());
        assertEmpty(missingComponentHighlights());
        // A genuinely missing node must still be reported by the same inspection.
        myFixture.configureByText(XmlFileType.INSTANCE,
                "<flow><chain name=\"missing\">THEN(missingTarget);</chain></flow>");
        List<HighlightInfo> missing = missingComponentHighlights();
        assertSize(1, missing);
        assertEquals("LiteFlow component 'missingTarget' not found", missing.get(0).getDescription());
    }

    public void testBeanNamesUsePrimaryNameOrMethodName() {
        addJava("example.NamedBeans", """
                import org.springframework.context.annotation.*;
                import com.yomahub.liteflow.core.NodeComponent;
                @Configuration public class NamedBeans {
                    static final String PREFIX = "constant";
                    @Bean public NodeComponent defaultNode() { return null; }
                    @Bean(value = {}) public NodeComponent emptyNames() { return null; }
                    @Bean(name = "namedNode") public NodeComponent namedFactory() { return null; }
                    @Bean({"primaryValue", "valueAlias"}) public NodeComponent valueFactory() { return null; }
                    @Bean(name = {"primaryName", "nameAlias"}) public NodeComponent nameFactory() { return null; }
                    @Bean(PREFIX + "Node") public NodeComponent constantFactory() { return null; }
                    @Bean(UNKNOWN_NAME) public NodeComponent unresolvedFactory() { return null; }
                }
                """);
        Map<String, LiteFlowNodeInfo> nodes = scan();
        assertBeanNode(nodes, "defaultNode", "defaultNode", NodeType.COMMON_COMPONENT);
        assertBeanNode(nodes, "emptyNames", "emptyNames", NodeType.COMMON_COMPONENT);
        assertBeanNode(nodes, "namedNode", "namedFactory", NodeType.COMMON_COMPONENT);
        assertBeanNode(nodes, "primaryValue", "valueFactory", NodeType.COMMON_COMPONENT);
        assertBeanNode(nodes, "primaryName", "nameFactory", NodeType.COMMON_COMPONENT);
        assertBeanNode(nodes, "constantNode", "constantFactory", NodeType.COMMON_COMPONENT);
        // LiteFlow registers the primary bean name, not Spring aliases or named factory methods.
        for (String id : List.of("valueAlias", "nameAlias", "namedFactory", "valueFactory", "nameFactory", "constantFactory", "unresolvedFactory")) {
            assertFalse(id, nodes.containsKey(id));
        }
    }

    public void testBeanReturnTypesAndNonComponentExclusion() {
        addJava("example.TypedBeans", """
                import org.springframework.context.annotation.*;
                import com.yomahub.liteflow.core.*;
                @Configuration public class TypedBeans {
                    @Bean public NodeSwitchComponent switchNode() { return null; }
                    @Bean public NodeBooleanComponent booleanNode() { return null; }
                    @Bean public NodeForComponent forNode() { return null; }
                    @Bean public NodeIteratorComponent iteratorNode() { return null; }
                    @Bean public CustomSwitch customSwitchNode() { return null; }
                    @Bean public String unrelatedBean() { return null; }
                    @Bean public Object unknownBean() { return null; }
                    @Bean public int primitiveBean() { return 0; }
                    public NodeComponent notABean() { return null; }
                    public static abstract class CustomSwitch extends NodeSwitchComponent {}
                }
                """);
        Map<String, LiteFlowNodeInfo> nodes = scan();
        assertBeanNode(nodes, "switchNode", "switchNode", NodeType.SWITCH_COMPONENT);
        assertBeanNode(nodes, "booleanNode", "booleanNode", NodeType.BOOLEAN_COMPONENT);
        assertBeanNode(nodes, "forNode", "forNode", NodeType.FOR_COMPONENT);
        assertBeanNode(nodes, "iteratorNode", "iteratorNode", NodeType.ITERATOR_COMPONENT);
        assertBeanNode(nodes, "customSwitchNode", "customSwitchNode", NodeType.SWITCH_COMPONENT);
        for (String id : List.of("unrelatedBean", "unknownBean", "primitiveBean", "notABean")) {
            assertFalse(id, nodes.containsKey(id));
        }
    }

    public void testExistingComponentSourcesAndDuplicateIdsKeepTheirResolution() {
        addJava("example.LegacyNode", """
                import com.yomahub.liteflow.annotation.LiteflowComponent;
                import com.yomahub.liteflow.core.NodeComponent;
                @LiteflowComponent("legacyNode") public class LegacyNode extends NodeComponent { public void process() {} }
                """);
        addJava("example.DefaultNode", """
                import org.springframework.stereotype.Component;
                import com.yomahub.liteflow.core.NodeComponent;
                @Component public class DefaultNode extends NodeComponent { public void process() {} }
                """);
        addJava("example.DeclaredClass", """
                import com.yomahub.liteflow.annotation.*;
                import com.yomahub.liteflow.enums.*;
                @LiteflowComponent("declaredClass") public class DeclaredClass {
                    @LiteflowMethod(LiteFlowMethodEnum.PROCESS) public void run() {}
                }
                """);
        addJava("example.DeclaredMethods", """
                import org.springframework.stereotype.Component;
                import com.yomahub.liteflow.annotation.*;
                import com.yomahub.liteflow.enums.*;
                @Component public class DeclaredMethods {
                    @LiteflowMethod(value = LiteFlowMethodEnum.PROCESS_SWITCH, nodeId = "declaredMethod", nodeType = NodeTypeEnum.SWITCH)
                    public String run() { return "legacyNode"; }
                }
                """);
        PsiFile flow = myFixture.addFileToProject("legacy.el.xml", """
                <flow><nodes><node id="scriptNode" type="script">return null;</node></nodes>
                    <chain name="legacyChain">THEN(legacyNode, defaultNode, declaredClass, scriptNode);</chain>
                </flow>
                """);
        Map<String, LiteFlowNodeInfo> before = scan();
        assertEquals(NodeType.COMMON_COMPONENT, before.get("legacyNode").getType());
        assertEquals(NodeType.COMMON_COMPONENT, before.get("defaultNode").getType());
        assertEquals(NodeType.DECLARATIVE_COMMON, before.get("declaredClass").getType());
        assertEquals(NodeType.DECLARATIVE_SWITCH, before.get("declaredMethod").getType());
        assertEquals(NodeType.SCRIPT_COMMON, before.get("scriptNode").getType());
        addJava("example.AdditionalBeans", """
                import org.springframework.context.annotation.*;
                import com.yomahub.liteflow.core.*;
                @Configuration public class AdditionalBeans {
                    @Bean("legacyNode") public NodeSwitchComponent duplicate() { return null; }
                    @Bean("extraNode") public NodeComponent extra() { return null; }
                }
                """);
        Map<String, LiteFlowNodeInfo> after = scan();
        for (String id : before.keySet()) {
            assertEquals(id, before.get(id).getType(), after.get(id).getType());
            assertEquals(id, before.get(id).getPsiElement(), after.get(id).getPsiElement());
        }
        assertInstanceOf(after.get("legacyNode").getPsiElement(), PsiClass.class);
        assertBeanNode(after, "extraNode", "extra", NodeType.COMMON_COMPONENT);
        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(getProject(), flow);
        assertTrue(resolver.isChain("legacyChain"));
        assertTrue(resolver.isNode("extraNode"));
    }

    public void testAiInheritanceAndBeanTypesKeepCategoriesAndIcons() {
        addAiComponents();
        Map<String, LiteFlowNodeInfo> nodes = scan();
        for (String id : List.of("writer", "remoteWriter", "beanAgent")) {
            assertEquals(id, NodeType.AI_AGENT_COMPONENT, nodes.get(id).getType());
            assertEquals(id, NodeCategory.COMMON, nodes.get(id).getType().toCategory());
        }
        for (String id : List.of("router", "beanRouter")) {
            assertEquals(id, NodeType.JEV_SWITCH_COMPONENT, nodes.get(id).getType());
            assertEquals(id, NodeCategory.SWITCH, nodes.get(id).getType().toCategory());
        }
        assertBeanNode(nodes, "beanAgent", "agentFactory", NodeType.AI_AGENT_COMPONENT);
        assertBeanNode(nodes, "beanRouter", "routerFactory", NodeType.JEV_SWITCH_COMPONENT);
        assertBeanNode(nodes, "customOption1", "optionFactory", NodeType.COMMON_COMPONENT);
        assertEquals(NodeType.COMMON_COMPONENT, nodes.get("plainAgent").getType());

        LiteFlowComponentIconProvider icons = new LiteFlowComponentIconProvider();
        assertSame(NodeType.AI_AGENT_COMPONENT.getIcon(), icons.getIcon(nodes.get("writer").getPsiElement(), 0));
        assertSame(NodeType.JEV_SWITCH_COMPONENT.getIcon(), icons.getIcon(nodes.get("router").getPsiElement(), 0));
        assertNotSame(icons.getIcon(nodes.get("plainAgent").getPsiElement(), 0),
                icons.getIcon(nodes.get("writer").getPsiElement(), 0));
        for (String path : List.of("/icons/ai.svg", "/icons/ai_dark.svg")) {
            Icon icon = IconLoader.getIcon(path, NodeType.class);
            assertEquals(14, icon.getIconWidth());
            assertEquals(14, icon.getIconHeight());
        }
    }

    public void testAiHighlightsKeepOrdinaryNodesAndSubVariablesDistinct() {
        addAiComponents();
        scan();
        PsiFile flow = myFixture.addFileToProject("ai.el.xml", """
                <flow>
                    <chain name="main">
                        /* AI routing */
                        THEN(writer, SWITCH(router).to(customOption1).DEFAULT(plainAgent), other, missingNode);
                    </chain>
                    <chain name="other">THEN(plainAgent);</chain>
                    <chain name="local">writer = THEN(plainAgent); THEN(writer);</chain>
                    <chain name="routed"><route>THEN(beanAgent);</route><body>THEN(plainAgent);</body></chain>
                </flow>
                """);
        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(getProject(), flow);
        assertTrue(resolver.isAiNode("writer"));
        assertTrue(resolver.isAiNode("router"));
        assertFalse(resolver.isAiNode("plainAgent"));
        assertFalse(resolver.isAiNode("customOption1"));
        assertFalse(resolver.isAiNode("missingNode"));
        AnnotationHolderImpl highlights = annotate(flow, new LiteFlowChainAnnotator());
        assertHighlight(flow, highlights, "router", LiteFlowHighlightColorSettings.AI_COMPONENT_KEY);
        assertHighlight(flow, highlights, "beanAgent", LiteFlowHighlightColorSettings.AI_COMPONENT_KEY);
        assertHighlight(flow, highlights, "customOption1", LiteFlowHighlightColorSettings.COMPONENT_KEY);
        assertHighlight(flow, highlights, "plainAgent", LiteFlowHighlightColorSettings.COMPONENT_KEY);
        assertHighlight(flow, highlights, "SWITCH", LiteFlowHighlightColorSettings.EL_KEYWORD_KEY);
        assertHighlight(flow, highlights, "other", LiteFlowHighlightColorSettings.CHAIN_KEY);
        assertHighlight(flow, highlights, "missingNode", LiteFlowHighlightColorSettings.UNKNOWN_COMPONENT_KEY);
        assertEquals(1L, highlights.stream().filter(a -> a.getTextAttributes() == LiteFlowHighlightColorSettings.EL_COMMENT_KEY).count());
        List<TextAttributesKey> writerColors = highlights.stream()
                .filter(a -> "writer".equals(flow.getText().substring(a.getStartOffset(), a.getEndOffset())))
                .map(a -> a.getTextAttributes()).collect(Collectors.toList());
        assertEquals(List.of(LiteFlowHighlightColorSettings.AI_COMPONENT_KEY,
                LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY, LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY), writerColors);

        PsiFile shadow = myFixture.addFileToProject("shadow.el.xml", """
                <flow><nodes><node id="writer" type="script">return null;</node></nodes>
                    <chain name="shadow">THEN(writer);</chain>
                </flow>
                """);
        assertFalse(LiteFlowElementResolver.create(getProject(), shadow).isAiNode("writer"));
        assertHighlight(shadow, annotate(shadow, new LiteFlowChainAnnotator()), "writer", LiteFlowHighlightColorSettings.COMPONENT_KEY);
    }

    public void testJevStillValidatesAsSwitchAndAgentAsCommon() {
        addAiComponents();
        scan();
        PsiFile valid = myFixture.addFileToProject("valid.el.xml", """
                <flow><chain name="valid">THEN(writer, SWITCH(router).to(customOption1).DEFAULT(plainAgent));</chain></flow>
                """);
        assertEmpty(annotate(valid, new LiteFlowElValidationAnnotator()));
        PsiFile invalid = myFixture.addFileToProject("invalid.el.xml", """
                <flow><chain name="invalid">SWITCH(writer).to(customOption1);</chain></flow>
                """);
        assertFalse(annotate(invalid, new LiteFlowElValidationAnnotator()).isEmpty());
    }

    public void testActualAgentExampleSourcesUseAiStyles() {
        addAiBaseClasses();
        // Snapshots from liteflow-agent-example: HarnessAgentComponent and JevSwitchComponent subclasses.
        myFixture.copyDirectoryToProject("aiExamples", "");
        Map<String, LiteFlowNodeInfo> nodes = scan();
        assertEquals(NodeType.AI_AGENT_COMPONENT, nodes.get("skillStreamAgent").getType());
        assertEquals(NodeType.JEV_SWITCH_COMPONENT, nodes.get("supportRouter").getType());
        assertEquals(NodeType.JEV_SWITCH_COMPONENT, nodes.get("customDecisionRouter").getType());
        assertEquals(3L, nodes.values().stream().filter(n -> n.getType().isAiComponent()).count());
        for (String id : List.of("prepare", "recordReply", "refund", "manual", "customOption1", "customUndecided")) {
            assertEquals(id, NodeType.COMMON_COMPONENT, nodes.get(id).getType());
        }
        myFixture.enableInspections(new LiteFlowMissingComponentInspection());
        for (String filename : List.of("agent.el.xml", "jev.el.xml")) {
            myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(filename));
            PsiFile flow = myFixture.getFile();
            assertEmpty(missingComponentHighlights());
            AnnotationHolderImpl highlights = annotate(flow, new LiteFlowChainAnnotator());
            List<String> aiIds = filename.equals("agent.el.xml")
                    ? List.of("skillStreamAgent") : List.of("supportRouter", "customDecisionRouter");
            for (String id : aiIds) {
                assertHighlight(flow, highlights, id, LiteFlowHighlightColorSettings.AI_COMPONENT_KEY);
            }
        }
    }

    private void addAiBaseClasses() {
        addJava("com.yomahub.liteflow.agent.component.AbstractAgentComponent", """
                import com.yomahub.liteflow.core.NodeComponent;
                public abstract class AbstractAgentComponent extends NodeComponent {}
                """);
        addJava("com.yomahub.liteflow.agent.component.AbstractAgentScopeComponent",
                "public abstract class AbstractAgentScopeComponent extends AbstractAgentComponent {}");
        addJava("com.yomahub.liteflow.agent.harness.component.HarnessAgentComponent", """
                import com.yomahub.liteflow.agent.component.AbstractAgentScopeComponent;
                public abstract class HarnessAgentComponent extends AbstractAgentScopeComponent {}
                """);
        addJava("com.yomahub.liteflow.agent.a2a.A2aAgentComponent", """
                import com.yomahub.liteflow.agent.component.AbstractAgentComponent;
                public abstract class A2aAgentComponent extends AbstractAgentComponent {}
                """);
        addJava("com.yomahub.liteflow.agent.jev.JevSwitchComponent", """
                import com.yomahub.liteflow.core.NodeSwitchComponent;
                public abstract class JevSwitchComponent extends NodeSwitchComponent {}
                """);
    }

    private void addAiComponents() {
        addAiBaseClasses();
        for (String[] definition : List.of(
                new String[]{"Writer", "writer", "com.yomahub.liteflow.agent.harness.component.HarnessAgentComponent"},
                new String[]{"RemoteWriter", "remoteWriter", "com.yomahub.liteflow.agent.a2a.A2aAgentComponent"},
                new String[]{"Router", "router", "com.yomahub.liteflow.agent.jev.JevSwitchComponent"},
                new String[]{"PlainAgent", "plainAgent", "com.yomahub.liteflow.core.NodeComponent"})) {
            addJava("example." + definition[0], "import com.yomahub.liteflow.annotation.LiteflowComponent;\n"
                    + "@LiteflowComponent(\"" + definition[1] + "\") public class " + definition[0]
                    + " extends " + definition[2] + " { public void process() {} }");
        }
        addJava("example.AiBeans", """
                import org.springframework.context.annotation.*;
                import com.yomahub.liteflow.agent.harness.component.HarnessAgentComponent;
                import com.yomahub.liteflow.agent.jev.JevSwitchComponent;
                import com.yomahub.liteflow.core.NodeComponent;
                @Configuration public class AiBeans {
                    @Bean("beanAgent") public HarnessAgentComponent agentFactory() { return null; }
                    @Bean("beanRouter") public JevSwitchComponent routerFactory() { return null; }
                    @Bean("customOption1") public NodeComponent optionFactory() { return null; }
                }
                """);
    }

    private AnnotationHolderImpl annotate(PsiFile file, Annotator annotator) {
        AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(file), false);
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
            holder.runAnnotatorWithContext(tag, annotator);
        }
        holder.assertAllAnnotationsCreated();
        return holder;
    }

    private void assertHighlight(PsiFile file, AnnotationHolderImpl highlights, String text, TextAttributesKey key) {
        List<TextAttributesKey> keys = highlights.stream()
                .filter(a -> text.equals(file.getText().substring(a.getStartOffset(), a.getEndOffset())))
                .map(a -> a.getTextAttributes()).collect(Collectors.toList());
        assertFalse("No highlight for " + text, keys.isEmpty());
        for (TextAttributesKey actual : keys) {
            assertSame(text, key, actual);
        }
    }

    private void addJava(String qualifiedName, String body) {
        int dot = qualifiedName.lastIndexOf('.');
        myFixture.addFileToProject(qualifiedName.replace('.', '/') + ".java",
                "package " + qualifiedName.substring(0, dot) + ";\n" + body);
    }

    private List<HighlightInfo> missingComponentHighlights() {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("LiteFlow component '"))
                .collect(Collectors.toList());
    }

    private Map<String, LiteFlowNodeInfo> scan() {
        List<LiteFlowNodeInfo> nodes = new LiteFlowNodeScanner().findLiteFlowNodes(getProject());
        LiteFlowCacheService.getInstance(getProject()).updateCache(List.of(), nodes);
        return nodes.stream().collect(Collectors.toMap(LiteFlowNodeInfo::getNodeId, Function.identity()));
    }

    private void assertBeanNode(Map<String, LiteFlowNodeInfo> nodes, String id, String methodName, NodeType type) {
        LiteFlowNodeInfo node = nodes.get(id);
        assertNotNull("Missing node: " + id, node);
        assertEquals(type, node.getType());
        assertInstanceOf(node.getPsiElement(), PsiMethod.class);
        assertEquals(methodName, ((PsiMethod) node.getPsiElement()).getName());
    }
}
