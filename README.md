# Koomer SOCKS5 代理服务器

Koomer 是一个基于 Netty 实现的高性能 SOCKS5 代理服务器，支持 TCP 和 UDP 代理功能。

## 功能特性

- ✅ 完整的 SOCKS5 协议支持
- ✅ TCP CONNECT 命令代理
- ✅ UDP ASSOCIATE 命令代理（UDP转发）
- ✅ 基于 Netty 的高性能异步网络处理
- ✅ 支持 IPv4、IPv6 和域名地址类型
- ✅ 日志记录功能

## 技术栈

- Java25
- Netty4
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

- [Socks5InitialRequestHandler](file://D:\dev\git_repo\新建文件夹\koomer\koomer-core\src\main\java\cn\suhoan\koomer\handler\Socks5InitialRequestHandler.java#L13-L26):
  处理SOCKS5初始握手请求
- [Socks5CommandRequestHandler](file://D:\dev\git_repo\新建文件夹\koomer\koomer-core\src\main\java\cn\suhoan\koomer\handler\Socks5CommandRequestHandler.java#L16-L145):
  处理SOCKS5命令请求（CONNECT/UDP_ASSOCIATE）
- [Socks5UdpServerHandler](file://D:\dev\git_repo\新建文件夹\koomer\koomer-core\src\main\java\cn\suhoan\koomer\handler\Socks5UdpServerHandler.java#L21-L182):
  处理UDP数据转发
- [RelayHandler](file://D:\dev\git_repo\新建文件夹\koomer\koomer-core\src\main\java\cn\suhoan\koomer\handler\RelayHandler.java#L13-L53):
  在客户端和目标服务器之间转发数据

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
```

| 参数            | 简写 | 参数示例       | 描述                            |
|---------------|----|------------|-------------------------------|
| --host        | -l | 0.0.0.0    | 指定监听地址，默认绑定::0（同时监听ipv4和ipv6） |
| --port        | -p | 10808      | 监听端口，默认监听端口为 10808。           |
| --enable-auth | -a | 无参数        | 启用身份验证功能，默认关闭。                |
| --username    | -u | myusername | 身份验证用户名，默认为空，未启用身份验证功能时自动忽略。  |
| --password    | -w | changit    | 身份验证密码，默认为空，未启用身份验证功能时自动忽略。   |

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

- [Socks5ProxyServer](file://D:\dev\git_repo\新建文件夹\koomer\koomer-server\src\main\java\cn\suhoan\koomer\Socks5ProxyServer.java#L22-L67):
  主服务器类，负责启动 SOCKS5 代理服务
- [App](file://D:\dev\git_repo\新建文件夹\koomer\koomer-server\src\main\java\cn\suhoan\koomer\App.java#L9-L37): 应用程序入口点
- 各种 Handler 类负责处理 SOCKS5 协议的不同阶段

## 许可证

[MIT](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目。