package com.carcare.common;

/**
 * Feed 流相关 Redis key 统一定义（阶段4）。
 * 数据结构选型对应经典方案：
 * 收件箱 ZSet(score=笔记id) → 天然支持按时间倒序+游标翻页；
 * 笔记缓存 String(JSON) → 查询收件箱后 MGET 批量取，避免回表；
 * 点赞 Set(member=userId) → O(1) 判断是否点过+SCARD 计数；
 * 达人集合 Set → 标记哪些作者走拉模式，Feed 查询时与关注列表求交集
 */
public class FeedKeys {

    /** 用户收件箱：member=笔记id，score=笔记id（自增即时间序，游标稳定） */
    public static final String FEED_INBOX = "feed:inbox:";
    /** 笔记详情缓存 */
    public static final String BLOG_CACHE = "feed:blog:";
    /** 点赞用户集合 */
    public static final String LIKES = "feed:likes:";
    /** 拉模式达人集合（粉丝数达阈值的作者） */
    public static final String KOL_SET = "feed:kol:set";

    public static String inbox(Long userId) {
        return FEED_INBOX + userId;
    }

    public static String blog(Long blogId) {
        return BLOG_CACHE + blogId;
    }

    public static String likes(Long blogId) {
        return LIKES + blogId;
    }
}
