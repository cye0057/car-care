package com.carcare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 汽车维修保养服务系统 启动类。
 * 技术演进路线（详见项目 PLAN.md）：
 * 阶段1 Redis缓存体系 → 阶段2 优惠券秒杀+RabbitMQ → 阶段3 GEO附近门店+热榜（已完成）
 * → 阶段4 评价Feed流 → 阶段5 车主端+WebSocket工单提醒
 */
@SpringBootApplication
@MapperScan("com.carcare.mapper")
@EnableScheduling
public class CarCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarCareApplication.class, args);
    }
}
