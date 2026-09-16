package com.carcare.service;

import cn.hutool.json.JSONUtil;
import com.carcare.config.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 来单提醒发布器：业务侧只负责「发布」，投递交给 Redis Pub/Sub（见 WebSocketConfig 订阅端）。
 * 解耦点：业务线程不感知谁在线、连接在哪个节点；
 * 无订阅者时消息即丢——来单提醒属「尽力而为」通知，离线场景由订单列表兜底，
 * 若要求必达应改用 MQ 持久化队列（面试可对比 Pub/Sub vs MQ 的取舍）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WsNotifyService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 推送业务通知给全体在线管理员
     *
     * @param type 通知类型：order=新订单 work=工单状态
     * @param text 展示文案
     */
    public void notifyAdmins(String type, String text, Map<String, Object> extra) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("text", text);
            msg.put("time", LocalDateTime.now().withNano(0).toString());
            if (extra != null) {
                msg.putAll(extra);
            }
            stringRedisTemplate.convertAndSend(WebSocketConfig.WS_NOTIFY_CHANNEL, JSONUtil.toJsonStr(msg));
        } catch (Exception e) {
            // 通知失败不影响主业务
            log.warn("WS 通知发布失败 type={}", type, e);
        }
    }
}
