package com.carcare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carcare.entity.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券表 Mapper。
 * 秒杀落库用带条件的原子 UPDATE（stock > 0 才扣减），
 * 受影响行数为 0 即代表 DB 层库存不足——这是 Redis 预减之外的最后一道防超卖闸门
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("UPDATE t_coupon SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int decreaseStock(@Param("id") Long id);

    @Update("UPDATE t_coupon SET stock = stock + 1 WHERE id = #{id}")
    int increaseStock(@Param("id") Long id);
}
