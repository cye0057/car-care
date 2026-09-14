package com.carcare.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 全局统一响应体。code=1 表示业务成功，code=0 表示业务失败，
 * 401/403 由拦截器直接写 HTTP 状态码返回，不走此结构包装
 *
 * @param <T> 业务数据类型
 */
@Data
@Schema(description = "统一响应结果")
public class Result<T> {

    @Schema(description = "业务状态码：1成功 0失败")
    private Integer code;

    @Schema(description = "提示信息")
    private String msg;

    @Schema(description = "业务数据")
    private T data;

    /** 无数据的成功响应，用于新增/修改/删除等写操作 */
    public static <T> Result<T> success() {
        return build(1, "success", null);
    }

    /** 携带数据的成功响应，用于查询类接口 */
    public static <T> Result<T> success(T data) {
        return build(1, "success", data);
    }

    /** 业务失败响应，msg 面向用户可直接展示 */
    public static <T> Result<T> fail(String msg) {
        return build(0, msg, null);
    }

    private static <T> Result<T> build(Integer code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
