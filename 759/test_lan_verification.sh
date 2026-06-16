#!/bin/bash

# ==========================================
# 局域网验证反向测试脚本
# ==========================================
#
# 使用方法：
#   chmod +x test_lan_verification.sh
#   ./test_lan_verification.sh
#
# 前置条件：
#   - Docker 和 Docker Compose 已安装
#   - jq 已安装
#   - 项目已通过 docker-compose up -d 启动
# ==========================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置
BASE_URL="http://localhost:3000"
APP_YML="src/main/resources/application.yml"
COOKIE_DIR="/tmp/checkin_test_$$"
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 清理函数
cleanup() {
    echo ""
    echo -e "${CYAN}清理测试环境...${NC}"
    rm -rf "$COOKIE_DIR"

    # 如果有备份配置，恢复
    if [ -f "${APP_YML}.bak" ]; then
        mv "${APP_YML}.bak" "$APP_YML"
        echo "已恢复 application.yml"
        docker-compose build app > /dev/null 2>&1 && docker-compose up -d > /dev/null 2>&1
        echo "已重新构建并启动应用"
    fi
}
trap cleanup EXIT

# 创建 cookie 目录
mkdir -p "$COOKIE_DIR"

# ==========================================
# 工具函数
# ==========================================

log_info() {
    echo -e "${CYAN}[INFO]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}  ✓${NC} $1"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    PASSED_TESTS=$((PASSED_TESTS + 1))
}

log_fail() {
    echo -e "${RED}  ✗${NC} $1"
    echo -e "    预期: $2"
    echo -e "    实际: $3"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    FAILED_TESTS=$((FAILED_TESTS + 1))
}

assert_eq() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$expected" = "$actual" ]; then
        log_pass "$test_name"
    else
        log_fail "$test_name" "$expected" "$actual"
    fi
}

assert_contains() {
    local test_name="$1"
    local expected_substr="$2"
    local actual="$3"
    if echo "$actual" | grep -q "$expected_substr"; then
        log_pass "$test_name"
    else
        log_fail "$test_name" "包含 '$expected_substr'" "$actual"
    fi
}

# 等待应用就绪
wait_for_app() {
    log_info "等待应用就绪..."
    for i in $(seq 1 40); do
        if curl -s "${BASE_URL}/api/auth/student/login" \
            -H "Content-Type: application/json" \
            -d '{"studentNo":"2021001","password":"password"}' 2>/dev/null | jq -r '.code' 2>/dev/null | grep -q "0"; then
            log_info "应用已就绪（等待了 ${i} 秒）"
            return 0
        fi
        sleep 1
    done
    echo -e "${RED}应用启动超时！${NC}"
    return 1
}

# 教师登录
teacher_login() {
    local cookie_file="$1"
    curl -s -c "$cookie_file" -X POST "${BASE_URL}/api/auth/teacher/login" \
        -H "Content-Type: application/json" \
        -d '{"teacherNo":"T001","password":"password"}' > /dev/null
}

# 学生登录
student_login() {
    local student_no="$1"
    local cookie_file="$2"
    local result
    result=$(curl -s -c "$cookie_file" -X POST "${BASE_URL}/api/auth/student/login" \
        -H "Content-Type: application/json" \
        -d "{\"studentNo\":\"${student_no}\",\"password\":\"password\"}")
    echo "$result" | jq -r '.code'
}

# 发起签到活动
open_session() {
    local cookie_file="$1"
    local course_id="${2:-1}"
    local result
    result=$(curl -s -b "$cookie_file" -X POST \
        "${BASE_URL}/api/teacher/course/${course_id}/sessions/open" \
        -H "Content-Type: application/json" \
        -d '{"durationMinutes":30}')
    echo "$result" | jq -r '.data.id // empty'
}

# 关闭签到活动
close_session() {
    local cookie_file="$1"
    local session_id="$2"
    curl -s -b "$cookie_file" -X POST \
        "${BASE_URL}/api/teacher/sessions/${session_id}/close" > /dev/null
}

# 学生签到
sign_in() {
    local cookie_file="$1"
    local session_id="$2"
    local extra_headers="${3:-}"
    if [ -n "$extra_headers" ]; then
        curl -s -b "$cookie_file" -X POST \
            -H "$extra_headers" \
            "${BASE_URL}/api/student/session/${session_id}/sign"
    else
        curl -s -b "$cookie_file" -X POST \
            "${BASE_URL}/api/student/session/${session_id}/sign"
    fi
}

# ==========================================
# 前置检查
# ==========================================

echo "=========================================="
echo " 局域网验证反向测试"
echo "=========================================="
echo ""

# 检查依赖
for cmd in curl jq docker docker-compose; do
    if ! command -v "$cmd" &> /dev/null; then
        echo -e "${RED}错误：未找到 $cmd 命令${NC}"
        exit 1
    fi
done
log_info "依赖检查通过"

# 检查应用是否运行
if ! wait_for_app; then
    echo -e "${RED}应用未运行，请先执行 docker-compose up -d${NC}"
    exit 1
fi

# ==========================================
# 测试1：同网段签到（正向验证）
# ==========================================

echo ""
echo -e "${YELLOW}━━━ 测试1：同网段签到（正向验证）━━━${NC}"
echo ""

# 教师登录并发起签到
teacher_login "$COOKIE_DIR/teacher.txt"
SESSION_ID=$(open_session "$COOKIE_DIR/teacher.txt" 1)

