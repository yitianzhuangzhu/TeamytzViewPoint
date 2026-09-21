# .tools — 本机构建环境（工作区自带）

本目录只为让 **Minecraft 1.12.2 / ForgeGradle 3** 的构建在这台没有系统 JDK 的机器上跑起来。
不是 mod 源码的一部分，删掉不影响 mod 代码，但删掉后必须重新拿到 JDK 8 才能编译。

## 为什么需要它

本机全盘无任何 `java.exe` / `javac.exe`（`where java` 找不到），注册表无 JavaSoft 项，
`JAVA_HOME` 为空。而 1.12.2 线只能用 **JDK 8~10**（ForgeGradle 3 + Gradle 4.9；Java 11+
直接报 `Unsupported class file major version`）。所以把一份便携 JDK 8 挂在工作区里。

## 内容

| 路径 | 说明 |
|---|---|
| `jdk8u504-b01/` | Temurin（Adoptium）OpenJDK 8u504-b01，Windows x64，约 190 MB，解压即用，不动系统环境变量。**当前是指向 `S:\mc\mod\TGCEAddon\.tools\jdk8u504-b01` 的目录联接（junction）**，省 190 MB 重复占用 |
| `download-jdk8.js` | JDK 下载脚本（先清华 Adoptium 镜像，失败回退 Adoptium API） |
| `fetch.js` | 通用 HTTPS 下载小工具 |

重新获取 JDK（junction 失效，或误删了 `jdk8u504-b01/`）：

```powershell
Remove-Item .tools\jdk8u504-b01 -Force -Recurse   # 若是坏掉的 junction，先移除链接本身
$node = 'C:\Users\admin\AppData\Local\Programs\DeepSeek Harness\resources\runtime\win32-x64\node.exe'
& $node .tools\download-jdk8.js .tools\jdk8.zip
Expand-Archive .tools\jdk8.zip -DestinationPath .tools -Force
```

或者重建 junction（TGCEAddon 还在的话）：

```powershell
cmd /c mklink /J ".tools\jdk8u504-b01" "S:\mc\mod\TGCEAddon\.tools\jdk8u504-b01"
```

**为什么用 Node 下载而不是 `Invoke-WebRequest` / `curl`**：在受限进程里 Windows 的 Schannel
拿不到凭证（`AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`），https 一律连接失败；
Node 自带 OpenSSL 与 CA 库，不走 Schannel，所以正常。Gradle/Java 走 JSSE，同样不受影响。

## 构建链怎么接到这份 JDK

`gradlew.bat` 启动脚本头部加了一段（**ASCII 注释，不要改成中文**——cmd 按 OEM 代码页读 .bat，
非 ASCII 字节会打乱语法）：

```bat
if not defined JAVA_HOME (
    for /d %%i in ("%DIRNAME%.tools\jdk8*") do set "JAVA_HOME=%%~fi"
)
```

有系统 JDK（`JAVA_HOME` 已设）的机器完全不受影响。

`gradle.properties` **没有**加 `org.gradle.java.home`：那份配置里写死绝对路径，换机器路径不存在时
Gradle 会直接拒绝启动。本项目 `org.gradle.daemon=false`，构建用 launcher 自己的 JVM，
所以 `gradlew.bat` 里定好 `JAVA_HOME` 就够了。

## 已验证基线（2026-09-21）

```
gradlew.bat build
  -> BUILD SUCCESSFUL in 24s（Gradle 4.9 + ForgeGradle 3.0.197 + Forge 1.12.2-14.23.5.2860）
  -> 产出 build/libs/TeamytzViewpoint-1.0.15.jar（2.29 MB，已 reobfJar）
  -> 1 条编译警告：MixinAbstractClientPlayer 的 @Inject 目标方法无法确定描述符（见下）
```

Mixin 警告说明：源码直接写了 SRG 名 `func_175149_v`，而编译期是 MCP 名环境（该方法叫
`isSpectator`），注解处理器解析不到就发警告。`src/main/resources/mixins.teamytzviewpoint.refmap.json`
里已把 `isSpectator()Z -> func_175149_v()Z` 钉死，运行期在 SRG 名环境下按名查找能命中，
**不影响产物**；`defaultRequire = 1` 意味着万一目标方法真找不到会崩，改 mixin 后请跑一次客户端验证。

本机 Gradle 缓存位置：`C:\Users\admin\.gradle`（wrapper dists + forge_gradle 的 MCP/mapped jar）。
