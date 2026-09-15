package com.carcare.service;

import com.carcare.mapper.SeqAllocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 号段模式发号器（不可变号段 + 原子换段 + 双缓冲预取）。
 * <p>
 * 原理：每次不是向 DB 逐条自增，而是 UPDATE max_id = max_id + step 一次性「预占」
 * 一个号段 (old, new]，之后 step 次发号只在内存 AtomicLong 上递增。
 * DB 写压力从「每单一次」降为「每 step 单一次」。
 * <p>
 * 并发正确性设计（实验测试 SegmentServiceTest 复现过早期回拨式实现的重复号 bug）：
 * 1) Segment 的 max/step 一经构造不再修改——换段 = 整体替换 AtomicReference 指向，
 *    彻底消灭「拿旧段的号 + 读新段的界」竞态；
 * 2) 计数器只前进不回拨：新段起点 = max(号段理论起点, 旧段上界)，
 *    越界号只会在锁内被丢弃、从不返回，因此从旧段上界+1 重发不会重复；
 * 3) 发号快路径无锁（AtomicLong），仅换段时拿锁 + 锁内重读槽位双重检查；
 * 4) 号段消耗 90% 时 CAS 抢预取权，后台备好 next 段，换段零 DB 等待。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentService {

    private final SeqAllocMapper seqAllocMapper;

    /** 每个业务一个可原子替换的号段槽位 */
    private final Map<String, AtomicReference<Segment>> slots = new ConcurrentHashMap<>();

    /** 不可变号段：除 current/next/loading 外构造后不再变更 */
    static final class Segment {
        final String bizType;
        final ReentrantLock lock = new ReentrantLock();
        final AtomicLong current;
        final long max;
        final int step;
        final AtomicBoolean loading = new AtomicBoolean(false);
        /** 预取好的下一段（双缓冲） */
        volatile Segment next;

        Segment(String bizType, long current, long max, int step) {
            this.bizType = bizType;
            this.current = new AtomicLong(current);
            this.max = max;
            this.step = step;
        }
    }

    /**
     * 取全局 id：
     * 快路径：current 自增未越界直接返回（无锁）；
     * 越界：进入 lockAndSwap 在「当前段」的锁内换段取号。
     */
    public long nextId(String bizType) {
        AtomicReference<Segment> slot = slots.computeIfAbsent(bizType,
                k -> new AtomicReference<>(new Segment(k, 0, 0, 0))); // 空段逼出首次换段
        Segment seg = slot.get();
        long id = seg.current.incrementAndGet();
        if (id > seg.max) {
            id = lockAndSwap(slot);
        }
        prefetch(slot, seg);
        return id;
    }

    /**
     * 换段临界区。铁律：锁谁、验谁、换谁必须是同一个对象——
     * 早期版本在 try 内重新赋值 seg 后 finally 解了新段的锁，
     * 抛 IllegalMonitorStateException（实验测试当场抓获）。
     * 循环模式：锁住「读到的当前段」→ 锁内验证槽位没被换过 → 没换过才换。
     */
    private long lockAndSwap(AtomicReference<Segment> slot) {
        for (;;) {
            Segment cur = slot.get();
            cur.lock.lock();
            try {
                if (slot.get() != cur) {
                    continue; // 别人已换段：解锁重读，在新段上再判断
                }
                if (cur.current.get() < cur.max) {
                    return cur.current.incrementAndGet(); // 号被别的越界线程补回来了，直接取
                }
                Segment fresh = takeNext(cur);
                slot.set(fresh);
                return fresh.current.incrementAndGet();
            } finally {
                cur.lock.unlock();
            }
        }
    }

    /** 优先用预取好的 next；没有则同步向 DB 领一段 */
    private Segment takeNext(Segment old) {
        Segment next = old.next;
        if (next != null) {
            old.next = null;
            return next;
        }
        return allocate(old.bizType, old.max);
    }

    /** 消耗满 90%：CAS 抢预取权，后台异步备好下一段 */
    private void prefetch(AtomicReference<Segment> slot, Segment seg) {
        if (seg.max <= 0 || seg.next != null
                || seg.current.get() < seg.max - seg.step / 10
                || !seg.loading.compareAndSet(false, true)) {
            return;
        }
        // 项目当前为 JDK 17，无虚拟线程；若整体升级 JDK 21 可换 Thread.ofVirtual()
        CompletableFuture.runAsync(() -> {
            try {
                // 段可能已被换下（同步领段抢先完成），此时预取只会浪费一个号段
                if (slot.get() == seg) {
                    seg.next = allocate(seg.bizType, seg.max);
                }
            } catch (Exception e) {
                log.error("预取号段失败 biz={}", seg.bizType, e);
            } finally {
                seg.loading.set(false);
            }
        });
    }

    /**
     * 向 DB 占一段 (newMax-step, newMax]。
     * notBelow=旧段上界：起点只前进不回拨。
     * allocate 的 UPDATE 靠 InnoDB 行锁串行，多实例并发领段不会拿到重叠区间。
     */
    private Segment allocate(String bizType, long notBelow) {
        seqAllocMapper.allocate(bizType);
        Long newMax = seqAllocMapper.selectMaxId(bizType);
        if (newMax == null) {
            throw new IllegalStateException("t_seq_alloc 未配置业务号段: " + bizType
                    + "，请执行 document/sql/phase2_seckill.sql 补种");
        }
        int step = getStepOf(bizType);
        long base = Math.max(newMax - step, notBelow);
        log.debug("号段加载 biz={} -> ({}, {}]", bizType, base, newMax);
        return new Segment(bizType, base, newMax, step);
    }

    private int getStepOf(String bizType) {
        var alloc = seqAllocMapper.selectById(bizType);
        return alloc == null ? 1000 : alloc.getStep();
    }
}
