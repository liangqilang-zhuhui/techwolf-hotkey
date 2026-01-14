package cn.techwolf.datastar.hotkey.exception;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认异常处理器实现
 * 职责：统一处理异常，记录日志，不抛出异常
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class DefaultExceptionHandler implements IExceptionHandler {
    @Override
    public void handleException(String context, Exception exception) {
        if (context == null) {
            context = "未知操作";
        }
        log.error("{}执行失败: {}", context, exception.getMessage(), exception);
    }
}
