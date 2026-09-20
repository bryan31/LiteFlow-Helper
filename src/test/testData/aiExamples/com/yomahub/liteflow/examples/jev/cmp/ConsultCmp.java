package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

@LiteflowComponent("consult")
public class ConsultCmp extends NodeComponent {
    @Override
    public void process() {
        getContextBean(SupportContext.class).complete(getNodeId(), "进入售后咨询分支，可在这里接入知识库或客服 Agent。");
    }
}
