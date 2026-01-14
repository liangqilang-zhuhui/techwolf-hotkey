package cn.techwolf.datastar.hotkey.scheduler;

/**
 * 定时任务调度器接口
 * 职责：统一管理所有定时任务的启动、停止和资源释放
 *
 * @author techwolf
 * @date 2024
 */
public interface IScheduler {
    /**
     * 启动定时任务
     */
    void start();

    /**
     * 停止定时任务
     */
    void stop();

    /**
     * 检查调度器是否运行中
     *
     * @return 是否运行中
     */
    boolean isRunning();
}
