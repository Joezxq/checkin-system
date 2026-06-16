# 签到系统

一个基于 Spring Boot + MySQL 的签到系统，支持学生签到和教师管理功能。

## 原始需求

> 使用maven、spring-boot基于java为后端语言做一个签到系统，使用mysql数据库，前端使用html、css、js。
> 前端功能要求：
> 学生端：实现PC端登录界面，支持账号（学生学号）密码登录
> 学生端：实现课程基本信息展示页面
> 学生端：通过PC端按钮模拟扫码签到功能（点击按钮即视为扫码成功）
> 学生端：实现简单的签到结果页面
> 教师端：实现课程管理界面，支持课程增删改查
> 教师端：实现学生管理界面，支持学生信息维护
> 教师端：实时显示当前签到数据
> 教师端：显示签到历史数据
> 后端功能要求：
> 基于Maven的SSM或者Spring Boot框架项目
> 实现用户认证模块（学生/教师登录验证）
> 实现课程管理相关业务逻辑（增删改查）
> 实现学生管理相关业务逻辑（增删改查）
> 实现签到记录存储和查询务逻辑
> 实现简单的数据验证和异常处理
> 模拟地理位置数据存储（可固定坐标或随机生成）
> 强制实现IP地址获取和局域网验证
> 强制实现同一课程单设备限制
> 获取ip时要确保获取的是本地机器的ip地址而不是环回地址127.0.0.1
> 局域网验证技术要求
> 基础级、进阶级局域网验证：
> 获取客户端真实IP地址（处理代理情况）
> 比较客户端IP与服务器IP是否在同一网段
> 实现简单的IP地址格式验证
> 基于IP地址实现单设备检测
> 提供清晰的错误提示（非局域网访问提示）

## 技术栈

- **后端**: Java 17 + Spring Boot 2.7.18 + Maven
- **数据库**: MySQL 8.0
- **前端**: HTML + CSS + JavaScript（原生）
- **部署**: Docker + Docker Compose

## 功能特性

### 学生端

- 学生登录
- 查看已选课程
- 课程签到（模拟扫码）
- 局域网验证
- 单设备限制

### 教师端

- 教师登录
- 课程管理（CRUD）
- 学生管理（CRUD）
- 选课管理
- 发起签到活动
- 实时签到统计（每3秒刷新）
- 历史签到记录查询

## 快速开始

### 1. 使用 Docker Compose 启动（推荐）

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 2. 本地开发启动

#### 前置条件

- Java 17
- Maven 3.6+
- MySQL 8.0

#### 启动步骤

1. 创建数据库

```sql
CREATE DATABASE checkin_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行数据库脚本

```bash
mysql -u root -p checkin_system < sql/schema.sql
mysql -u root -p checkin_system < sql/seed.sql
```

3. 修改配置文件
   编辑 `src/main/resources/application.yml`，修改数据库连接信息。

4. 启动应用

```bash
mvn spring-boot:run
```

## 访问地址

- 首页: http://localhost:3000
- 学生登录: http://localhost:3000/student/login.html
- 教师登录: http://localhost:3000/teacher/login.html

## 测试账号

### 教师账号

- 工号: T001, T002, T003, T004, T005
- 密码: password

### 学生账号

- 学号: 2021001 ~ 2021030
- 密码: password

## 项目结构

```
button-checkin/
├── docker-compose.yml          # Docker Compose 配置
├── Dockerfile                  # Docker 镜像构建文件
├── pom.xml                     # Maven 配置
├── sql/                        # 数据库脚本
│   ├── schema.sql             # 表结构
│   └── seed.sql               # 测试数据
└── src/
    └── main/
        ├── java/com/checkin/
        │   ├── controller/    # 控制器层
        │   ├── service/       # 业务逻辑层
        │   ├── repository/    # 数据访问层
        │   ├── entity/        # 实体类
        │   ├── dto/           # 数据传输对象
        │   ├── config/        # 配置类
        │   ├── util/          # 工具类
        │   ├── exception/     # 异常处理
        │   └── interceptor/   # 拦截器
        └── resources/
            ├── application.yml # 应用配置
            └── static/         # 前端页面
                ├── common/     # 通用资源
                ├── student/    # 学生端页面
                └── teacher/    # 教师端页面
