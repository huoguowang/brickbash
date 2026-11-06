# 砖块爆破 BrickBash

[![Build APK](https://github.com/huoguowang/brickbash/actions/workflows/build-apk.yml/badge.svg)](https://github.com/huoguowang/brickbash/actions/workflows/build-apk.yml)

原生 Android 打砖块游戏。Kotlin + 自定义 View + Canvas，**零第三方依赖**（没用 AndroidX，也没用游戏引擎），
构建快、包小、逻辑全部可读。代码约 600 行，分三块：

> 仓库：https://github.com/huoguowang/brickbash
> 已经过一次真实 CI 编译（JDK 17 + Gradle 8.7 + AGP 8.5.2），产物 `app-debug.apk` 约 796 KB。

| 文件 | 职责 |
| --- | --- |
| `MainActivity.kt` | 全屏、常亮、刘海屏、生命周期 |
| `GameView.kt` | 主循环（Choreographer）、触摸、绘制、震动、最高分存档 |
| `GameWorld.kt` | 纯游戏逻辑：球、挡板、砖块、碰撞、关卡、粒子 |

```
brickbash/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties
├─ .github/workflows/build-apk.yml   ← 推到 GitHub 自动出 APK
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/dsh/brickbash/{MainActivity,GameView,GameWorld}.kt
      └─ res/{values,drawable}
```

## 怎么编译出 APK

### 方式一：Android Studio（最省事）

1. 装 Android Studio（自带 JDK 17 和 SDK）。
2. `File → Open` 选这个 `brickbash` 目录，等 Gradle Sync 完成（第一次要下 Gradle 8.7 + AGP + SDK 34）。
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`。
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`，发到手机点开就能装。

> 这个仓库里只放了 `gradle-wrapper.properties`，没有 `gradle-wrapper.jar`（二进制文件）。
> Android Studio 会自己处理；命令行同学先跑一次 `gradle wrapper` 生成 `./gradlew`。

### 方式二：命令行

需要 JDK 17 + Android SDK（`ANDROID_HOME` 指向 SDK，装 `platforms;android-34` 和 `build-tools;34.0.0`）。

```bash
gradle wrapper          # 只需一次，生成 gradlew
./gradlew assembleDebug
```

### 方式三：没有电脑也能出包（GitHub Actions）

推到 GitHub，仓库自带 `.github/workflows/build-apk.yml`：
Actions 里会自动用 JDK 17 + Gradle 8.7 编译，跑完在 run 页面底部下载
`brickbash-debug-apk` 工件，解压就是 `app-debug.apk`。

## 玩法

- 手指按住屏幕拖动挡板，点击发射小球。
- 砖块全清 → 进入下一关，球速 +40dp/s，行数逐关增加（最多 7 行）。
- 掉球扣一条命，3 条命用完游戏结束；最高分存在 SharedPreferences 里。

## 想改哪里

| 想调的东西 | 位置 |
| --- | --- |
| 球速 / 难度曲线 | `GameWorld.levelBallSpeed()` |
| 每关砖块行数 | `GameWorld.buildLevel()` 里的 `rows` |
| 砖块配色（7 种） | `GameWorld.ROW_COLORS` |
| 挡板宽度、球大小 | `GameWorld.resize()` |
| 震动强度 | `GameView.buzz()` |
| 粒子数量 | `GameWorld.burst()` 的 `repeat(12)` |
| 命中反弹角度 | `GameWorld.bounceOffPaddle()` 的 `Math.PI / 3` |

## 上架前要改的

1. `app/build.gradle.kts` 里 `applicationId` 换成你自己的包名（不能和别人的重复）。
2. 换成自己的 keystore 签名（现在 release 偷懒用了 debug 签名，只能自己装、不能上架）。
3. `versionCode` / `versionName`。
4. 图标：`res/drawable/ic_launcher.xml`（现在是矢量图，可以直接改色块）。

## 当前状态

- 已通过 GitHub Actions 实际编译，产物是签名过的 debug APK（debug keystore 签的，能 sideload、不能上架）。
- 这台手机本机没有 JDK / Android SDK，所以本地没有编译能力，改动后靠 CI 验证。
- 没有音效（要的话加 `res/raw` + `SoundPool`，我可以补）。
- 没有排行榜、成就、多语言。
