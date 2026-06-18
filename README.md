# Tweak

<div align="center">
  <img src="androidApp/src/main/res/mipmap-xxhdpi/ic_logo.png" alt="Tweak Logo" width="120"/>
  <h3>Android 玩机工具箱</h3>
  <p>强大的 Android 系统调优与监控工具</p>
</div>

## ✨ 功能特性

- 🔋 **系统监控**
  - 实时 CPU 状态监控（负载、温度）
  - 内存状态监控（物理内存、交换分区）
  - GPU 状态监控（负载、显存使用）

- 🛠️ **系统调优**
  - ROOT 权限支持
  - 系统属性修改
  - 原生文件系统操作
  - SoC 信息识别

- 🎨 **现代化界面**
  - 基于 Compose Multiplatform 构建
  - MiuiX 设计语言
  - 流体玻璃效果（Liquid Glass）
  - 响应式布局

## 📋 系统要求

- **最低 Android 版本**: Android 7.0 (API 24)
- **目标 Android 版本**: Android 15 (API 37)
- **可选**: ROOT 权限（解锁全部功能）

## 🏗️ 技术架构

### 核心技术栈

- **Kotlin Multiplatform** - 跨平台代码共享
- **Compose Multiplatform 1.11.1** - 声明式 UI
- **Kotlin 2.4.0** - 编程语言
- **Android Gradle Plugin 9.2.1** - 构建工具

### 主要依赖

| 库 | 版本 | 用途 |
|---|---|---|
| [libsu](https://github.com/topjohnwu/libsu) | 6.0.0 | ROOT 权限管理 |
| [MiuiX](https://github.com/miuix-kotlin-multiplatform/miuix) | 0.9.2 | UI 组件库 |
| [Coil](https://coil-kt.github.io/coil/) | 3.5.0 | 图片加载 |
| [Backdrop](https://github.com/Kyant0/Backdrop) | 2.0.0 | 玻璃效果 |
| DataStore | 1.2.1 | 数据持久化 |
| Navigation3 | 1.1.1 | 导航框架 |

### 项目模块

```
Tweak-Alpha/
├── androidApp/              # Android 应用主模块
├── shared/                  # 共享业务逻辑（KMP）
├── androidSharedNative/     # 原生 C++ 模块（JNI/AIDL）
├── backdrop/                # 玻璃效果库
└── glass/                   # 玻璃效果示例
```

## 🚀 快速开始

### 克隆项目

```bash
git clone https://github.com/yourusername/Tweak-Alpha.git
cd Tweak-Alpha
```

### 构建项目

```bash
# Windows
.\gradlew assembleDebug

# Linux/macOS
./gradlew assembleDebug
```

### 安装到设备

```bash
# Windows
.\gradlew installDebug

# Linux/macOS
./gradlew installDebug
```

## 🔧 开发环境

- **Android Studio**: Ladybug | 2024.2.1 或更高版本
- **JDK**: 17 或更高版本
- **Gradle**: 8.x（使用 Wrapper）
- **NDK**: 用于原生 C++ 模块编译

## 📦 构建配置

### 签名配置

将签名文件放置在 `androidApp/sign/` 目录下，并配置 `build.gradle.kts` 中的签名信息。

### ProGuard

项目已配置代码混淆规则，发布版本会自动启用。规则文件：
- `androidApp/proguard-rules.pro`
- `backdrop/proguard-rules.pro`

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源许可

本项目基于 [GPL-3.0 License](LICENSE) 开源。

## 🔗 相关链接

- [问题反馈](https://github.com/yourusername/Tweak-Alpha/issues)
- [更新日志](https://github.com/yourusername/Tweak-Alpha/releases)

## ⚠️ 免责声明

- 本工具需要 ROOT 权限运行完整功能，可能导致系统不稳定
- 使用本工具造成的任何问题，开发者概不负责
- 请在充分了解风险的情况下使用

---

<div align="center">
  Made with ❤️ by Tweak Team
</div>
