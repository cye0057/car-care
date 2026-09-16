package com.carcare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carcare.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 评价/笔记表 Mapper，继承 MyBatis-Plus BaseMapper 获得单表 CRUD 能力。
 * 点赞计数用原子 UPDATE 增减（GREATEST 防负数），明细在 Redis Set
 */
public interface ReviewMapper extends BaseMapper<Review> {

    @Update("UPDATE t_review SET liked_count = GREATEST(liked_count + #{delta}, 0) WHERE id = #{id}")
    int incrLikedCount(@Param("id") Long id, @Param("delta") int delta);
}