```

## API 接口

### 认证接口

- POST `/api/auth/student/login` - 学生登录
- POST `/api/auth/teacher/login` - 教师登录
- POST `/api/auth/logout` - 登出

### 学生接口

- GET `/api/student/courses` - 获取学生课程列表
- GET `/api/student/course/{courseId}/active-session` - 获取活动签到活动
- POST `/api/student/session/{sessionId}/sign` - 学生签到

### 教师接口

- GET `/api/teacher/courses` - 获取教师课程列表
- POST `/api/teacher/courses` - 创建课程
- PUT `/api/teacher/courses/{id}` - 更新课程
- DELETE `/api/teacher/courses/{id}` - 删除课程
- GET `/api/teacher/students` - 分页查询学生
- POST `/api/teacher/students` - 创建学生
- PUT `/api/teacher/students/{id}` - 更新学生
- DELETE `/api/teacher/students/{id}` - 删除学生
- POST `/api/teacher/course/{courseId}/enroll` - 学生加入课程
- DELETE `/api/teacher/course/{courseId}/enroll/{studentId}` - 移除学生
- POST `/api/teacher/course/{courseId}/sessions/open` - 发起签到活动
- POST `/api/teacher/sessions/{sessionId}/close` - 关闭签到活动
- GET `/api/teacher/sessions/{sessionId}/realtime` - 获取实时签到数据
- GET `/api/teacher/sessions/history` - 获取历史签到记录

## 核心功能说明

### 局域网验证

- 默认使用 /24 子网掩码（255.255.255.0）
- 可在 `application.yml` 中配置子网掩码
- 支持 X-Forwarded-For 头解析（需配置可信代理）

### 单设备限制

- 基于 IP + Cookie（设备ID）实现
- 同一签到活动，同一设备只能签到一次
- Cookie 有效期 1 年

### 时间格式

- 统一使用 `yyyy-MM-dd HH:mm:ss` 格式
- 时区：Asia/Shanghai (GMT+8)

### 错误处理

- 所有业务接口返回 HTTP 200
- 响应体包含自定义错误码和中文错误信息
- 前端统一处理错误提示

## 注意事项

1. **局域网测试**: 确保客户端和服务器在同一网段
2. **Docker 网络**: Docker 容器内的 IP 可能与宿主机不同，需要配置 `server-ip-override`
3. **密码安全**: 所有密码使用 BCrypt 加密存储
4. **Session 管理**: 默认 Session 超时时间 30 分钟

## 代码架构

### 整体架构

本项目采用经典的三层架构模式：

```
┌─────────────────────────────────────────────────────────┐
│                     前端层 (Frontend)                    │
│  HTML + CSS + JavaScript (原生)                         │
│  - 学生端页面 (student/)                                 │
│  - 教师端页面 (teacher/)                                 │
│  - 通用组件 (common/)                                    │
└─────────────────────────────────────────────────────────┘
                          ↓ HTTP/REST
┌─────────────────────────────────────────────────────────┐
│                   控制器层 (Controller)                  │
│  - AuthController: 认证相关                              │
│  - StudentController: 学生功能                           │
│  - TeacherController: 教师功能                           │
│  - 统一异常处理 (GlobalExceptionHandler)                │
│  - 拦截器 (AuthInterceptor)                             │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   业务逻辑层 (Service)                   │
│  - AuthService: 认证服务                                 │
│  - StudentService: 学生管理                              │
│  - CourseService: 课程管理                               │
│  - EnrollmentService: 选课管理                           │
│  - AttendanceService: 签到管理                           │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  数据访问层 (Repository)                 │
│  Spring Data JPA                                        │
│  - StudentRepository                                    │
│  - TeacherRepository                                    │
│  - CourseRepository                                     │
│  - EnrollmentRepository                                 │
│  - AttendanceSessionRepository                          │
│  - AttendanceRecordRepository                           │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    数据库层 (Database)                   │
│  MySQL 8.0                                              │
│  - 6张核心表                                             │
│  - UTF-8编码                                             │
│  - InnoDB引擎                                            │
└─────────────────────────────────────────────────────────┘
```

### 核心模块说明

#### 1. 实体层 (Entity)

**核心实体类**：
- `Student`: 学生实体，包含学号、姓名、班级等信息
- `Teacher`: 教师实体，包含工号、姓名等信息
- `Course`: 课程实体，关联教师
- `Enrollment`: 选课关系，多对多关联学生和课程
- `AttendanceSession`: 签到活动，包含开始时间、结束时间、状态
- `AttendanceRecord`: 签到记录，记录学生签到详情

**实体关系**：
```
Teacher 1 ──────── * Course
                      │
                      │ *
                      │
                      * Enrollment * ──────── Student
                      │
                      │ 1
                      │
                      * AttendanceSession
                      │
                      │ 1
                      │
                      * AttendanceRecord * ──────── Student
