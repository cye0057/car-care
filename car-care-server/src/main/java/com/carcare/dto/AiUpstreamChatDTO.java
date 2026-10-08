package com.carcare.dto;

import com.carcare.vo.AiUserContextVO;
import lombok.Data;

import java.util.List;

/**
 * 发给 car-care-ai 的对话请求体。
 * 字段与 Python 侧 app/schemas.py::ChatRequest 一一对应，两边改动必须同步。
 * <p>
 * 注意历史消息是**由 Java 组装后下发**的：Python 不存会话，因此每次请求都要带上
 * 最近若干轮对话，否则模型就「失忆」了。
 */
@Data
public class AiUpstreamChatDTO {

    private String sessionId;

    private String message;

    private Long userId;

    /** 最近若干轮历史，按时间正序 */
    private List<Turn> history;

    /** 用户私有上下文：车辆、近期订单、工单进度、已领取优惠券 */
    private AiUserContextVO userContext;

    @Data
    public static class Turn {
        /** user / assistant */
        private String role;
        private String content;
    }
}
