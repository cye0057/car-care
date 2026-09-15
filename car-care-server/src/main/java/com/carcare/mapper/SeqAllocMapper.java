package com.carcare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carcare.entity.SeqAlloc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 号段分配表 Mapper。
 * allocate 用 UPDATE 原子推进 max_id，再回读新值——
 * 并发下靠 InnoDB 行锁保证两个实例不会拿到重叠号段
 */
public interface SeqAllocMapper extends BaseMapper<SeqAlloc> {

    @Update("UPDATE t_seq_alloc SET max_id = max_id + step WHERE biz_type = #{bizType}")
    int allocate(@Param("bizType") String bizType);

    @Select("SELECT max_id FROM t_seq_alloc WHERE biz_type = #{bizType}")
    Long selectMaxId(@Param("bizType") String bizType);
}