```

#### 2. 数据传输对象 (DTO)

- `Result<T>`: 统一响应格式，包含code、message、data
- `StudentRequest`: 创建学生请求
- `UpdateStudentRequest`: 更新学生请求
- `CourseRequest`: 课程请求
- `OpenSessionRequest`: 开启签到活动请求
- `StudentLoginRequest`: 学生登录请求
- `TeacherLoginRequest`: 教师登录请求

#### 3. 工具类 (Util)

- `IPUtil`: IP地址处理工具
  - 解析客户端真实IP
  - 局域网验证
  - 子网掩码计算
- `DeviceUtil`: 设备管理工具
  - 生成设备ID
  - Cookie管理
- `GeoUtil`: 地理位置工具（预留）

#### 4. 配置类 (Config)

- `WebConfig`: Web配置
  - 拦截器注册
  - CORS配置
  - 静态资源映射
- `JacksonConfig`: JSON序列化配置
  - 时间格式化
  - 时区设置
  - 空值处理
- `PasswordConfig`: 密码加密配置
  - BCrypt加密器

#### 5. 异常处理 (Exception)

- `BusinessException`: 业务异常，包含错误码和消息
- `GlobalExceptionHandler`: 全局异常处理器
  - 业务异常处理
  - 参数校验异常处理
  - IllegalArgumentException处理
  - NullPointerException处理
  - 通用异常处理

### 数据库设计

#### 表结构

**1. students (学生表)**
```sql
- id: BIGINT (主键)
- student_no: VARCHAR(20) (学号，唯一)
- name: VARCHAR(50) (姓名)
- password: VARCHAR(100) (密码，BCrypt加密)
- class_name: VARCHAR(50) (班级)
- created_at: DATETIME (创建时间)
```

**2. teachers (教师表)**
```sql
- id: BIGINT (主键)
- teacher_no: VARCHAR(20) (工号，唯一)
- name: VARCHAR(50) (姓名)
- password: VARCHAR(100) (密码，BCrypt加密)
- created_at: DATETIME (创建时间)
```

**3. courses (课程表)**
```sql
- id: BIGINT (主键)
- course_name: VARCHAR(100) (课程名称)
- teacher_id: BIGINT (教师ID，外键)
- created_at: DATETIME (创建时间)
```

**4. enrollments (选课表)**
```sql
- id: BIGINT (主键)
- course_id: BIGINT (课程ID，外键)
- student_id: BIGINT (学生ID，外键)
- enrolled_at: DATETIME (选课时间)
- UNIQUE(course_id, student_id) (联合唯一索引)
```

**5. attendance_sessions (签到活动表)**
```sql
- id: BIGINT (主键)
- course_id: BIGINT (课程ID，外键)
- start_time: DATETIME (开始时间)
- end_time: DATETIME (结束时间，可为空)
- status: VARCHAR(20) (状态: OPEN/CLOSED)
- created_at: DATETIME (创建时间)
```

**6. attendance_records (签到记录表)**
```sql
- id: BIGINT (主键)
- session_id: BIGINT (签到活动ID，外键)
- student_id: BIGINT (学生ID，外键)
- sign_time: DATETIME (签到时间)
- client_ip: VARCHAR(50) (客户端IP)
- device_id: VARCHAR(100) (设备ID)
- UNIQUE(session_id, student_id) (联合唯一索引)
- UNIQUE(session_id, device_id) (联合唯一索引)
```

#### 索引设计

- 主键索引：所有表的id字段
- 唯一索引：
  - students.student_no
  - teachers.teacher_no
  - enrollments(course_id, student_id)
  - attendance_records(session_id, student_id)
  - attendance_records(session_id, device_id)
- 外键索引：
  - courses.teacher_id
  - enrollments.course_id, student_id
  - attendance_sessions.course_id
  - attendance_records.session_id, student_id

### 技术细节

#### 1. 认证与授权

**Session管理**：
- 使用Spring Session + Cookie
- Session超时时间：30分钟（可配置）
- Cookie名称：CHECKIN_SESSION
- 拦截器验证：AuthInterceptor

**权限控制**：
- 学生只能访问自己的数据
- 教师只能管理自己的课程
- 跨教师数据访问被阻止

**密码安全**：
- BCrypt加密（强度10）
- 密码字段使用@JsonIgnore，不返回给前端
- 登录失败不泄露具体原因

#### 2. 局域网验证

**实现原理**：
```java
// 1. 获取客户端IP
String clientIp = IPUtil.getClientIp(request);

// 2. 获取服务器IP
String serverIp = IPUtil.getServerIp();

