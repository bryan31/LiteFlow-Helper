package com.yomahub.liteflow.examples.agent.skillstream.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("prepare")
public class PrepareCmp extends NodeComponent {

    @Override
    public void process() {
        Object prompt = this.getSlot().getChainReqData(this.getSlot().getChainId());
        System.out.println("[prepare] prompt=" + prompt);
    }
}
