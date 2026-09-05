# 从源码构建

当前提供 Windows PowerShell 构建脚本，原生 Java，无第三方运行时依赖，不需要 Gradle

## 准备工具

- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0

Android SDK 可通过 Android Studio 的 SDK Manager 安装上述两个组件

## 构建

在项目根目录运行，路径换成自己的安装目录

```powershell
.\build.ps1 -JdkPath 'C:\tools\jdk-17' -SdkPath 'C:\Android\Sdk' -Development
```

也可以设置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行

```powershell
.\build.ps1 -Development
```

脚本会编译源码、执行规则检查、打包并校验签名，安装包在 `dist` 下

公开源码默认生成自己的 `development.keystore`，输出 `PageTurner-1.1-dev.apk`

开发包和作者发布的安装包签名不同，不能直接覆盖安装，换装前请先记下自己的设置

不要提交任何签名密钥，正式发布仍使用作者保留的原签名

## 源码位置

| 文件 | 用途 |
| --- | --- |
| `app/src/main/java/local/pageturner/MainActivity.java` | 主界面 |
| `app/src/main/java/local/pageturner/SetupActivity.java` | 权限引导 |
| `app/src/main/java/local/pageturner/TurnService.java` | 悬浮圆点、点击和滑动 |
| `app/src/main/java/local/pageturner/PageProbe.java` | 读取无障碍信息，不截屏 |
| `app/src/main/java/local/pageturner/ProgressWatch.java` | 短时比较页面变化 |
| `tests` | 可在电脑上运行的规则检查 |

规则检查不代替实机验证，改动触摸、悬浮窗或无障碍服务后，请在手机上试用并说明机型
