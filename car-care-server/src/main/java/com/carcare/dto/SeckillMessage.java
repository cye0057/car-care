package com.carcare.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀异步落库消息体：只携带定位信息，消费端回查 DB 取最新数据。
 * couponOrderId 由发号器在投递前生成，消费者按此 id 落库，
 * 接口层可直接返回该 id 供前端轮询领取结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {
    private Long couponId;
    private Long userId;
    private Long couponOrderId;
}
