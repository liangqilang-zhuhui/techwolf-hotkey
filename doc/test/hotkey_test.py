#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
热Key功能测试脚本
用于测试热Key检测功能的完整性、合理性和潜在bug

测试配置：
- hotKeyQpsThreshold: 30.0 (热Key QPS阈值)
- warmKeyQpsThreshold: 10.0 (温Key QPS阈值)
- recorderMaxCapacity: 15 (访问记录最大容量)
"""

import requests
import time
import threading
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Tuple
import sys

# 配置
BASE_URL = "http://localhost:8080"
API_SET = f"{BASE_URL}/api/redis/set"
API_GET = f"{BASE_URL}/api/redis/get"
API_GET_BATCH = f"{BASE_URL}/api/redis/get-batch"
API_SET_BATCH = f"{BASE_URL}/api/redis/set-batch"

# 监控API
API_MONITOR_INFO = f"{BASE_URL}/api/hotkey/monitor/info"
API_MONITOR_CHECK = f"{BASE_URL}/api/hotkey/monitor/check"
API_MONITOR_HOTKEYS = f"{BASE_URL}/api/hotkey/monitor/hotkeys"
API_MONITOR_STATS = f"{BASE_URL}/api/hotkey/monitor/stats"
API_MONITOR_REFRESH = f"{BASE_URL}/api/hotkey/monitor/refresh"

# 测试配置
HOT_KEY_QPS_THRESHOLD = 30.0  # 热Key QPS阈值
WARM_KEY_QPS_THRESHOLD = 10.0  # 温Key QPS阈值
RECORDER_MAX_CAPACITY = 15  # 访问记录最大容量

# 测试结果统计
test_results = {
    "passed": 0,
    "failed": 0,
    "errors": []
}


def log_test(test_name: str, passed: bool, message: str = ""):
    """记录测试结果"""
    status = "✓ PASS" if passed else "✗ FAIL"
    print(f"[{status}] {test_name}")
    if message:
        print(f"    {message}")
    if passed:
        test_results["passed"] += 1
    else:
        test_results["failed"] += 1
        test_results["errors"].append(f"{test_name}: {message}")


def wait_for_promotion(seconds: int = 6):
    """等待热Key晋升（晋升间隔5秒，等待6秒确保完成）"""
    print(f"    等待 {seconds} 秒，让热Key检测完成晋升...")
    time.sleep(seconds)


def wait_for_demotion(seconds: int = 61):
    """等待热Key降级（降级间隔60秒，等待61秒确保完成）"""
    print(f"    等待 {seconds} 秒，让热Key检测完成降级...")
    time.sleep(seconds)


def check_is_hot_key(key: str) -> bool:
    """检查指定key是否为热Key"""
    try:
        response = requests.get(API_MONITOR_CHECK, params={"key": key}, timeout=5)
        if response.status_code == 200:
            data = response.json()
            return data.get("isHotKey", False)
        return False
    except Exception as e:
        print(f"    检查热Key状态失败: {e}")
        return False


def get_monitor_info() -> Dict:
    """获取完整监控信息"""
    try:
        response = requests.get(API_MONITOR_INFO, timeout=5)
        if response.status_code == 200:
            return response.json()
        return {}
    except Exception as e:
        print(f"    获取监控信息失败: {e}")
        return {}


def get_hot_keys() -> List[str]:
    """获取热Key列表"""
    try:
        response = requests.get(API_MONITOR_HOTKEYS, timeout=5)
        if response.status_code == 200:
            data = response.json()
            hot_keys = data.get("hotKeys", [])
            return hot_keys if isinstance(hot_keys, list) else []
        return []
    except Exception as e:
        print(f"    获取热Key列表失败: {e}")
        return []


def test_1_basic_set_get():
    """测试1: 基础功能测试 - 正常set和get操作"""
    print("\n" + "="*60)
    print("测试1: 基础功能测试")
    print("="*60)
    
    key = "test:basic:1"
    value = "basic_value_123"
    
    try:
        # 测试set
        response = requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
        if response.status_code == 200:
            log_test("基础SET操作", True)
        else:
            log_test("基础SET操作", False, f"状态码: {response.status_code}")
            return
        
        # 测试get
        response = requests.get(API_GET, params={"key": key}, timeout=5)
        if response.status_code == 200 and response.text.strip() == value:
            log_test("基础GET操作", True)
        else:
            log_test("基础GET操作", False, f"返回值: {response.text}")
    
    except Exception as e:
        log_test("基础功能测试", False, str(e))


def test_2_hot_key_detection():
    """测试2: 热Key检测测试 - 高频访问触发热Key"""
    print("\n" + "="*60)
    print("测试2: 热Key检测测试")
    print("="*60)
    
    key = "test:hotkey:1"
    value = "hotkey_value_123"
    
    try:
        # 1. 设置key
        requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
        log_test("设置测试key", True)
        
        # 2. 高频访问（超过30 QPS，持续10秒，总共至少300次访问）
        # 使用批量接口快速访问
        print(f"    开始高频访问 key={key}，目标QPS > {HOT_KEY_QPS_THRESHOLD}")
        start_time = time.time()
        access_count = 0
        target_count = int(HOT_KEY_QPS_THRESHOLD * 10) + 100  # 10秒内至少400次访问
        
        # 使用批量接口，每次访问100次
        batch_size = 100
        batches = (target_count + batch_size - 1) // batch_size
        
        for i in range(batches):
            response = requests.get(API_GET_BATCH, params={"key": key, "count": batch_size}, timeout=30)
            if response.status_code == 200:
                access_count += batch_size
            time.sleep(0.1)  # 稍微延迟，避免过快
        
        elapsed = time.time() - start_time
        actual_qps = access_count / elapsed if elapsed > 0 else 0
        
        log_test(f"高频访问完成 (访问{access_count}次, 耗时{elapsed:.2f}秒, QPS={actual_qps:.2f})", 
                 actual_qps > HOT_KEY_QPS_THRESHOLD, 
                 f"实际QPS: {actual_qps:.2f}, 阈值: {HOT_KEY_QPS_THRESHOLD}")
        
        # 3. 等待晋升完成
        wait_for_promotion(6)
        
        # 4. 验证key是否被识别为热Key
        print("    验证key是否被识别为热Key...")
        is_hot = check_is_hot_key(key)
        log_test("热Key识别验证", is_hot, 
                 f"key={key}, isHotKey={is_hot}")
        
        # 5. 验证热Key缓存是否生效（连续访问应该从缓存获取）
        print("    验证热Key缓存是否生效...")
        cache_hit_count = 0
        total_checks = 10
        
        for i in range(total_checks):
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200 and response.text.strip() == value:
                cache_hit_count += 1
            time.sleep(0.1)
        
        # 如果缓存生效，应该能正常获取到值
        log_test("热Key缓存验证", cache_hit_count == total_checks, 
                 f"成功获取: {cache_hit_count}/{total_checks}")
        
        # 6. 获取监控统计信息
        monitor_info = get_monitor_info()
        if monitor_info.get("enabled"):
            print(f"    监控信息: 热Key数量={monitor_info.get('hotKeyCount', 0)}, "
                  f"命中率={monitor_info.get('hotKeyHitRate', 0)*100:.2f}%")
    
    except Exception as e:
        log_test("热Key检测测试", False, str(e))


def test_3_warm_key_detection():
    """测试3: 温Key检测测试 - 中等频率访问触发温Key"""
    print("\n" + "="*60)
    print("测试3: 温Key检测测试")
    print("="*60)
    
    key = "test:warmkey:1"
    value = "warmkey_value_123"
    
    try:
        # 1. 设置key
        requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
        log_test("设置测试key", True)
        
        # 2. 中等频率访问（10-30 QPS之间，持续10秒）
        print(f"    开始中等频率访问 key={key}，目标QPS在 {WARM_KEY_QPS_THRESHOLD}-{HOT_KEY_QPS_THRESHOLD} 之间")
        start_time = time.time()
        access_count = 0
        target_qps = (WARM_KEY_QPS_THRESHOLD + HOT_KEY_QPS_THRESHOLD) / 2  # 20 QPS
        target_count = int(target_qps * 10)  # 10秒内200次访问
        
        # 控制访问频率在20 QPS左右
        for i in range(target_count):
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200:
                access_count += 1
            time.sleep(1.0 / target_qps)  # 控制QPS
        
        elapsed = time.time() - start_time
        actual_qps = access_count / elapsed if elapsed > 0 else 0
        
        log_test(f"中等频率访问完成 (访问{access_count}次, 耗时{elapsed:.2f}秒, QPS={actual_qps:.2f})", 
                 WARM_KEY_QPS_THRESHOLD <= actual_qps < HOT_KEY_QPS_THRESHOLD,
                 f"实际QPS: {actual_qps:.2f}, 期望范围: [{WARM_KEY_QPS_THRESHOLD}, {HOT_KEY_QPS_THRESHOLD})")
        
        # 3. 等待检测完成
        wait_for_promotion(6)
        
        # 4. 验证值仍然可以正常获取
        response = requests.get(API_GET, params={"key": key}, timeout=5)
        log_test("温Key访问验证", response.status_code == 200 and response.text.strip() == value,
                 f"返回值: {response.text.strip()}")
    
    except Exception as e:
        log_test("温Key检测测试", False, str(e))


def test_4_capacity_limit():
    """测试4: 容量限制测试 - 测试recorderMaxCapacity=15的限制"""
    print("\n" + "="*60)
    print("测试4: 容量限制测试")
    print("="*60)
    
    try:
        # 创建超过容量限制的key（16个key，超过15的限制）
        print(f"    创建 {RECORDER_MAX_CAPACITY + 1} 个不同的key（超过容量限制{RECORDER_MAX_CAPACITY}）")
        
        keys = []
        for i in range(RECORDER_MAX_CAPACITY + 1):
            key = f"test:capacity:{i}"
            value = f"value_{i}"
            requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
            keys.append(key)
        
        log_test(f"创建 {len(keys)} 个key", True)
        
        # 对每个key进行高频访问（触发容量限制）
        print(f"    对每个key进行高频访问（触发容量清理）...")
        for key in keys:
            # 每个key访问50次，快速触发
            requests.get(API_GET_BATCH, params={"key": key, "count": 50}, timeout=10)
        
        # 等待清理完成
        wait_for_promotion(6)
        
        # 验证：至少前几个key应该还能正常访问
        success_count = 0
        for i, key in enumerate(keys[:5]):  # 检查前5个key
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200:
                success_count += 1
        
        log_test("容量限制后key访问验证", success_count > 0,
                 f"成功访问: {success_count}/5 个key（容量限制可能导致部分key被清理）")
    
    except Exception as e:
        log_test("容量限制测试", False, str(e))


def test_5_concurrent_access():
    """测试5: 并发访问测试 - 多线程并发访问"""
    print("\n" + "="*60)
    print("测试5: 并发访问测试")
    print("="*60)
    
    key = "test:concurrent:1"
    value = "concurrent_value_123"
    thread_count = 10
    requests_per_thread = 50
    
    try:
        # 1. 设置key
        requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
        log_test("设置测试key", True)
        
        # 2. 多线程并发访问
        print(f"    启动 {thread_count} 个线程，每个线程访问 {requests_per_thread} 次")
        
        def worker(thread_id: int) -> Tuple[int, int]:
            success = 0
            failed = 0
            for i in range(requests_per_thread):
                try:
                    response = requests.get(API_GET, params={"key": key}, timeout=5)
                    if response.status_code == 200 and response.text.strip() == value:
                        success += 1
                    else:
                        failed += 1
                except Exception as e:
                    failed += 1
                time.sleep(0.01)  # 稍微延迟
            return success, failed
        
        start_time = time.time()
        with ThreadPoolExecutor(max_workers=thread_count) as executor:
            futures = [executor.submit(worker, i) for i in range(thread_count)]
            results = [f.result() for f in as_completed(futures)]
        
        elapsed = time.time() - start_time
        total_success = sum(r[0] for r in results)
        total_failed = sum(r[1] for r in results)
        total_requests = thread_count * requests_per_thread
        
        log_test(f"并发访问完成 (成功: {total_success}/{total_requests}, 失败: {total_failed}, 耗时: {elapsed:.2f}秒)",
                 total_failed == 0,
                 f"成功率: {total_success*100/total_requests:.2f}%")
    
    except Exception as e:
        log_test("并发访问测试", False, str(e))


def test_6_cache_hit_rate():
    """测试6: 缓存命中率测试 - 验证热Key缓存命中率"""
    print("\n" + "="*60)
    print("测试6: 缓存命中率测试")
    print("="*60)
    
    key = "test:cache:1"
    value = "cache_value_123"
    
    try:
        # 1. 设置key
        requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
        log_test("设置测试key", True)
        
        # 2. 高频访问触发热Key
        print(f"    高频访问触发热Key...")
        requests.get(API_GET_BATCH, params={"key": key, "count": 500}, timeout=30)
        wait_for_promotion(6)
        
        # 3. 连续访问，验证缓存命中
        print("    连续访问验证缓存命中...")
        hit_count = 0
        miss_count = 0
        total_checks = 100
        
        for i in range(total_checks):
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200:
                if response.text.strip() == value:
                    hit_count += 1
                else:
                    miss_count += 1
            else:
                miss_count += 1
            time.sleep(0.05)
        
        hit_rate = hit_count / total_checks * 100 if total_checks > 0 else 0
        log_test(f"缓存命中率验证 (命中: {hit_count}/{total_checks}, 命中率: {hit_rate:.2f}%)",
                 hit_rate >= 90,  # 期望命中率至少90%
                 f"命中率: {hit_rate:.2f}%")
    
    except Exception as e:
        log_test("缓存命中率测试", False, str(e))


def test_7_edge_cases():
    """测试7: 边界情况测试 - 空值、特殊字符、超长key等"""
    print("\n" + "="*60)
    print("测试7: 边界情况测试")
    print("="*60)
    
    test_cases = [
        ("test:empty:value", ""),  # 空值
        ("test:special:chars", "!@#$%^&*()"),  # 特殊字符
        ("test:unicode", "测试中文值"),  # Unicode字符
        ("test:long:value", "A" * 1000),  # 长值
    ]
    
    try:
        for key, value in test_cases:
            # 设置
            response = requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
            if response.status_code == 200:
                log_test(f"设置边界key: {key[:30]}", True)
            else:
                log_test(f"设置边界key: {key[:30]}", False, f"状态码: {response.status_code}")
                continue
            
            # 获取
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200 and response.text == value:
                log_test(f"获取边界key: {key[:30]}", True)
            else:
                log_test(f"获取边界key: {key[:30]}", False, 
                        f"期望: {value[:50]}, 实际: {response.text[:50]}")
    
    except Exception as e:
        log_test("边界情况测试", False, str(e))


def test_8_key_not_exists():
    """测试8: 不存在的key测试"""
    print("\n" + "="*60)
    print("测试8: 不存在的key测试")
    print("="*60)
    
    key = "test:nonexistent:999999"
    
    try:
        response = requests.get(API_GET, params={"key": key}, timeout=5)
        # 不存在的key应该返回空或特定状态码，不应该崩溃
        log_test("访问不存在的key", response.status_code in [200, 404],
                 f"状态码: {response.status_code}, 响应: {response.text[:50]}")
    
    except Exception as e:
        log_test("不存在的key测试", False, str(e))


def test_9_multiple_hot_keys():
    """测试9: 多个热Key测试 - 同时存在多个热Key"""
    print("\n" + "="*60)
    print("测试9: 多个热Key测试")
    print("="*60)
    
    hot_key_count = 5
    keys = []
    
    try:
        # 1. 创建多个key
        for i in range(hot_key_count):
            key = f"test:multihot:{i}"
            value = f"multihot_value_{i}"
            requests.post(API_SET, params={"key": key, "value": value}, timeout=5)
            keys.append(key)
        
        log_test(f"创建 {hot_key_count} 个测试key", True)
        
        # 2. 对每个key进行高频访问
        print(f"    对每个key进行高频访问（触发热Key）...")
        for key in keys:
            requests.get(API_GET_BATCH, params={"key": key, "count": 500}, timeout=30)
        
        # 3. 等待晋升完成
        wait_for_promotion(6)
        
        # 4. 验证所有key是否都被识别为热Key
        print("    验证所有key是否都被识别为热Key...")
        hot_key_list = get_hot_keys()
        detected_hot_keys = [k for k in keys if k in hot_key_list]
        log_test(f"热Key识别验证 (识别: {len(detected_hot_keys)}/{hot_key_count})",
                 len(detected_hot_keys) == hot_key_count,
                 f"识别的热Key: {detected_hot_keys}")
        
        # 5. 验证所有key都能正常访问
        success_count = 0
        for key in keys:
            response = requests.get(API_GET, params={"key": key}, timeout=5)
            if response.status_code == 200:
                success_count += 1
        
        log_test(f"多个热Key访问验证 (成功: {success_count}/{hot_key_count})",
                 success_count == hot_key_count,
                 f"所有key应该都能正常访问")
    
    except Exception as e:
        log_test("多个热Key测试", False, str(e))


def test_10_value_update():
    """测试10: 值更新测试 - 热Key的值在Redis中更新后，缓存是否自动刷新"""
    print("\n" + "="*60)
    print("测试10: 值更新测试")
    print("="*60)
    
    key = "test:update:1"
    value1 = "value_original"
    value2 = "value_updated"
    
    try:
        # 1. 设置初始值
        requests.post(API_SET, params={"key": key, "value": value1}, timeout=5)
        log_test("设置初始值", True)
        
        # 2. 高频访问触发热Key
        requests.get(API_GET_BATCH, params={"key": key, "count": 500}, timeout=30)
        wait_for_promotion(6)
        
        # 3. 验证缓存中的值
        response = requests.get(API_GET, params={"key": key}, timeout=5)
        log_test("验证缓存中的初始值", response.text.strip() == value1,
                 f"返回值: {response.text.strip()}")
        
        # 4. 更新Redis中的值
        requests.post(API_SET, params={"key": key, "value": value2}, timeout=5)
        log_test("更新Redis中的值", True)
        
        # 5. 等待自动刷新（刷新间隔10秒）
        print("    等待自动刷新（刷新间隔10秒）...")
        time.sleep(12)
        
        # 6. 验证缓存是否更新
        response = requests.get(API_GET, params={"key": key}, timeout=5)
        log_test("验证缓存是否自动更新", response.text.strip() == value2,
                 f"期望: {value2}, 实际: {response.text.strip()}")
    
    except Exception as e:
        log_test("值更新测试", False, str(e))


def main():
    """主测试函数"""
    print("\n" + "="*60)
    print("热Key功能测试脚本")
    print("="*60)
    print(f"测试配置:")
    print(f"  - 热Key QPS阈值: {HOT_KEY_QPS_THRESHOLD}")
    print(f"  - 温Key QPS阈值: {WARM_KEY_QPS_THRESHOLD}")
    print(f"  - 访问记录最大容量: {RECORDER_MAX_CAPACITY}")
    print(f"  - 服务地址: {BASE_URL}")
    print("="*60)
    
    # 检查服务是否可用
    try:
        # 使用监控API检查服务是否可用
        response = requests.get(API_MONITOR_INFO, timeout=5)
        if response.status_code != 200:
            print(f"\n警告: 无法连接到服务 {BASE_URL}")
            print("请确保服务已启动并运行在 8080 端口")
            sys.exit(1)
    except Exception as e:
        print(f"\n错误: 无法连接到服务 {BASE_URL}: {e}")
        print("请确保服务已启动并运行在 8080 端口")
        sys.exit(1)
    
    # 执行所有测试
    test_1_basic_set_get()
    test_2_hot_key_detection()
    test_3_warm_key_detection()
    test_4_capacity_limit()
    test_5_concurrent_access()
    test_6_cache_hit_rate()
    test_7_edge_cases()
    test_8_key_not_exists()
    test_9_multiple_hot_keys()
    test_10_value_update()
    
    # 输出测试结果汇总
    print("\n" + "="*60)
    print("测试结果汇总")
    print("="*60)
    print(f"通过: {test_results['passed']}")
    print(f"失败: {test_results['failed']}")
    print(f"总计: {test_results['passed'] + test_results['failed']}")
    
    if test_results['errors']:
        print("\n失败详情:")
        for error in test_results['errors']:
            print(f"  - {error}")
    
    # 返回退出码
    if test_results['failed'] > 0:
        print("\n测试未完全通过，请检查上述错误")
        sys.exit(1)
    else:
        print("\n所有测试通过！")
        sys.exit(0)


if __name__ == "__main__":
    main()
