package cn.techwolf.datastar.hotkey.demo;

/**
 * Redis客户端运行时异常类
 * <p>
 * 当Redis操作过程中发生错误时，抛出此异常。
 * </p>
 *
 * @author system
 * @date 2024
 */
public class RedisClientRuntimeException extends RuntimeException {

    public RedisClientRuntimeException(String message) {
        super(message);
    }

    public RedisClientRuntimeException(Throwable cause) {
        super(cause);
    }

    public RedisClientRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
