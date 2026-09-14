package com.carcare.common;

/**
 * 业务异常：Service 层校验不通过时抛出，由 GlobalExceptionHandler
 * 捕获后以 Result.fail(message) 返回给前端，不打印堆栈
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
