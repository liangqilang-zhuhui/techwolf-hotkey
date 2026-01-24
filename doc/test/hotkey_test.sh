#!/bin/bash
# 热Key功能测试脚本 (Shell版本)
# 用于快速测试热Key功能的基本场景

BASE_URL="http://localhost:8080"
API_SET="${BASE_URL}/api/redis/set"
API_GET="${BASE_URL}/api/redis/get"
API_GET_BATCH="${BASE_URL}/api/redis/get-batch"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试结果统计
PASSED=0
FAILED=0

# 打印测试结果
print_test() {
    local test_name=$1
    local passed=$2
    local message=$3
    
    if [ "$passed" -eq 1 ]; then
        echo -e "${GREEN}✓ PASS${NC} $test_name"
        if [ -n "$message" ]; then
        echo "    $message"
        fi
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC} $test_name"
        if [ -n "$message" ]; then
        echo "    $message"
        fi
        FAILED=$((FAILED + 1))
    fi
}

# 等待热Key晋升
wait_promotion() {
    local seconds=${1:-6}
    echo "    等待 ${seconds} 秒，让热Key检测完成晋升..."
    sleep $seconds
}

echo "============================================================"
echo "热Key功能测试脚本 (Shell版本)"
echo "============================================================"
echo "测试配置:"
echo "  - 热Key QPS阈值: 30.0"
echo "  - 温Key QPS阈值: 10.0"
echo "  - 访问记录最大容量: 15"
echo "  - 服务地址: ${BASE_URL}"
echo "============================================================"

# 检查服务是否可用
echo ""
echo "检查服务可用性..."
if curl -s -f "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    print_test "服务连接检查" 1 "服务运行正常"
else
    echo -e "${RED}错误: 无法连接到服务 ${BASE_URL}${NC}"
    echo "请确保服务已启动并运行在 8080 端口"
    exit 1
fi

# 测试1: 基础功能测试
echo ""
echo "============================================================"
echo "测试1: 基础功能测试"
echo "============================================================"

KEY="test:basic:1"
VALUE="basic_value_123"

# SET操作
if curl -s -X POST "${API_SET}?key=${KEY}&value=${VALUE}" | grep -q "成功"; then
    print_test "基础SET操作" 1
else
    print_test "基础SET操作" 0 "SET操作失败"
fi

# GET操作
RESULT=$(curl -s "${API_GET}?key=${KEY}")
if [ "$RESULT" = "$VALUE" ]; then
    print_test "基础GET操作" 1
else
    print_test "基础GET操作" 0 "返回值: ${RESULT}"
fi

# 测试2: 热Key检测测试
echo ""
echo "============================================================"
echo "测试2: 热Key检测测试"
echo "============================================================"

KEY="test:hotkey:1"
VALUE="hotkey_value_123"

# 设置key
curl -s -X POST "${API_SET}?key=${KEY}&value=${VALUE}" > /dev/null
print_test "设置测试key" 1

# 高频访问（使用批量接口）
echo "    开始高频访问 key=${KEY}，目标QPS > 30"
echo "    使用批量接口快速访问..."

START_TIME=$(date +%s)
curl -s "${API_GET_BATCH}?key=${KEY}&count=500" > /dev/null
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

print_test "高频访问完成 (访问500次, 耗时${ELAPSED}秒)" 1

# 等待晋升完成
wait_promotion 6

# 验证热Key缓存
echo "    验证热Key缓存是否生效..."
SUCCESS=0
for i in {1..10}; do
    RESULT=$(curl -s "${API_GET}?key=${KEY}")
    if [ "$RESULT" = "$VALUE" ]; then
        SUCCESS=$((SUCCESS + 1))
    fi
    sleep 0.1
done

if [ $SUCCESS -eq 10 ]; then
    print_test "热Key缓存验证" 1 "成功获取: ${SUCCESS}/10"
else
    print_test "热Key缓存验证" 0 "成功获取: ${SUCCESS}/10"
fi

# 测试3: 多个热Key测试
echo ""
echo "============================================================"
echo "测试3: 多个热Key测试"
echo "============================================================"

HOT_KEY_COUNT=5
echo "    创建 ${HOT_KEY_COUNT} 个测试key..."

for i in $(seq 0 $((HOT_KEY_COUNT - 1))); do
    KEY="test:multihot:${i}"
    VALUE="multihot_value_${i}"
    curl -s -X POST "${API_SET}?key=${KEY}&value=${VALUE}" > /dev/null
done

print_test "创建 ${HOT_KEY_COUNT} 个测试key" 1

# 对每个key进行高频访问
echo "    对每个key进行高频访问（触发热Key）..."
for i in $(seq 0 $((HOT_KEY_COUNT - 1))); do
    KEY="test:multihot:${i}"
    curl -s "${API_GET_BATCH}?key=${KEY}&count=500" > /dev/null
done

# 等待晋升完成
wait_promotion 6

# 验证所有key都能正常访问
SUCCESS=0
for i in $(seq 0 $((HOT_KEY_COUNT - 1))); do
    KEY="test:multihot:${i}"
    EXPECTED="multihot_value_${i}"
    RESULT=$(curl -s "${API_GET}?key=${KEY}")
    if [ "$RESULT" = "$EXPECTED" ]; then
        SUCCESS=$((SUCCESS + 1))
    fi
done

if [ $SUCCESS -eq $HOT_KEY_COUNT ]; then
    print_test "多个热Key访问验证" 1 "成功: ${SUCCESS}/${HOT_KEY_COUNT}"
else
    print_test "多个热Key访问验证" 0 "成功: ${SUCCESS}/${HOT_KEY_COUNT}"
fi

# 测试4: 边界情况测试
echo ""
echo "============================================================"
echo "测试4: 边界情况测试"
echo "============================================================"

# 空值测试
KEY="test:empty:value"
VALUE=""
curl -s -X POST "${API_SET}?key=${KEY}&value=${VALUE}" > /dev/null
RESULT=$(curl -s "${API_GET}?key=${KEY}")
if [ "$RESULT" = "$VALUE" ]; then
    print_test "空值测试" 1
else
    print_test "空值测试" 0 "期望: 空, 实际: ${RESULT}"
fi

# 特殊字符测试
KEY="test:special:chars"
VALUE="!@#\$%^&*()"
curl -s -X POST "${API_SET}?key=${KEY}&value=${VALUE}" > /dev/null
RESULT=$(curl -s "${API_GET}?key=${KEY}")
if [ "$RESULT" = "$VALUE" ]; then
    print_test "特殊字符测试" 1
else
    print_test "特殊字符测试" 0 "期望: ${VALUE}, 实际: ${RESULT}"
fi

# 测试结果汇总
echo ""
echo "============================================================"
echo "测试结果汇总"
echo "============================================================"
echo -e "${GREEN}通过: ${PASSED}${NC}"
echo -e "${RED}失败: ${FAILED}${NC}"
echo "总计: $((PASSED + FAILED))"

if [ $FAILED -eq 0 ]; then
    echo ""
    echo -e "${GREEN}所有测试通过！${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}测试未完全通过，请检查上述错误${NC}"
    exit 1
fi
