# kt-gui

KtConnect 的图形化界面(主要用于本地联调)

## 📖 项目简介

kt-gui 是 [KtConnect](https://github.com/alibaba/kt-connect) 的桌面图形化客户端，旨在降低 Kubernetes 本地开发调试工具的使用门槛。通过直观的图形界面，开发者可以更便捷地使用 KtConnect 的核心能力，无需记忆复杂的命令行参数。

> KtConnect 是阿里开源的一款 Kubernetes 开发测试工具，可实现本地服务与集群之间的互联互通。

> **注意**：使用本工具前请确保已安装并配置好 [KtConnect](https://github.com/alibaba/kt-connect) 环境。

## ✨ 功能特性

- 🖥️ **图形化操作界面** —— 告别命令行，通过可视化界面完成 KtConnect 的配置与操作
- 🚀 **核心功能覆盖** —— 支持 KtConnect 的主要能力（如 Connect、Mesh 等）
- 🔧 **一键连接** —— 简化 Kubernetes 集群连接配置流程
- 📦 **开箱即用** —— 提供 Windows 可执行程序

## 🛠️ 技术栈

- **语言**：Jav
  a- **构建工具**：Maven
- **打包工具**：Launch4j（生成 Windows exe 可执行文件）
- **安装包**：NSIS（生成 Windows 安装程序）

## 🚀 构建与运行

### 环境要求

- JDK 17 或更高版本
- Maven 3.6+

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/devinx3/kt-gui.git
cd kt-gui

# 使用 Maven 构建
mvn clean package
```

### 运行方式

**方式一：通过 Maven 运行**

```bash
mvn javafx:run
```

**方式二：可执行镜像**  
`mvn clean package javafx:jlink jpackage:jpackage`  
构建完成后，在 `target/app/kt-gui` 目录下会生成可执行文件。

**方式二：可执行文件**  
1. 修改 文件中 `pom.xml` build 配置标签 `<type>APP_IMAGE</type>`
- Windows: `EXE` / `MSI`
- Mac: `DMG` / `PKG`  
- Linux: `DEB`/ `RPM`  

2. `mvn clean package javafx:jlink jpackage:jpackage`  
构建完成后，在 `target/app/kt-gui` 目录下会生成可执行文件。

## 📝 使用说明

1. 启动 kt-gui 后，配置 Kubernetes 集群连接信息
2. 选择需要使用的 KtConnect 功能(Connect / Clean)
3. 在服务界面新增服务列表和本地流量分发 (Mesh / Recover)