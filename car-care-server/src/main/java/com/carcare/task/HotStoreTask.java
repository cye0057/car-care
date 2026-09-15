package com.carcare.task;

import com.carcare.service.GeoStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热度榜定时刷新任务。
 * 演示用 1 分钟（cron: 秒 分 时 日 月 周），生产可放宽到 5 分钟并叠加权重衰减。
 * 多实例部署时该任务会重复执行——聚合是幂等全量重建所以无害，
 * 阶段6 可升级为 Redisson 分布式锁保证单实例执行（简历点）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotStoreTask {

    private final GeoStoreService geoStoreService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void refreshHot() {
        try {
            geoStoreService.refreshHot();
        } catch (Exception e) {
            log.error("热度榜刷新任务异常", e);
        }
    }
}
