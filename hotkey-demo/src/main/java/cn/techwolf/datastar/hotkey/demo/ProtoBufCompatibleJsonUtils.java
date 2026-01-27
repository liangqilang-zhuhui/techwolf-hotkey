package cn.techwolf.datastar.hotkey.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProtoBuf兼容的JSON工具类
 * <p>
 * 提供对象与JSON字符串之间的转换功能，兼容ProtoBuf格式。
 * </p>
 *
 * @author system
 * @date 2024
 */
public class ProtoBufCompatibleJsonUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtoBufCompatibleJsonUtils.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 要转换的对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error("对象转JSON失败", e);
            throw new RuntimeException("对象转JSON失败", e);
        }
    }

    /**
     * 将JSON字符串转换为对象
     *
     * @param json  JSON字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 转换后的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            LOGGER.error("JSON转对象失败, json: {}, class: {}", json, clazz.getName(), e);
            throw new RuntimeException("JSON转对象失败", e);
        }
    }
}
