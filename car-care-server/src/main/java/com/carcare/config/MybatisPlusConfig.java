package com.carcare.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页插件（3.5.9+ 拆包后需额外引入 mybatis-plus-jsqlparser 依赖）
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器：Page 对象作为 Mapper 方法参数时自动拼接 limit，
     * 并额外执行一次 count 查询得到总记录数
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
