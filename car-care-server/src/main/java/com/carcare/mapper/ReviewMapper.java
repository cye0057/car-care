package com.carcare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carcare.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 评价/笔记表 Mapper，继承 MyBatis-Plus BaseMapper 获得单表 CRUD 能力。
 * 点赞计数用原子 UPDATE 增减（GREATEST 防负数），明细在 Redis Set
 */
public interface ReviewMapper extends BaseMapper<Review> {

    @Update("UPDATE t_review SET liked_count = GREATEST(liked_count + #{delta}, 0) WHERE id = #{id}")
    int incrLikedCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 门店评分聚合：该店全部有效评价的平均分（保留 1 位小数，与 t_store.score 精度一致）。
     * 手写 SQL 不会被 @TableLogic 自动追加条件，deleted 过滤必须显式写
     */
    @Select("SELECT ROUND(AVG(score), 1) FROM t_review WHERE store_id = #{storeId} AND deleted = 0")
    BigDecimal avgScoreByStore(@Param("storeId") Long storeId);
}
