package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

@LiteflowComponent("complaint")
public class ComplaintCmp extends NodeComponent {
    @Override
    public void process() {
        getContextBean(SupportContext.class).complete(getNodeId(), "已进入投诉处理流程。请补充相关订单与沟通经过，以便专员了解情况并跟进。");
    }
}
