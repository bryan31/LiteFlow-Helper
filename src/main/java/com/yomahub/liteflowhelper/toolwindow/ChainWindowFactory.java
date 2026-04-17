package com.yomahub.liteflowhelper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.service.LiteFlowRefreshStateService;
import com.yomahub.liteflowhelper.service.LiteFlowScanCoordinator;
import com.yomahub.liteflowhelper.service.LiteFlowScanListener;
import com.yomahub.liteflowhelper.service.LiteFlowScanTrigger;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LiteFlow Helper 工具窗口工厂。
 */
public class ChainWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(ChainWindowFactory.class);

    private static final Icon CHAIN_ICON = IconLoader.getIcon("/icons/chain.svg", ChainWindowFactory.class);
    private static final String MSG_INDEXING = "项目正在索引中，请稍候...";
    private static final String MSG_LOADING = "正在扫描 LiteFlow 数据...";
    private static final String MSG_NO_CHAINS_FOUND = "未找到任何流程(Chain)";
    private static final String MSG_NO_NODES_FOUND = "未找到任何节点(Node)";

    private Tree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private DefaultMutableTreeNode chainsRootNode;
    private DefaultMutableTreeNode liteflowNodesRootNode;
    private Project project;
    private LiteFlowCacheService cacheService;
    private LiteFlowScanCoordinator scanCoordinator;
    private SearchTextField searchTextField;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.cacheService = LiteFlowCacheService.getInstance(project);
        this.scanCoordinator = LiteFlowScanCoordinator.getInstance(project);

        JPanel topPanel = new JPanel(new BorderLayout());
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        AnAction refreshAction = new AnAction("刷新", "重新扫描并加载 Chains 和 Nodes", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                showLoadingMessage();
                LiteFlowRefreshStateService.getInstance(project).requestManualRefresh();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                Project currentProject = e.getProject();
                e.getPresentation().setEnabled(currentProject != null
                        && !currentProject.isDisposed()
                        && !DumbService.getInstance(currentProject).isDumb());
            }
        };
        actionGroup.add(refreshAction);

        ActionToolbar actionToolbar = actionManager.createActionToolbar("LiteFlowHelperToolbar", actionGroup, true);
        actionToolbar.setTargetComponent(toolWindow.getComponent());
        topPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);

        searchTextField = new SearchTextField();
        searchTextField.getTextEditor().getEmptyText().setText("通过名称或 ID 过滤...");
        searchTextField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTree();
            }
        });
        topPanel.add(searchTextField, BorderLayout.CENTER);

        rootNode = new DefaultMutableTreeNode("Root");
        chainsRootNode = new DefaultMutableTreeNode("规则 (Chains)");
        liteflowNodesRootNode = new DefaultMutableTreeNode("组件 (Nodes)");
        rootNode.add(chainsRootNode);
        rootNode.add(liteflowNodesRootNode);

        treeModel = new DefaultTreeModel(rootNode);
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new MyTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                if (project.isDisposed() || DumbService.getInstance(project).isDumb()) {
                    return;
                }

                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }

                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof ChainInfo chainInfo && chainInfo.getPsiFile().isValid()) {
                    new OpenFileDescriptor(project, chainInfo.getPsiFile().getVirtualFile(), chainInfo.getOffset()).navigate(true);
                } else if (userObject instanceof LiteFlowNodeInfo nodeInfo && nodeInfo.getPsiElement().isValid()) {
                    new OpenFileDescriptor(project, nodeInfo.getPsiFile().getVirtualFile(), nodeInfo.getOffset()).navigate(true);
                }
            }
        });

        JComponent mainPanel = buildMainPanel(topPanel);
        Content content = ContentFactory.getInstance().createContent(mainPanel, "", false);
        toolWindow.getContentManager().addContent(content);

        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
            @Override
            public void enteredDumbMode() {
                SwingUtilities.invokeLater(() -> showIndexingMessage());
                actionToolbar.updateActionsImmediately();
            }

            @Override
            public void exitDumbMode() {
                SwingUtilities.invokeLater(() -> {
                    LOG.info("退出 dumb mode，准备刷新 LiteFlow 数据");
                    scanCoordinator.requestScan(LiteFlowScanTrigger.TOOL_WINDOW, false, false);
                });
                actionToolbar.updateActionsImmediately();
            }
        });

        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(LiteFlowScanListener.TOPIC, new LiteFlowScanListener() {
            @Override
            public void scanStarted(@NotNull LiteFlowScanTrigger trigger) {
                SwingUtilities.invokeLater(() -> {
                    if (cacheService.isCacheEmpty()) {
                        showLoadingMessage();
                    }
                });
            }

            @Override
            public void scanFinished(@NotNull LiteFlowScanTrigger trigger) {
                SwingUtilities.invokeLater(() -> filterTree());
            }
        });

        if (DumbService.getInstance(project).isDumb()) {
            showIndexingMessage();
        } else if (cacheService.isCacheEmpty()) {
            showLoadingMessage();
            scanCoordinator.requestScan(LiteFlowScanTrigger.TOOL_WINDOW, false, false);
        } else {
            filterTree();
        }
    }

    private JComponent buildMainPanel(@NotNull JPanel topPanel) {
        JBScrollPane scrollPane = new JBScrollPane(tree);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private void filterTree() {
        if (searchTextField == null || cacheService == null) {
            return;
        }

        String filterText = searchTextField.getText().toLowerCase().trim();
        List<ChainInfo> allChains = cacheService.getCachedChains();
        List<LiteFlowNodeInfo> allNodes = cacheService.getCachedNodes();

        List<ChainInfo> filteredChains = filterText.isEmpty()
                ? new ArrayList<>(allChains)
                : allChains.stream()
                .filter(chain -> chain.getName().toLowerCase().contains(filterText))
                .collect(Collectors.toList());
        updateTreeWithChains(filteredChains, true);

        List<LiteFlowNodeInfo> filteredNodes = filterText.isEmpty()
                ? new ArrayList<>(allNodes)
                : allNodes.stream()
                .filter(node -> node.getNodeId().toLowerCase().contains(filterText)
                        || (node.getNodeName() != null && node.getNodeName().toLowerCase().contains(filterText)))
                .collect(Collectors.toList());
        updateTreeWithLiteFlowNodes(filteredNodes, true);
    }

    private void showIndexingMessage() {
        showStatusMessage(MSG_INDEXING);
    }

    private void showLoadingMessage() {
        showStatusMessage(MSG_LOADING);
    }

    private void showStatusMessage(@NotNull String message) {
        if (chainsRootNode == null || liteflowNodesRootNode == null || treeModel == null || tree == null) {
            return;
        }
        chainsRootNode.removeAllChildren();
        liteflowNodesRootNode.removeAllChildren();
        chainsRootNode.add(new DefaultMutableTreeNode(message));
        liteflowNodesRootNode.add(new DefaultMutableTreeNode(message));
        treeModel.nodeStructureChanged(rootNode);
        tree.expandPath(new TreePath(chainsRootNode.getPath()));
        tree.expandPath(new TreePath(liteflowNodesRootNode.getPath()));
    }

    private void updateTreeWithChains(List<ChainInfo> chainsToShow, boolean expandNode) {
        if (chainsRootNode == null || treeModel == null || tree == null || project == null || project.isDisposed()) {
            return;
        }
        chainsRootNode.removeAllChildren();
        if (chainsToShow == null || chainsToShow.isEmpty()) {
            chainsRootNode.add(new DefaultMutableTreeNode(MSG_NO_CHAINS_FOUND));
        } else {
            chainsToShow.forEach(chainInfo -> chainsRootNode.add(new DefaultMutableTreeNode(chainInfo)));
        }
        treeModel.nodeStructureChanged(chainsRootNode);
        if (expandNode && chainsToShow != null && !chainsToShow.isEmpty()) {
            tree.expandPath(new TreePath(chainsRootNode.getPath()));
        }
    }

    private void updateTreeWithLiteFlowNodes(List<LiteFlowNodeInfo> nodesToShow, boolean expandNode) {
        if (liteflowNodesRootNode == null || treeModel == null || tree == null || project == null || project.isDisposed()) {
            return;
        }
        liteflowNodesRootNode.removeAllChildren();
        if (nodesToShow == null || nodesToShow.isEmpty()) {
            liteflowNodesRootNode.add(new DefaultMutableTreeNode(MSG_NO_NODES_FOUND));
        } else {
            nodesToShow.forEach(nodeInfo -> liteflowNodesRootNode.add(new DefaultMutableTreeNode(nodeInfo)));
        }
        treeModel.nodeStructureChanged(liteflowNodesRootNode);
        if (expandNode && nodesToShow != null && !nodesToShow.isEmpty()) {
            tree.expandPath(new TreePath(liteflowNodesRootNode.getPath()));
        }
    }

    private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                          boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode treeNode)) {
                return;
            }

            Object userObject = treeNode.getUserObject();
            if (userObject instanceof String text) {
                if ("规则 (Chains)".equals(text)) {
                    append(text);
                    setIcon(AllIcons.Nodes.ModuleGroup);
                } else if ("组件 (Nodes)".equals(text)) {
                    append(text);
                    setIcon(AllIcons.Nodes.PpLibFolder);
                } else if (text.startsWith("未找到任何") || MSG_INDEXING.equals(text) || MSG_LOADING.equals(text)) {
                    append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    setIcon(AllIcons.General.Information);
                } else {
                    append(text);
                }
                return;
            }

            if (userObject instanceof ChainInfo chainInfo) {
                append(chainInfo.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append(" (" + chainInfo.getFileName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                setIcon(CHAIN_ICON);
                return;
            }

            if (userObject instanceof LiteFlowNodeInfo nodeInfo) {
                append(nodeInfo.getNodeId(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                SimpleTextAttributes blueText = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.PINK);
                String description = nodeInfo.getType().getDescription();
                if (description != null && !description.isEmpty()) {
                    append(" [", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    append(description, blueText);
                    append("]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }

                String locationInfo = nodeInfo.getFileName() + " - " + nodeInfo.getSource();
                append(" (" + locationInfo + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);

                Icon nodeIcon = nodeInfo.getType().getIcon();
                setIcon(nodeIcon != null ? nodeIcon : AllIcons.Nodes.Property);
                return;
            }

            if (userObject != null) {
                append(userObject.toString());
            } else {
                append("null object", SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
        }
    }
}
