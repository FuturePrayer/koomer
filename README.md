# Koomer SOCKS5 代理服务器

Koomer 是一个基于 Netty 实现的高性能 SOCKS5 代理服务器，支持 TCP 和 UDP 代理功能。

## 功能特性

- ✅ 完整的 SOCKS5 协议支持
- ✅ TCP CONNECT 命令代理
- ✅ UDP ASSOCIATE 命令代理（UDP转发）
- ✅ 基于 Netty 的高性能异步网络处理
- ✅ 支持 IPv4、IPv6 和域名地址类型
- ✅ 用户名/密码身份验证
- ✅ IP 小黑屋（认证失败自动封禁）
- ✅ 日志记录功能

## 技术栈

- Java25
- Netty4
- Picocli
- Logback
- Maven

## 项目结构

```
koomer/
├── koomer-core/     # 核心功能模块
│   └── handler/     # SOCKS5协议处理相关类
└── koomer-server/   # 服务启动模块
```

核心组件：

- `Socks5InitialRequestHandler`: 处理SOCKS5初始握手请求
- `Socks5PasswordAuthRequestHandler`: 处理SOCKS5密码认证请求
- `Socks5CommandRequestHandler`: 处理SOCKS5命令请求（CONNECT/UDP_ASSOCIATE）
- `Socks5UdpServerHandler`: 处理UDP数据转发
- `RelayHandler`: 在客户端和目标服务器之间转发数据
- `LoginAttemptService`: 登录尝试跟踪与IP封禁服务

## 快速开始

### 环境要求

- JDK 25 或更高版本
- Maven 3.6 或更高版本

### 构建项目

```bash
# 克隆项目
git clone <项目地址>

# 进入项目目录
cd koomer

# 编译和打包
mvn clean package
```

### 运行服务

1. 使用java命令直接启动

```bash
# 运行打包后的jar文件
java -jar koomer-server/target/koomer-server.jar [-l 0.0.0.0] [-p 10808]

# 启用身份验证及IP封禁策略
java -jar koomer-server/target/koomer-server.jar -a -u myusername -w changeit --max-attempts 5 --auth-window 300 --ban-duration 600

# 查看帮助
java -jar koomer-server/target/koomer-server.jar --help
```

| 参数             | 简写   | 参数示例       | 描述                                               |
|----------------|------|------------|--------------------------------------------------|
| --host         | -l   | 0.0.0.0    | 指定监听地址，默认绑定 `::0`（同时监听IPv4和IPv6）。                |
| --port         | -p   | 10808      | 监听端口，默认监听端口为 `10808`。                            |
| --enable-auth  | -a   | 无参数        | 启用身份验证功能，默认关闭。                                   |
| --username     | -u   | myusername | 身份验证用户名，默认为空，未启用身份验证功能时自动忽略。                     |
| --password     | -w   | changeit   | 身份验证密码，默认为空，未启用身份验证功能时自动忽略。                      |
| --max-attempts |      | 5          | 时间窗口内允许的最大认证失败次数，默认为 `5`。仅在开启身份验证时生效。           |
| --auth-window  |      | 300        | 统计认证失败次数的时间窗口（秒），默认为 `300`（5分钟）。仅在开启身份验证时生效。    |
| --ban-duration |      | 600        | 认证失败次数超限后的IP封禁时长（秒），默认为 `600`（10分钟）。仅在开启身份验证时生效。 |
| --help         | -h   |            | 显示帮助信息并退出。                                       |
| --version      | -V   |            | 显示版本信息并退出。                                       |

### IP 小黑屋功能

当启用身份验证（`-a`）时，系统会自动启用 IP 封禁策略：

- 在指定的时间窗口（`--auth-window`，默认5分钟）内，如果某个来源IP的认证失败次数达到阈值（`--max-attempts`，默认5次），该IP将被封禁。
- 被封禁的IP在封禁时长（`--ban-duration`，默认10分钟）内无法建立任何新连接，所有连接请求将被直接拒绝。
- 封禁到期后自动解除，失败计数重置。
- 认证成功会清除该IP之前的失败记录。

2. 使用docker启动

```bash
docker run -d --name koomer --network host --restart always swr.cn-east-3.myhuaweicloud.com/suhoan/koomer:latest
```

### 使用代理

配置您的应用程序或系统使用 SOCKS5 代理：

- 代理地址: localhost
- 代理端口: 10808

## 使用示例

启动服务后，您可以使用任何支持 SOCKS5 协议的客户端进行连接：

1. 浏览器代理设置
2. curl 命令:
   ```bash
   curl --socks5 localhost:10808 https://www.suhoan.cn
   ```


3. 其他支持 SOCKS5 的应用程序

## 开发说明

### 模块说明

- **koomer-core**: 包含 SOCKS5 协议的核心实现
- **koomer-server**: 服务启动和主应用程序入口

### 主要类说明

- `Socks5ProxyServer`: 主服务器类，负责启动 SOCKS5 代理服务
- `App`: 应用程序入口点，基于 Picocli 实现命令行参数解析
- `LoginAttemptService`: 登录尝试跟踪服务，负责统计失败次数和管理IP封禁
- 各种 Handler 类负责处理 SOCKS5 协议的不同阶段

## 许可证

[MIT](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目。