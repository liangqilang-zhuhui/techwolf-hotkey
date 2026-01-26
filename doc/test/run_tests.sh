#!/bin/bash
# 热Key功能测试套件
# 统一的测试运行脚本，整合所有测试功能

set -e

BASE_URL="http://localhost:8080"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="${SCRIPT_DIR}/hotkey_test.py"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

# 检查服务是否可用
check_service() {
    print_info "检查服务是否可用..."
    if ! curl -s -f "${BASE_URL}/api/hotkey/monitor/info" > /dev/null 2>&1; then
        print_error "无法连接到服务 ${BASE_URL}"
        echo ""
        echo "请确保服务已启动并运行在 8080 端口"
        echo ""
        echo "启动命令:"
        echo "  cd /Users/admin/kanzhun-ai-workspace/techwolf-hotkey-demo"
        echo "  mvn spring-boot:run"
        exit 1
    fi
    print_success "服务连接正常"
}

# 显示使用说明
show_usage() {
    echo "============================================================"
    echo "热Key功能测试套件"
    echo "============================================================"
    echo ""
    echo "使用方法:"
    echo "  $0 [选项]"
    echo ""
    echo "选项:"
    echo "  (无参数)        运行基础功能测试"
    echo "  --long-run      运行长时间运行测试（OOM验证）"
    echo "  --monitor       监控系统状态"
    echo "  --all           运行所有测试（基础测试 + 长时间测试）"
    echo "  --help          显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                    # 运行基础功能测试"
    echo "  $0 --long-run         # 运行30分钟长时间测试"
    echo "  $0 --long-run --duration 60  # 运行60分钟长时间测试"
    echo "  $0 --monitor          # 监控系统状态"
    echo ""
}

# 运行基础功能测试
run_basic_tests() {
    echo ""
    echo "============================================================"
    echo "1. 运行基础功能测试"
    echo "============================================================"
    echo ""
    
    python3 "${TEST_SCRIPT}"
    TEST_EXIT_CODE=$?
    
    if [ $TEST_EXIT_CODE -ne 0 ]; then
        echo ""
        print_warning "基础功能测试失败，退出码: $TEST_EXIT_CODE"
        echo "请检查测试结果"
        return $TEST_EXIT_CODE
    fi
    
    echo ""
    print_success "基础功能测试通过"
    return 0
}

# 运行长时间测试
run_long_running_test() {
    local duration=${1:-30}
    local interval=${2:-60}
    
    echo ""
    echo "============================================================"
    echo "2. 长时间运行测试（验证OOM问题）"
    echo "============================================================"
    echo ""
    echo "此测试将持续运行 ${duration} 分钟，用于验证系统在长时间运行下是否会出现OOM问题"
    echo "检查间隔: ${interval} 秒"
    echo ""
    echo "注意：测试将持续运行，可以随时按 Ctrl+C 中断"
    echo ""
    
    python3 "${TEST_SCRIPT}" --long-run --duration "${duration}" --interval "${interval}"
    LONG_TEST_EXIT_CODE=$?
    
    if [ $LONG_TEST_EXIT_CODE -eq 0 ]; then
        echo ""
        print_success "长时间运行测试完成"
    else
        echo ""
        print_warning "长时间运行测试异常退出，退出码: $LONG_TEST_EXIT_CODE"
    fi
    
    return $LONG_TEST_EXIT_CODE
}

# 监控系统状态
monitor_system() {
    echo ""
    echo "============================================================"
    echo "系统状态监控"
    echo "============================================================"
    echo ""
    
    python3 "${TEST_SCRIPT}" --monitor
}

# 主函数
main() {
    # 解析参数
    local run_basic=false
    local run_long=false
    local run_monitor=false
    local run_all=false
    local duration=30
    local interval=60
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --long-run)
                run_long=true
                shift
                ;;
            --monitor)
                run_monitor=true
                shift
                ;;
            --all)
                run_all=true
                shift
                ;;
            --duration)
                duration="$2"
                shift 2
                ;;
            --interval)
                interval="$2"
                shift 2
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                print_error "未知参数: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # 如果没有指定任何选项，默认运行基础测试
    if [ "$run_basic" = false ] && [ "$run_long" = false ] && [ "$run_monitor" = false ] && [ "$run_all" = false ]; then
        run_basic=true
    fi
    
    # 检查服务
    check_service
    echo ""
    
    # 运行监控
    if [ "$run_monitor" = true ]; then
        monitor_system
        exit 0
    fi
    
    # 运行基础测试
    if [ "$run_basic" = true ] || [ "$run_all" = true ]; then
        run_basic_tests
        BASIC_EXIT_CODE=$?
        
        if [ $BASIC_EXIT_CODE -ne 0 ] && [ "$run_all" = false ]; then
            exit $BASIC_EXIT_CODE
        fi
    fi
    
    # 运行长时间测试
    if [ "$run_long" = true ] || [ "$run_all" = true ]; then
        if [ "$run_all" = true ]; then
            echo ""
            read -p "是否运行长时间测试？(y/n, 默认n): " -t 10 RUN_LONG_TEST || RUN_LONG_TEST="n"
            if [ "$RUN_LONG_TEST" != "y" ] && [ "$RUN_LONG_TEST" != "Y" ]; then
                echo ""
                print_info "跳过长时间运行测试"
                echo "如需运行，请执行: $0 --long-run"
            else
                run_long_running_test "$duration" "$interval"
            fi
        else
            run_long_running_test "$duration" "$interval"
        fi
    fi
    
    echo ""
    echo "============================================================"
    echo "测试完成"
    echo "============================================================"
    echo ""
}

# 执行主函数
main "$@"