// 3. 验证是否在同一网段
boolean inSameNetwork = IPUtil.isInSameNetwork(
    clientIp, serverIp, subnetMask
);
```

**配置项**：
- `checkin.network.subnet-mask`: 子网掩码（默认255.255.255.0）
- `checkin.network.server-ip-override`: 手动指定服务器IP
- `checkin.network.enable-forwarded-header`: 是否启用X-Forwarded-For
- `checkin.network.trusted-proxies`: 可信代理列表

**IP解析优先级**：
1. X-Forwarded-For（需启用且来自可信代理）
2. X-Real-IP
3. request.getRemoteAddr()

#### 3. 单设备限制

**实现机制**：
- 设备ID存储在Cookie中（CHECKIN_DEVICE_ID）
- Cookie有效期：1年
- 数据库唯一约束：(session_id, device_id)

**防作弊措施**：
- 同一设备只能签到一次
- 同一学生只能签到一次
- 签到记录包含IP和设备ID，可追溯

#### 4. 实时数据刷新

**前端轮询**：
```javascript
// 每3秒刷新一次
setInterval(() => {
    loadRealtimeData();
}, 3000);
```

**后端优化**：
- 使用JPA的@Query优化查询
- 分别查询已签到和未签到学生
- 返回统计数据（总数、已签到数、未签到数）

#### 5. 分页查询

**实现方式**：
```java
Pageable pageable = PageRequest.of(page, size);
Page<Student> studentPage = studentRepository.findAll(pageable);
```

**参数校验**：
- page < 0 → 自动修正为0
- size <= 0 → 自动修正为10
- size > 100 → 自动限制为100

#### 6. XSS防护

**前端防护**：
```javascript
// HTML转义函数
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 使用
innerHTML = `<td>${escapeHtml(student.name)}</td>`;
```

**后端防护**：
- 使用@JsonIgnore防止敏感字段泄露
- 参数校验使用@Valid和@NotBlank
- 统一异常处理，不泄露堆栈信息

#### 7. 时间处理

**统一格式**：
- 格式：yyyy-MM-dd HH:mm:ss
- 时区：Asia/Shanghai (GMT+8)

**Jackson配置**：
```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    serialization:
      write-dates-as-timestamps: false
```

**前端格式化**：
```javascript
function formatDateTime(dateTimeStr) {
    // 支持ISO格式和标准格式
    // 统一转换为 yyyy-MM-dd HH:mm:ss
}
```

#### 8. 日志管理

**日志级别**：
- 开发环境：DEBUG
- 生产环境：INFO

**日志配置**：
```yaml
logging:
  level:
    com.checkin: INFO
    org.hibernate.SQL: WARN
  file:
    name: logs/checkin-system.log
```

**日志内容**：
- 业务异常：WARN级别
- 系统异常：ERROR级别，包含堆栈
- 参数校验失败：WARN级别

#### 9. 数据库连接池

**HikariCP配置**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

**优化建议**：
- 生产环境建议maximum-pool-size: 20
- 根据并发量调整连接池大小
- 监控连接池使用情况

#### 10. 性能优化

**已实施的优化**：
- 使用索引加速查询
- 分页查询避免全表扫描
- 使用@Query自定义查询，避免N+1问题
- 关闭生产环境的SQL日志
- 使用连接池复用连接

**可进一步优化**：
- 添加Redis缓存（课程列表、学生列表）
- 使用WebSocket替代轮询（实时签到数据）
- 数据库读写分离
- 静态资源CDN加速

### 安全特性

#### 已实施的安全措施

1. **XSS防护**：所有用户输入转义
2. **SQL注入防护**：使用JPA参数化查询
3. **密码安全**：BCrypt加密存储
4. **Session安全**：超时控制、Cookie安全
5. **权限控制**：拦截器验证、数据隔离
6. **参数校验**：所有输入参数校验
7. **错误处理**：不泄露敏感信息

#### 建议添加的安全措施

1. **CSRF防护**：添加CSRF Token
2. **HTTPS**：生产环境强制HTTPS
3. **CSP**：Content Security Policy头
4. **Rate Limiting**：API请求频率限制
5. **审计日志**：记录敏感操作

## 开发说明

### 添加新功能

1. 在 `entity` 包中创建实体类
2. 在 `repository` 包中创建 Repository 接口
3. 在 `service` 包中实现业务逻辑
4. 在 `controller` 包中创建 REST API
5. 在 `static` 目录中创建前端页面

### 数据库迁移

修改 `sql/schema.sql` 和 `sql/seed.sql`，重新构建 Docker 镜像。

### 代码规范

- 使用Lombok简化代码（@Data, @Slf4j等）
- 统一使用Result<T>包装响应
- 异常使用BusinessException，包含错误码
- 所有时间使用LocalDateTime
- 所有字符串使用UTF-8编码

### 测试建议

1. **单元测试**：使用JUnit 5 + Mockito
2. **集成测试**：使用@SpringBootTest
3. **API测试**：使用Postman或cURL
4. **性能测试**：使用JMeter或Gatling
5. **安全测试**：使用OWASP ZAP

## 文档

- [API文档](API_DOCUMENTATION.md) - 完整的API接口文档
- [安全审计报告](FINAL_SECURITY_AUDIT.md) - 安全审计和修复记录
- [改进总结](IMPROVEMENTS_SUMMARY.md) - 最新改进内容

## 许可证

MIT License
