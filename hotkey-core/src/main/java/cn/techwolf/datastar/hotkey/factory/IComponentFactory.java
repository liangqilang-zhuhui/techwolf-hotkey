package cn.techwolf.datastar.hotkey.factory;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.scheduler.IScheduler;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;

/**
 * 组件工厂接口
 * 职责：创建和管理所有热Key相关组件，支持依赖注入和实现替换
 *
 * @author techwolf
 * @date 2024
 */
public interface IComponentFactory {
    /**
     * 创建访问记录器
     *
     * @param config 配置参数
     * @return 访问记录器
     */
    IAccessRecorder createAccessRecorder(HotKeyConfig config);

    /**
     * 创建数据存储
     *
     * @param config 配置参数
     * @return 数据存储
     */
    IHotKeyStorage createHotKeyStorage(HotKeyConfig config);

    /**
     * 创建热Key选择器
     *
     * @param accessRecorder 访问记录器
     * @param config 配置参数
     * @return 热Key选择器
     */
    IHotKeySelector createHotKeySelector(IAccessRecorder accessRecorder, HotKeyConfig config);

    /**
     * 创建缓存数据更新器
     *
     * @param hotKeyStorage 数据存储
     * @param config 配置参数
     * @return 缓存数据更新器
     */
    ICacheDataUpdater createCacheDataUpdater(IHotKeyStorage hotKeyStorage, HotKeyConfig config);

    /**
     * 创建热Key管理器
     *
     * @param accessRecorder 访问记录器
     * @param hotKeyStorage 数据存储
     * @param cacheDataUpdater 缓存数据更新器（用于清理被降级key）
     * @return 热Key管理器
     */
    IHotKeyManager createHotKeyManager(IAccessRecorder accessRecorder,
                                       IHotKeyStorage hotKeyStorage,
                                       ICacheDataUpdater cacheDataUpdater);

    /**
     * 创建定时任务调度器
     *
     * @param config 配置参数
     * @param hotKeyManager 热Key管理器
     * @param hotKeySelector 热Key选择器
     * @return 定时任务调度器
     */
    IScheduler createScheduler(HotKeyConfig config,
                               IHotKeyManager hotKeyManager,
                               IHotKeySelector hotKeySelector);
}
