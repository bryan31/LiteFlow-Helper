package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

@LiteflowComponent("exchange")
public class ExchangeCmp extends NodeComponent {
    @Override
    public void process() {
        getContextBean(SupportContext.class).complete(getNodeId(), "进入换货处理分支，请进一步确认需要更换的商品和规格。");
    }
}
