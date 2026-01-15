package cn.techwolf.datastar.hotkey.scheduler;

import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.exception.DefaultExceptionHandler;
import cn.techwolf.datastar.hotkey.exception.IExceptionHandler;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务管理器实现
 * 职责：统一管理所有定时任务的启动、停止和资源释放
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class SchedulerManager implements IScheduler {
    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    /**
     * 热Key管理器
     */
    private final IHotKeyManager hotKeyManager;

    /**
     * 热Key选择器
     */
    private final IHotKeySelector hotKeySelector;

    /**
     * 异常处理器
     */
    private final IExceptionHandler exceptionHandler;

    private final IAccessRecorder accessRecorder;
    /**
     * 定时任务执行器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 是否运行中
     */
    private volatile boolean running = false;

    /**
     * 构造函数
     *
     * @param config 配置参数
     * @param hotKeyManager 热Key管理器
     * @param hotKeySelector 热Key选择器
     * @param accessRecorder 访问记录器
     */
    public SchedulerManager(HotKeyConfig config,
                           IHotKeyManager hotKeyManager,
                           IHotKeySelector hotKeySelector,
                           IAccessRecorder accessRecorder) {
        this(config, hotKeyManager, hotKeySelector, accessRecorder, null);
    }

    /**
     * 构造函数（支持自定义异常处理器）
     *
     * @param config 配置参数
     * @param hotKeyManager 热Key管理器
     * @param hotKeySelector 热Key选择器
     * @param accessRecorder 访问记录器
     * @param exceptionHandler 异常处理器（可选，为null时使用默认处理器）
     */
    public SchedulerManager(HotKeyConfig config,
                           IHotKeyManager hotKeyManager,
                           IHotKeySelector hotKeySelector,
                           IAccessRecorder accessRecorder,
                           IExceptionHandler exceptionHandler) {
        this.config = config;
        this.hotKeyManager = hotKeyManager;
        this.hotKeySelector = hotKeySelector;
        this.accessRecorder = accessRecorder;
        this.exceptionHandler = exceptionHandler != null ? exceptionHandler : new DefaultExceptionHandler();
    }

    @Override
    public void start() {
        if (running) {
            log.warn("定时任务管理器已在运行中");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "HotKey-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // 启动热Key晋升任务
        schedulePromotionTask();
        
        // 启动热Key降级和清理任务
        scheduleDemotionTask();

        running = true;
        long promotionInterval = config.getDetection().getPromotionInterval();
        long demotionInterval = config.getDetection().getDemotionInterval();
        log.info("定时任务管理器已启动，晋升间隔: {}ms, 降级间隔: {}ms", promotionInterval, demotionInterval);
    }

    /**
     * 启动热Key晋升任务
     */
    private void schedulePromotionTask() {
        long promotionInterval = config.getDetection().getPromotionInterval();
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        executePromotionTask();
                    } catch (Exception e) {
                        exceptionHandler.handleException("热Key晋升任务", e);
                    }
                },
                promotionInterval,
                promotionInterval,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 执行热Key晋升任务
     */
    private void executePromotionTask() {
        // 1. 获取当前热Key列表
        Set<String> currentHotKeys = hotKeyManager.getHotKeys();
        // 2. 调用选择器计算应该晋升的key
        Set<String> newHotKeys = hotKeySelector.promoteHotKeys(currentHotKeys);
        // 3. 更新管理器中的热Key列表
        if (newHotKeys != null && !newHotKeys.isEmpty()) {
            hotKeyManager.promoteHotKeys(newHotKeys);
        }
    }

    /**
     * 启动热Key降级和清理任务
     */
    private void scheduleDemotionTask() {
        long demotionInterval = config.getDetection().getDemotionInterval();
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        executeDemotionTask();
                    } catch (Exception e) {
                        exceptionHandler.handleException("热Key降级和淘汰任务", e);
                    }
                },
                demotionInterval,
                demotionInterval,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 执行热Key降级和清理任务
     */
    private void executeDemotionTask() {
        // 1. 获取当前热Key列表
        Set<String> currentHotKeys = hotKeyManager.getHotKeys();
        // 2. 调用选择器计算应该降级的key
        Set<String> removedKeys = hotKeySelector.demoteAndEvict(currentHotKeys);
        // 3. 更新管理器中的热Key列表（清理逻辑在 Manager 内部自动处理）
        if (removedKeys != null && !removedKeys.isEmpty()) {
            hotKeyManager.demoteHotKeys(removedKeys);
        }
        // 4. 清理过期和低QPS的访问记录（异步执行）
        accessRecorder.cleanupKeys();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        running = false;
        log.info("定时任务管理器已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
