package cn.techwolf.datastar.hotkey.monitor;

/**
 * 热Key监控器接口（模块五）
 * 职责：每分钟监控，内存热key列表、数量；数据存储层大小；访问记录模块的数据量、大小
 *
 * @author techwolf
 * @date 2024
 */
public interface IHotKeyMonitor {
    /**
     * 获取监控信息
     *
     * @return 监控信息对象
     */
    MonitorInfo getMonitorInfo();

    /**
     * 监控信息对象
     */
    class MonitorInfo {
        /**
         * 热key列表
         */
        private java.util.Set<String> hotKeys;

        /**
         * 热key数量
         */
        private int hotKeyCount;

        /**
         * 数据存储层大小
         */
        private long storageSize;

        /**
         * 访问记录模块的数据量
         */
        private int recorderSize;

        /**
         * 访问记录模块的内存大小（估算）
         */
        private long recorderMemorySize;

        // Getters and Setters
        public java.util.Set<String> getHotKeys() {
            return hotKeys;
        }

        public void setHotKeys(java.util.Set<String> hotKeys) {
            this.hotKeys = hotKeys;
        }

        public int getHotKeyCount() {
            return hotKeyCount;
        }

        public void setHotKeyCount(int hotKeyCount) {
            this.hotKeyCount = hotKeyCount;
        }

        public long getStorageSize() {
            return storageSize;
        }

        public void setStorageSize(long storageSize) {
            this.storageSize = storageSize;
        }

        public int getRecorderSize() {
            return recorderSize;
        }

        public void setRecorderSize(int recorderSize) {
            this.recorderSize = recorderSize;
        }

        public long getRecorderMemorySize() {
            return recorderMemorySize;
        }

        public void setRecorderMemorySize(long recorderMemorySize) {
            this.recorderMemorySize = recorderMemorySize;
        }
    }
}
