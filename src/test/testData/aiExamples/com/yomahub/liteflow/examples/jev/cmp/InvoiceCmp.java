package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

@LiteflowComponent("invoice")
public class InvoiceCmp extends NodeComponent {
    @Override
    public void process() {
        getContextBean(SupportContext.class).complete(getNodeId(), "已进入发票办理流程。请准备订单号、发票抬头与纳税人识别号，以便核对开票信息。");
    }
}
