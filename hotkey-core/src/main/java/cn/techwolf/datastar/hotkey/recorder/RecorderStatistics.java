package cn.techwolf.datastar.hotkey.recorder;

import java.io.Serializable;

/**
 * 访问记录器统计信息
 * 包含 PromotionQueue 和 RecentQps 的大小和内存使用情况
 *
 * @author techwolf
 * @date 2024
 */
public class RecorderStatistics implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * PromotionQueue 统计信息
     */
    private ComponentStatistics promotionQueue;

    /**
     * RecentQps 统计信息
     */
    private ComponentStatistics recentQps;

    public RecorderStatistics() {
    }

    public RecorderStatistics(ComponentStatistics promotionQueue, ComponentStatistics recentQps) {
        this.promotionQueue = promotionQueue;
        this.recentQps = recentQps;
    }

    public ComponentStatistics getPromotionQueue() {
        return promotionQueue;
    }

    public void setPromotionQueue(ComponentStatistics promotionQueue) {
        this.promotionQueue = promotionQueue;
    }

    public ComponentStatistics getRecentQps() {
        return recentQps;
    }

    public void setRecentQps(ComponentStatistics recentQps) {
        this.recentQps = recentQps;
    }

    @Override
    public String toString() {
        return "RecorderStatistics{" +
                "promotionQueue=" + promotionQueue +
                ", recentQps=" + recentQps +
                '}';
    }

    /**
     * 组件统计信息（PromotionQueue 或 RecentQps）
     */
    public static class ComponentStatistics implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 大小（key数量）
         */
        private int size;

        /**
         * 内存大小（字节）
         */
        private long memorySize;

        /**
         * 平均key长度（字符数）
         */
        private int avgKeyLength;

        public ComponentStatistics() {
        }

        public ComponentStatistics(int size, long memorySize, int avgKeyLength) {
            this.size = size;
            this.memorySize = memorySize;
            this.avgKeyLength = avgKeyLength;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public long getMemorySize() {
            return memorySize;
        }

        public void setMemorySize(long memorySize) {
            this.memorySize = memorySize;
        }

        public int getAvgKeyLength() {
            return avgKeyLength;
        }

        public void setAvgKeyLength(int avgKeyLength) {
            this.avgKeyLength = avgKeyLength;
        }

        @Override
        public String toString() {
            return "ComponentStatistics{" +
                    "size=" + size +
                    ", memorySize=" + memorySize +
                    ", avgKeyLength=" + avgKeyLength +
                    '}';
        }
    }
}
