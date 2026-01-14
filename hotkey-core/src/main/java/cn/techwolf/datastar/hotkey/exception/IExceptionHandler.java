package cn.techwolf.datastar.hotkey.exception;

/**
 * 异常处理器接口
 * 职责：统一处理异常，提供一致的异常处理策略
 *
 * @author techwolf
 * @date 2024
 */
public interface IExceptionHandler {
    /**
     * 处理异常
     *
     * @param context 异常上下文（如操作名称、key等）
     * @param exception 异常对象
     */
    void handleException(String context, Exception exception);
}
