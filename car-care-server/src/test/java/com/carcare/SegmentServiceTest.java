package com.carcare;

import com.carcare.service.SegmentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 号段发号器（SegmentService）行为实验。
 * 每个实验用独立的 biz_type 行，避免互相污染内存缓存的号段槽位。
 * 观察点：
 * 1) 领一次号段（step=1000）能发 1000 个号 → DB 写压力 1/1000
 * 2) 号段消耗到 90% 时，后台预取下一段（还没用完 max_id 就已推进）
 * 3) 跨号段发号连续无空洞、无重复
 * 4) 多线程并发发号不重不漏（AtomicLong 快路径 + 越界锁的正确性）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SegmentServiceTest {

    @Autowired
    SegmentService segmentService;

    @Autowired
    JdbcTemplate jdbc;

    private void reset(String biz) {
        jdbc.update("DELETE FROM t_seq_alloc WHERE biz_type = ?", biz);
        jdbc.update("INSERT INTO t_seq_alloc (biz_type, max_id, step) VALUES (?, 0, 1000)", biz);
    }

    private long dbMax(String biz) {
        return jdbc.queryForObject("SELECT max_id FROM t_seq_alloc WHERE biz_type = ?", Long.class, biz);
    }

    @Test
    @DisplayName("实验1+2：发800个号DB只推进1次；发到900时预取已把第二段占好")
    void allocationAndPrefetch() throws Exception {
        String biz = "demo_prefetch";
        reset(biz);

        long first = segmentService.nextId(biz);
        long last = -1;
        for (int i = 2; i <= 800; i++) {
            last = segmentService.nextId(biz);
        }
        System.out.printf("[实验1] 第1个号=%d 第800个号=%d → DB max_id=%d（800次发号，DB只被推进1次）%n",
                first, last, dbMax(biz));
        Assertions.assertEquals(1L, first);
        Assertions.assertEquals(800L, last);
        Assertions.assertEquals(1000L, dbMax(biz));

        for (int i = 801; i <= 900; i++) {
            segmentService.nextId(biz);
        }
        Thread.sleep(500); // 等后台预取线程完成
        long after = dbMax(biz);
        System.out.printf("[实验2] 发到第900个号（第一段还没用完）→ DB max_id=%d ← 预取线程已悄悄占好第二段(1000,2000]%n", after);
        Assertions.assertEquals(2000L, after, "90% 预取未生效");
    }

    @Test
    @DisplayName("实验3：跨号段发号 1..1005 连续无空洞")
    void continuityAcrossSegments() {
        String biz = "demo_cont";
        reset(biz);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 1005; i++) {
            ids.add(segmentService.nextId(biz));
        }
        Set<Long> set = new HashSet<>(ids);
        Assertions.assertEquals(1005, set.size(), "出现重复号！");
        Assertions.assertEquals(1L, Collections.min(ids));
        Assertions.assertEquals(1005L, Collections.max(ids));
        System.out.printf("[实验3] 发1005个号：去重后%d个，范围[%d, %d]，无重复无空洞%n",
                set.size(), Collections.min(ids), Collections.max(ids));
        System.out.print("[实验3] 号段边界(998~1006)样本：");
        ids.stream().filter(id -> id >= 998 && id <= 1006).sorted()
                .forEach(id -> System.out.print(id + " "));
        System.out.printf("%n[实验3] 此时 DB max_id=%d（第二段已被预取占位）%n", dbMax(biz));
    }

    @Test
    @DisplayName("实验4：8线程×1300次并发发号，不重复、趋势递增（允许号段空洞）")
    void concurrentUniqueness() throws Exception {
        String biz = "demo_conc";
        reset(biz);
        int threads = 8;
        int per = 1300;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> local = new ArrayList<>();
                for (int i = 0; i < per; i++) {
                    local.add(segmentService.nextId(biz));
                }
                return local;
            }));
        }
        long begin = System.nanoTime();
        start.countDown();
        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> f : futures) {
            all.addAll(f.get(30, TimeUnit.SECONDS));
        }
        double ms = (System.nanoTime() - begin) / 1e6;
        pool.shutdown();

        Set<Long> set = new HashSet<>(all);
        Assertions.assertEquals(threads * per, set.size(), "并发发号出现重复！");
        Assertions.assertEquals(1L, Collections.min(all));
        long max = Collections.max(all);
        Assertions.assertTrue(max >= (long) threads * per, "max 不可能小于发号总数");
        long gaps = (max - 1 + 1) - set.size(); // 空洞数 = 值域跨度 - 实际号数
        System.out.printf("[实验4] %d线程×%d=%d个号：重复%d个，范围[1, %d]，空洞%d个(预取浪费/竞态丢弃，属正常)，耗时%.0fms，吞吐≈%.0f号/秒，DB max_id=%d%n",
                threads, per, all.size(), all.size() - set.size(), max, gaps, ms,
                all.size() / (ms / 1000), dbMax(biz));
    }
}