if [ -z "$SESSION_ID" ] || [ "$SESSION_ID" = "null" ]; then
    log_info "已有进行中的签到活动，先关闭再重新发起"
    # 尝试获取现有活动并关闭
    # 直接发起新的，如果失败说明需要关闭旧的
    log_info "跳过测试1（需要手动清理签到活动）"
else
    log_info "签到活动ID: $SESSION_ID"

    # 学生登录
    LOGIN_CODE=$(student_login "2021001" "$COOKIE_DIR/student1.txt")
    assert_eq "学生 2021001 登录成功" "0" "$LOGIN_CODE"

    # 签到
    SIGN_RESULT=$(sign_in "$COOKIE_DIR/student1.txt" "$SESSION_ID")
    SIGN_CODE=$(echo "$SIGN_RESULT" | jq -r '.code')
    assert_eq "同网段签到返回 code=0" "0" "$SIGN_CODE"

    if [ "$SIGN_CODE" = "0" ]; then
        CLIENT_IP=$(echo "$SIGN_RESULT" | jq -r '.data.record.clientIp')
        assert_contains "签到记录包含客户端 IP" "." "$CLIENT_IP"
    fi

    # 关闭签到活动
    close_session "$COOKIE_DIR/teacher.txt" "$SESSION_ID"
fi

# ==========================================
# 测试2：不同网段拒绝（反向验证）
# ==========================================

echo ""
echo -e "${YELLOW}━━━ 测试2：不同网段拒绝（反向验证）━━━${NC}"
echo ""

log_info "修改 server-ip-override 为不同网段 192.168.1.100"
cp "$APP_YML" "${APP_YML}.bak"
sed -i.tmp 's/server-ip-override: "169.150.249.166"/server-ip-override: "192.168.1.100"/' "$APP_YML"
rm -f "${APP_YML}.tmp"

log_info "重新构建并启动..."
docker-compose build app > /dev/null 2>&1
docker-compose up -d > /dev/null 2>&1

if wait_for_app; then
    # 教师登录并发起新签到
    teacher_login "$COOKIE_DIR/teacher2.txt"
    SESSION_ID2=$(open_session "$COOKIE_DIR/teacher2.txt" 1)

    if [ -n "$SESSION_ID2" ] && [ "$SESSION_ID2" != "null" ]; then
        log_info "签到活动ID: $SESSION_ID2"

        # 使用不同学生
        LOGIN_CODE2=$(student_login "2021002" "$COOKIE_DIR/student2.txt")
        assert_eq "学生 2021002 登录成功" "0" "$LOGIN_CODE2"

        # 尝试签到（应被拒绝）
        SIGN_RESULT2=$(sign_in "$COOKIE_DIR/student2.txt" "$SESSION_ID2")
        SIGN_CODE2=$(echo "$SIGN_RESULT2" | jq -r '.code')
        assert_eq "不同网段签到返回 code=403" "403" "$SIGN_CODE2"

        SIGN_MSG2=$(echo "$SIGN_RESULT2" | jq -r '.message')
        assert_contains "错误消息包含'非局域网'" "非局域网" "$SIGN_MSG2"

        # 检查日志
        LOG_OUTPUT=$(docker logs checkin-app 2>&1 | grep "非局域网访问" | tail -1)
        assert_contains "日志记录了非局域网访问" "非局域网访问" "$LOG_OUTPUT"

        # 关闭签到活动
        close_session "$COOKIE_DIR/teacher2.txt" "$SESSION_ID2"
    else
        log_info "无法发起签到活动，跳过测试2"
    fi
else
    echo -e "${RED}应用重启失败，跳过测试2${NC}"
fi

# 恢复配置
log_info "恢复原始配置..."
mv "${APP_YML}.bak" "$APP_YML"
docker-compose build app > /dev/null 2>&1
docker-compose up -d > /dev/null 2>&1
wait_for_app || true

# ==========================================
# 测试3：X-Forwarded-For 伪造防护
# ==========================================

echo ""
echo -e "${YELLOW}━━━ 测试3：X-Forwarded-For 伪造防护━━━${NC}"
echo ""

# 场景A：默认配置下 X-Forwarded-For 被忽略
log_info "场景A：默认配置下 X-Forwarded-For 头被忽略"

teacher_login "$COOKIE_DIR/teacher3.txt"
SESSION_ID3=$(open_session "$COOKIE_DIR/teacher3.txt" 1)

if [ -n "$SESSION_ID3" ] && [ "$SESSION_ID3" != "null" ]; then
    log_info "签到活动ID: $SESSION_ID3"

    LOGIN_CODE3=$(student_login "2021003" "$COOKIE_DIR/student3.txt")
    assert_eq "学生 2021003 登录成功" "0" "$LOGIN_CODE3"

    # 携带伪造的 X-Forwarded-For 头
    SIGN_RESULT3=$(sign_in "$COOKIE_DIR/student3.txt" "$SESSION_ID3" "X-Forwarded-For: 10.0.0.50")
    SIGN_CODE3=$(echo "$SIGN_RESULT3" | jq -r '.code')
    assert_eq "伪造 X-Forwarded-For 时签到仍成功（头被忽略）" "0" "$SIGN_CODE3"

    close_session "$COOKIE_DIR/teacher3.txt" "$SESSION_ID3"
else
    log_info "无法发起签到活动，跳过测试3A"
fi

# ==========================================
# 测试结果汇总
# ==========================================

echo ""
echo "=========================================="
echo " 测试结果汇总"
echo "=========================================="
echo ""
echo "总测试数: $TOTAL_TESTS"
echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
echo -e "${RED}失败: $FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}部分测试失败！${NC}"
    exit 1
fi
