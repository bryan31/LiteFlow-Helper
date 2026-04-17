package com.yomahub.liteflowhelper.icon;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * 为 LiteFlow 的 XML 配置文件提供自定义图标。
 * <p>
 * 这个类会检查项目中的 XML 文件，如果文件符合 LiteFlow 配置文件的特征，
 * 就在项目树视图中将其默认图标替换为指定的自定义图标。
 * </p>
 */
public class LiteFlowXmlIconProvider extends IconProvider {

    /**
     * 加载位于 'resources/icons/xml.svg' 的自定义图标。
     * 请确保您的图标文件确实存放在该路径下。
     */
    private static final Icon LITEFLOW_XML_ICON = IconLoader.getIcon("/icons/xml.svg", LiteFlowXmlIconProvider.class);

    /**
     * IntelliJ Platform 调用此方法来获取文件的图标。
     *
     * @param element PSI 元素，通常代表一个文件。
     * @param flags   一些附加的标志，当前未使用。
     * @return 如果 element 是一个 LiteFlow XML 文件，则返回自定义图标；否则返回 null。
     */
    @Nullable
    @Override
    public Icon getIcon(@NotNull PsiElement element, int flags) {
        // 确保项目已完成索引，避免在 "Dumb Mode" 下执行操作。
        if (element.getProject() != null && DumbService.getInstance(element.getProject()).isDumb()) {
            return null;
        }

        // 判断当前元素是否为 XmlFile 的一个实例。
        if (element instanceof XmlFile) {
            XmlFile xmlFile = (XmlFile) element;

            // [核心修改] 使用返回 boolean 的 isLiteFlowXml 方法进行判断。
            if (LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
                // 如果判断为真，则返回我们的自定义图标。
                return LITEFLOW_XML_ICON;
            }
        }

        // 对于所有其他情况（非 XML 文件或非 LiteFlow XML 文件），返回 null。
        // IDEA 将会使用默认的图标。
        return null;
    }
}
