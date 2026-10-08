package com.carcare;

import com.carcare.service.SegmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 实验5：DB 号段被「回拨」之后，继续发号会不会发出重复号？
 *
 * 背景：SegmentService.allocate() 里有一行守卫
 *     long base = Math.max(newMax - step, notBelow);
 *   注释说是「起点只前进不回拨」。
 * 本实验人为把 DB 的 max_id 改小（模拟：备份恢复 / 人工改库 / 主从切换读到旧值），
 * 看这行守卫到底挡住了什么。
 *
 * 【为什么要跑多轮】
 * 单轮结果是不可信的：号段发号器内部有「主线程同步领段」和「后台预取线程领段」
 * 两条路径会竞争同一个 DB 计数器，谁先执行会改变本轮结局。
 * 只跑一轮就下结论，等于把运气当成规律。所以这里跑 ROUNDS 轮，看分布。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SegmentRollbackExperimentTest {

    /** 轮数：单轮不可信，看分布 */
    private static final int ROUNDS = 10;

    /** 每轮先发多少个号（刚好用完第一段的一部分） */
    private static final int WARMUP = 800;

    /** 回拨之后再发多少个号 */
    private static final int AFTER_ROLLBACK = 1200;

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
    @DisplayName("实验5：DB max_id 被回拨后继续发号，统计重复号（多轮看分布）")
    void rollbackHazard() {
        List<Integer> dupCounts = new ArrayList<>();
        List<String> segmentTrace = new ArrayList<>();

        System.out.printf("%n[实验5] 每轮：先发 %d 个号 → 把 DB max_id 人为改回 0 → 再发 %d 个号%n",
                WARMUP, AFTER_ROLLBACK);

        for (int round = 1; round <= ROUNDS; round++) {
            // 每轮用独立 biz_type：号段槽位是按 bizType 缓存在内存里的，换名字才能从零开始
            String biz = "demo_rollback_" + round;
            reset(biz);

            Set<Long> issued = new HashSet<>();
            for (int i = 0; i < WARMUP; i++) {
                issued.add(segmentService.nextId(biz));
            }
            long maxBeforeRollback = dbMax(biz);

            // 人为回拨 DB 号段进度
            jdbc.update("UPDATE t_seq_alloc SET max_id = 0 WHERE biz_type = ?", biz);

            int dup = 0;
            List<Long> dupSamples = new ArrayList<>();
            long minId = Long.MAX_VALUE;
            long maxId = Long.MIN_VALUE;
            for (int i = 0; i < AFTER_ROLLBACK; i++) {
                long id = segmentService.nextId(biz);
                minId = Math.min(minId, id);
                maxId = Math.max(maxId, id);
                if (!issued.add(id)) {
                    dup++;
                    if (dupSamples.size() < 3) {
                        dupSamples.add(id);
                    }
                }
            }

            dupCounts.add(dup);
            System.out.printf("[实验5] 第%2d轮：回拨前 max_id=%d → 回拨后再发 %d 个号，"
                            + "范围 [%d, %d]，★重复 %d 个 %s%n",
                    round, maxBeforeRollback, AFTER_ROLLBACK, minId, maxId, dup,
                    dupSamples.isEmpty() ? "" : "样例=" + dupSamples);
        }

        int min = dupCounts.stream().mapToInt(Integer::intValue).min().orElse(-1);
        int max = dupCounts.stream().mapToInt(Integer::intValue).max().orElse(-1);
        double avg = dupCounts.stream().mapToInt(Integer::intValue).average().orElse(-1);
        long zeroRounds = dupCounts.stream().filter(d -> d == 0).count();

        System.out.printf("%n[实验5] ===== 汇总（%d 轮）=====%n", ROUNDS);
        System.out.printf("[实验5] 重复数 min=%d  max=%d  平均=%.1f%n", min, max, avg);
        System.out.printf("[实验5] 10 轮里 0 重复的有 %d 轮，出现重复的有 %d 轮%n",
                zeroRounds, ROUNDS - zeroRounds);
        System.out.printf("[实验5] 各轮明细：%s%n", dupCounts);
        System.out.printf("[实验5] 想一想：为什么同一份代码、同一个场景，各轮结果会不一样？%n%n");
    }
}
