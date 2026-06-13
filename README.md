# TVAppGet

一个简易 Android TV 应用，用于读取 `youhunwl/TVAPP` 项目 README 中的一览表，并在电视上以遥控器友好的方式展示应用简介、版本、状态和下载入口。

## 功能

- 启动后在线拉取 `https://raw.githubusercontent.com/youhunwl/TVAPP/main/README.md`
- 解析应用名称、版本、下载地址、状态和备注
- 左侧列表支持遥控器方向键和 OK 键操作
- 直接 APK 链接可下载并调用系统安装器
- 目录或 README 链接会通过 GitHub Contents API 查找 APK，进入版本选择列表
- 支持 Android TV/盒子启动入口，触屏不是必需硬件

## 构建

用 Android Studio 打开本目录，等待 Gradle 同步完成后构建 `app` 模块即可。

工程配置：

- `minSdk 21`
- `targetSdk 35`
- 原生 Java + Android View，无第三方运行时依赖

## 电视端注意事项

首次安装下载到的 APK 时，Android 8.0 及以上系统通常会要求允许“安装未知来源应用”。应用会自动打开对应设置页，允许后返回重新点击下载项即可安装。
