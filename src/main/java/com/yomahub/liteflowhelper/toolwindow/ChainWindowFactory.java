package com.yomahub.liteflowhelper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * LiteFlow Helper 工具窗口的工厂类。
 * [BGT重构] 负责创建工具窗口的UI内容、初始化扫描服务、处理用户交互以及管理数据刷新。
 * 所有的扫描操作现在都在后台任务中运行，以防止UI冻结。
 */
public class ChainWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(ChainWindowFactory.class);

    private Tree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private DefaultMutableTreeNode chainsRootNode;
    private DefaultMutableTreeNode liteflowNodesRootNode;

    private Project project;
    private LiteFlowChainScanner chainScanner;
    private LiteFlowNodeScanner nodeScanner;

    private Timer autoRefreshTimer;
    private static final long AUTO_REFRESH_INTERVAL = 30 * 1000;

    private SearchTextField searchTextField;
    private List<ChainInfo> allChains = new ArrayList<>();
    private List<LiteFlowNodeInfo> allNodes = new ArrayList<>();

    private static final Icon CHAIN_ICON = IconLoader.getIcon("/icons/chain.svg", ChainWindowFactory.class);
    private static final String MSG_INDEXING = "项目正在索引中，请稍候...";
    private static final String MSG_NO_CHAINS_FOUND = "未找到任何流程 (Chain)";
    private static final String MSG_NO_NODES_FOUND = "未找到任何节点 (Node)";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.chainScanner = new LiteFlowChainScanner();
        this.nodeScanner = new LiteFlowNodeScanner();

        JPanel topPanel = new JPanel(new BorderLayout());
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        AnAction refreshAction = new AnAction("刷新", "重新扫描并加载Chains和Nodes", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // [BGT修改] 手动刷新时，直接调用新的后台任务方法
                runRefreshTask();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                Project currentProject = e.getProject();
                e.getPresentation().setEnabled(currentProject != null && !currentProject.isDisposed() && !DumbService.getInstance(currentProject).isDumb());
            }
        };
        actionGroup.add(refreshAction);
        ActionToolbar actionToolbar = actionManager.createActionToolbar("LiteFlowHelperToolbar", actionGroup, true);
        actionToolbar.setTargetComponent(toolWindow.getComponent());
        topPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);

        searchTextField = new SearchTextField();
        searchTextField.getTextEditor().getEmptyText().setText("通过名称或ID过滤...");
        searchTextField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterTree(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterTree(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterTree(); }
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
                if (e.getClickCount() == 2) {
                    if (project == null || project.isDisposed() || DumbService.getInstance(project).isDumb()) return;
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object userObject = selectedNode.getUserObject();
                    if (userObject instanceof ChainInfo) {
                        ChainInfo chainInfo = (ChainInfo) userObject;
                        if (chainInfo.getPsiFile().isValid()) {
                            new OpenFileDescriptor(project, chainInfo.getPsiFile().getVirtualFile(), chainInfo.getOffset()).navigate(true);
                        }
                    } else if (userObject instanceof LiteFlowNodeInfo) {
                        LiteFlowNodeInfo nodeInfo = (LiteFlowNodeInfo) userObject;
                        if (nodeInfo.getPsiElement().isValid()) {
                            new OpenFileDescriptor(project, nodeInfo.getPsiFile().getVirtualFile(), nodeInfo.getOffset()).navigate(true);
                        }
                    }
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(tree);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(mainPanel, "", false);
        toolWindow.getContentManager().addContent(content);

        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
            @Override
            public void enteredDumbMode() {
                SwingUtilities.invokeLater(() -> showIndexingMessage());
                actionToolbar.updateActionsImmediately();
            }

            @Override
            public void exitDumbMode() {
                // [BGT修改] 索引完成后，在UI线程中启动后台刷新任务
                SwingUtilities.invokeLater(() -> {
                    LOG.info("已退出 dumb mode。触发 LiteFlow 数据刷新。");
                    runRefreshTask();
                });
                actionToolbar.updateActionsImmediately();
            }
        });

        if (DumbService.getInstance(project).isDumb()) {
            showIndexingMessage();
        } else {
            // [BGT修改] 初始加载也使用后台任务
            runRefreshTask();
        }
        startAutoRefreshTimer();

        Disposer.register(toolWindow.getDisposable(), this::stopAutoRefreshTimer);
    }

    /**
     * 根据搜索框中的文本过滤树形视图。
     */
    private void filterTree() {
        String filterText = searchTextField.getText().toLowerCase().trim();

        List<ChainInfo> filteredChains = filterText.isEmpty() ? new ArrayList<>(allChains) :
                allChains.stream()
                        .filter(chain -> chain.getName().toLowerCase().contains(filterText))
                        .collect(Collectors.toList());
        updateTreeWithChains(filteredChains, true);

        List<LiteFlowNodeInfo> filteredNodes = filterText.isEmpty() ? new ArrayList<>(allNodes) :
                allNodes.stream()
                        .filter(node -> node.getNodeId().toLowerCase().contains(filterText) ||
                                (node.getNodeName() != null && node.getNodeName().toLowerCase().contains(filterText)))
                        .collect(Collectors.toList());
        updateTreeWithLiteFlowNodes(filteredNodes, true);
    }

    /**
     * 在树形视图中显示“项目正在索引中...”的消息。
     */
    private void showIndexingMessage() {
        if (chainsRootNode == null || liteflowNodesRootNode == null || treeModel == null || tree == null) return;
        chainsRootNode.removeAllChildren();
        liteflowNodesRootNode.removeAllChildren();
        chainsRootNode.add(new DefaultMutableTreeNode(MSG_INDEXING));
        liteflowNodesRootNode.add(new DefaultMutableTreeNode(MSG_INDEXING));
        treeModel.nodeStructureChanged(rootNode);
        tree.expandPath(new TreePath(chainsRootNode.getPath()));
        tree.expandPath(new TreePath(liteflowNodesRootNode.getPath()));
    }

    /**
     * [BGT修改]
     * 启动自动刷新定时器。定时器任务现在会调用 runWhenSmart 来确保在非Dumb Mode下执行后台刷新。
     */
    private void startAutoRefreshTimer() {
        if (autoRefreshTimer != null) autoRefreshTimer.cancel();

        autoRefreshTimer = new Timer("LiteFlowHelperAutoRefreshTimer", true);
        autoRefreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (project != null && !project.isDisposed()) {
                    // runWhenSmart 确保任务只在索引完成后执行
                    DumbService.getInstance(project).runWhenSmart(() -> runRefreshTask());
                } else {
                    LOG.info("自动刷新：项目为null或已释放，正在停止定时器。");
                    stopAutoRefreshTimer();
                }
            }
        }, AUTO_REFRESH_INTERVAL, AUTO_REFRESH_INTERVAL);
        LOG.info("LiteFlow Helper 自动刷新定时器已启动。");
    }

    private void stopAutoRefreshTimer() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
            LOG.info("LiteFlow Helper 自动刷新定时器已停止。");
        }
    }

    /**
     * [BGT新增]
     * 运行一个后台任务来扫描Chains和Nodes，并在完成后更新UI。
     * 这是所有刷新操作（手动、自动、索引后）的统一入口。
     */
    private void runRefreshTask() {
        if (project == null || project.isDisposed()) {
            LOG.warn("刷新任务跳过：项目为null或已释放。");
            return;
        }

        // 定义一个可被用户取消的后台任务
        Task.Backgroundable task = new Task.Backgroundable(project, "正在扫描LiteFlow组件", true) {
            // 这两个字段将用于在后台线程和UI线程之间传递数据
            private List<ChainInfo> foundChains;
            private List<LiteFlowNodeInfo> foundNodes;

            /**
             * 这个方法在后台线程中执行。
             * 所有耗时操作都在这里完成。
             */
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.info("后台任务开始：扫描LiteFlow chains和nodes。");
                indicator.setIndeterminate(false); // 设置为非不确定模式，可以显示具体进度
                indicator.setFraction(0.0);

                // 步骤 1: 扫描 chains
                indicator.setText("正在扫描 LiteFlow chains...");
                foundChains = chainScanner.findChains(getProject());
                indicator.setFraction(0.5);

                // 检查用户是否点击了取消按钮
                indicator.checkCanceled();

                // 步骤 2: 扫描 nodes
                indicator.setText("正在扫描 LiteFlow nodes...");
                foundNodes = nodeScanner.findLiteFlowNodes(getProject());
                indicator.setFraction(1.0);

                LOG.info("后台任务扫描完成。");
            }

            /**
             * 当后台任务成功完成后，此方法在UI线程（EDT）中执行。
             * 所有UI更新操作都必须在这里进行。
             */
            @Override
            public void onSuccess() {
                if (getProject() == null || getProject().isDisposed()) {
                    return;
                }
                LOG.info("后台任务成功：正在更新UI。");

                // 1. 更新主数据缓存
                allChains = (foundChains != null) ? foundChains : Collections.emptyList();
                allNodes = (foundNodes != null) ? foundNodes : Collections.emptyList();

                // 2. 应用当前过滤器以更新UI
                filterTree();
            }

            /**
             * 当任务被取消时，在UI线程中调用。
             */
            @Override
            public void onCancel() {
                LOG.info("LiteFlow组件扫描任务被取消。");
            }

            /**
             * 当后台任务中发生异常时，在UI线程中调用。
             */
            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("扫描LiteFlow组件时发生错误", error);
            }
        };

        // 将任务交给IDE的进度管理器来执行
        ProgressManager.getInstance().run(task);
    }

    /**
     * 使用给定的Chain数据列表更新树的“流程 (Chains)”部分。
     */
    private void updateTreeWithChains(List<ChainInfo> chainsToShow, boolean expandNode) {
        if (chainsRootNode == null || treeModel == null || tree == null || project == null || project.isDisposed()) return;
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

    /**
     * 使用给定的Node数据列表更新树的“节点 (Nodes)”部分。
     */
    private void updateTreeWithLiteFlowNodes(List<LiteFlowNodeInfo> nodesToShow, boolean expandNode) {
        if (liteflowNodesRootNode == null || treeModel == null || tree == null || project == null || project.isDisposed()) return;
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

    /**
     * 自定义树节点的渲染器。
     */
    private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                          boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
                Object userObject = treeNode.getUserObject();

                if (userObject instanceof String) {
                    String text = (String) userObject;
                    if ("规则 (Chains)".equals(text)) {
                        append(text);
                        setIcon(AllIcons.Nodes.ModuleGroup);
                    } else if ("组件 (Nodes)".equals(text)) {
                        append(text);
                        setIcon(AllIcons.Nodes.PpLibFolder);
                    } else if (text.startsWith("未找到任何") || MSG_INDEXING.equals(text)) {
                        append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                        setIcon(AllIcons.General.Information);
                    } else {
                        append(text);
                    }
                } else if (userObject instanceof ChainInfo) {
                    ChainInfo chainInfo = (ChainInfo) userObject;
                    append(chainInfo.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    append(" (" + chainInfo.getFileName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    setIcon(CHAIN_ICON);
                } else if (userObject instanceof LiteFlowNodeInfo) {
                    LiteFlowNodeInfo nodeInfo = (LiteFlowNodeInfo) userObject;
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
                } else if (userObject != null) {
                    append(userObject.toString());
                } else {
                    append("null object", SimpleTextAttributes.ERROR_ATTRIBUTES);
                }
            }
        }
    }
}
