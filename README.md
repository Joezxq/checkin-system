# 智慧课堂签到系统

基于 Spring Boot 的课堂签到管理系统，支持二维码扫码签到、批量学生管理、签到率统计等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| ORM | Spring Data JPA (Hibernate 5.6) |
| 数据库 | MySQL 8.0 |
| 安全 | BCrypt 密码加密 + HttpSession 认证 |
| 前端 | Vanilla JS + Bootstrap 5.3 (CDN) |
| 二维码 | qrcodejs (CDN) |
| 构建工具 | Maven |
| JDK | 17+ |

## 功能特性

### 教师端
- **课程管理** — 创建/编辑/删除课程，课程列表实时显示签到状态
- **学生管理** — 单个添加 + CSV 批量导入，按班级筛选，搜索防抖
- **选课管理** — 复选框批量勾选学生加入/移出课程
- **签到管理** — 发起签到（10分钟默认，可自定义），实时查看签到数据（3秒轮询）
- **二维码签到** — 自动生成 QR 码供学生扫码签到（格式：`CHECKIN:sessionId:qrToken`）
- **签到统计** — 关闭签到弹出签到率统计；历史记录显示每场签到率；课程维度统计含平均率
- **过期自动关闭** — 创建新签到时自动关闭已过期的活动

### 学生端
- **课程查看** — 课程卡片显示是否正在进行签到（绿色脉冲徽标）
- **手动签到** — 点击签到按钮，局域网校验
- **扫码签到** — 输入 QR 码文本或扫描教师端二维码签到（跳过局域网限制，手机可用）

### 通用
- 教师自助注册
- Session 登录认证 + 角色权限拦截
- 明亮现代 UI + 表格交错淡入动画

## 快速开始

### 1. 环境要求
- JDK 17+
- MySQL 8.0+
- Maven 3.6+

### 2. 创建数据库
```sql
CREATE DATABASE checkin_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'checkin'@'localhost' IDENTIFIED BY 'checkin123';
GRANT ALL PRIVILEGES ON checkin_system.* TO 'checkin'@'localhost';
FLUSH PRIVILEGES;
```

### 3. 导入表结构和种子数据
```bash
mysql -u checkin -pcheckin123 checkin_system < sql/schema.sql
mysql -u checkin -pcheckin123 checkin_system < sql/seed.sql

```

### 4. 启动应用
```bash
cd 759
mvn spring-boot:run
```

### 5. 访问
浏览器打开 `http://localhost:3000`

### 测试账号
| 角色 | 账号 | 密码 |
|------|------|------|
| 教师 | T001 | password |
| 学生 | 2021001 | password |

## 数据库配置

编辑 `759/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/checkin_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: checkin
    password: checkin123

server:
  port: 3000
```

局域网部署时修改 `server-ip-override`：
```yaml
checkin:
  network:
    server-ip-override: "192.168.x.x"  # 改为服务器实际 IP
```

## API 文档

### 认证 (无需登录)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/student/login` | 学生登录 |
| POST | `/api/auth/teacher/login` | 教师登录 |
| POST | `/api/auth/teacher/register` | 教师注册 |
| POST | `/api/auth/logout` | 登出 |

### 学生端 (需 STUDENT 角色)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/student/courses` | 我的课程 |
| GET | `/api/student/course/{id}/active-session` | 课程活跃签到 |
| GET | `/api/student/session/{id}/check` | 签到状态检查 |
| POST | `/api/student/session/{id}/sign` | 手动签到 |
| POST | `/api/student/session/{id}/qrsign` | 扫码签到 |

### 教师端 (需 TEACHER 角色)

**课程管理**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/teacher/courses` | 课程列表 |
| POST | `/api/teacher/courses` | 创建课程 |
| PUT | `/api/teacher/courses/{id}` | 更新课程 |
| DELETE | `/api/teacher/courses/{id}` | 删除课程（级联） |
| GET | `/api/teacher/course/{id}/active-session` | 活跃签到 |
| GET | `/api/teacher/courses/{id}/statistics` | 课程统计 |

**学生管理**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/teacher/students?page=&size=&keyword=&className=` | 分页查询 |
| POST | `/api/teacher/students` | 添加学生 |
| POST | `/api/teacher/students/batch` | CSV 批量导入 |
| GET | `/api/teacher/students/classes` | 班级列表 |
| PUT | `/api/teacher/students/{id}` | 更新学生 |
| DELETE | `/api/teacher/students/{id}` | 删除学生 |

**选课管理**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/teacher/course/{id}/students` | 已选学生 |
| POST | `/api/teacher/course/{id}/enroll` | 单个添加 |
| POST | `/api/teacher/course/{id}/enroll/batch` | 批量添加 |
| DELETE | `/api/teacher/course/{id}/enroll/{sid}` | 单个移除 |
| DELETE | `/api/teacher/course/{id}/enroll/batch` | 批量移除 |

**签到管理**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/teacher/course/{id}/sessions/open` | 发起签到 |
| POST | `/api/teacher/sessions/{id}/close` | 关闭签到（返回统计） |
| GET | `/api/teacher/sessions/{id}/realtime` | 实时数据（含 qrToken） |
| GET | `/api/teacher/sessions/{id}/statistics` | 签到统计 |
| GET | `/api/teacher/sessions/history` | 历史记录（含签到率） |

## 项目结构

```
759/
├── sql/
│   ├── schema.sql          # 表结构
│   ├── seed.sql             # 测试数据
├── src/main/java/com/checkin/
│   ├── CheckinSystemApplication.java
│   ├── config/              # Web/MVC/密码配置
│   ├── controller/          # REST 控制器
│   ├── dto/                 # 请求/响应 DTO
│   ├── entity/              # JPA 实体
│   ├── exception/           # 全局异常处理
│   ├── interceptor/         # 认证拦截器
│   ├── repository/          # JPA Repository
│   ├── service/             # 业务逻辑层
│   └── util/                # IP/设备/地理位置工具
├── src/main/resources/
│   ├── application.yml      # 应用配置
│   └── static/              # 前端静态资源
│       ├── index.html       # 首页
│       ├── common/          # 公共 CSS/JS
│       ├── student/         # 学生端页面
│       └── teacher/         # 教师端页面
└── pom.xml
```

## 数据库 ER 图

```
teacher ──1:N── course ──1:N── attendance_session ──1:N── attendance_record
                                   │
student ──1:N── enrollment ──N:1──┘                └── N:1 ── student
```

## License

MIT
