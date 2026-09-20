package com.yomahub.liteflow.examples.jev.cmp;

import com.yomahub.liteflow.agent.jev.JevChoiceResult;
import com.yomahub.liteflow.agent.jev.JevSwitchComponent;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.examples.jev.SupportContext;

import java.util.LinkedHashMap;
import java.util.Map;

@LiteflowComponent("supportRouter")
public class SupportRouter extends JevSwitchComponent {
    @Override
    protected Object state() {
        return Map.of("customerMessage", getContextBean(SupportContext.class).getMessage());
    }

    @Override
    protected String instructions() {
        return "根据 customerMessage 中客户当前明确的主要诉求选择处理流程。"
                + "注意否定表达和意图变化；客户已撤回的诉求不应作为当前诉求。"
                + "多个诉求无法确定主次、缺少必要信息或与售后无关时，选择均不适用。";
    }

    @Override
    public Map<String, String> choices() {
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("refund", "客户明确希望退货退款或退回款项，且没有撤回退款诉求。即使同时抱怨服务，明确要求退款仍归此类。");
        choices.put("exchange", "客户希望更换商品，例如更换尺寸、颜色或换一个完好的商品，而非退回款项。");
        choices.put("logistics", "客户主要希望查询订单发货、快递运输或派送进度，催发货或催配送，没有明确退款或换货诉求。");
        choices.put("invoice", "客户主要希望开具、补发、更改发票，或询问发票抬头、税号与开票信息。");
        choices.put("complaint", "客户明确要求投诉客服态度、服务体验或升级给主管处理，且主要诉求不是退款、换货、查物流或开发票。");
        choices.put("consult", "客户咨询商品、使用方法或一般售后规则，尚未提出明确退款或换货要求，也不属于物流、发票或投诉处理。");
        return choices;
    }

    @Override
    protected void onDecision(JevChoiceResult result) {
        getContextBean(SupportContext.class).setDecision(result);
    }
}
