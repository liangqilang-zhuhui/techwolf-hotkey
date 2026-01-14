package cn.techwolf.datastar.hotkey.factory;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.selector.HotKeySelector;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;
import cn.techwolf.datastar.hotkey.updater.CacheDataUpdater;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;
import cn.techwolf.datastar.hotkey.core.HotKeyManager;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.AccessRecorder;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.scheduler.IScheduler;
import cn.techwolf.datastar.hotkey.scheduler.SchedulerManager;
import cn.techwolf.datastar.hotkey.storage.HotKeyStorage;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;

/**
 * 默认组件工厂实现
 * 职责：使用默认实现创建所有热Key相关组件
 *
 * @author techwolf
 * @date 2024
 */
public class DefaultComponentFactory implements IComponentFactory {
    @Override
    public IAccessRecorder createAccessRecorder(HotKeyConfig config) {
        return new AccessRecorder(config);
    }

    @Override
    public IHotKeyStorage createHotKeyStorage(HotKeyConfig config) {
        return new HotKeyStorage(config);
    }

    @Override
    public IHotKeySelector createHotKeySelector(IAccessRecorder accessRecorder, HotKeyConfig config) {
        return new HotKeySelector(accessRecorder, config);
    }

    @Override
    public ICacheDataUpdater createCacheDataUpdater(IHotKeyStorage hotKeyStorage, HotKeyConfig config) {
        return new CacheDataUpdater(hotKeyStorage, config);
    }

    @Override
    public IHotKeyManager createHotKeyManager(IAccessRecorder accessRecorder,
                                              IHotKeyStorage hotKeyStorage,
                                              ICacheDataUpdater cacheDataUpdater) {
        return new HotKeyManager(accessRecorder, hotKeyStorage, cacheDataUpdater);
    }

    @Override
    public IScheduler createScheduler(HotKeyConfig config,
                                      IHotKeyManager hotKeyManager,
                                      IHotKeySelector hotKeySelector) {
        return new SchedulerManager(config, hotKeyManager, hotKeySelector);
    }
}
