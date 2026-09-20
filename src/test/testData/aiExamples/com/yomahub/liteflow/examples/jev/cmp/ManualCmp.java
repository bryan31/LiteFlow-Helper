package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

@LiteflowComponent("manual")
public class ManualCmp extends NodeComponent {
    @Override
    public void process() {
        getContextBean(SupportContext.class).complete(getNodeId(), "当前信息不足、置信度未达阈值或没有适合的候选，进入人工处理分支。");
    }
}
