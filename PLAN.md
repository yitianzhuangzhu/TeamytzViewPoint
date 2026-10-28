# PLAN — 鸟瞰点方块「自定义外观材质」功能

- 项目根目录（绝对路径）：`S:\mc\mod\TeamytzViewPoint-main`
- 目标平台 / MC 版本 / era：**Forge 1.12.2（legacy 1.12.2 线）**，Forge `1.12.2-14.23.5.2860`，ForgeGradle `3.+`，Gradle wrapper **4.9**，MCP snapshot `20171003-1.12`，语言级别 **Java 8**（JDK 8 = `.tools/jdk8u504-b01`，由 `gradlew.bat` 自动回退设置 `JAVA_HOME`）
- 写入本文件的阶段：**A（PLAN）**。本阶段只做调研与规划，不改任何源码。
- 计划编写日期：**2026-09-22**（所有 web 验证均在当日完成，来源列在 §9）

---

## 1. 目标与成功标准

### 1.1 功能目标

在已有鸟瞰点方块（`teamytzviewpoint:viewpoint_block`）的既有 GUI 里，新增一项 **自定义外观材质** 设置：玩家在 GUI 里用**输入框填方块注册名**（如 `minecraft:stonebrick`、`techguns:concrete`），方块在世界里的**模型/贴图**随之改变。

用户已拍板的三条硬约束（不重新设计、不提替代方案）：

1. **交互方式 = 输入框填注册名**，带实时校验（合法/非法视觉反馈）+ 外观预览。不是可滚动列表、不是预设调色板。
2. **只换外观**：模型/贴图跟随目标方块；碰撞、硬度、音效、掉落物、光照属性仍然是鸟瞰点方块自己的，不改变任何玩法属性。
3. **每个方块单独设置**：存在各自方块 TileEntity 的 NBT 里，不做全局默认值。

### 1.2 成功标准（可逐条验收，详见 §10）

| # | 成功标准 |
|---|---|
| S1 | 未设置材质的鸟瞰点方块外观与现状**完全一致**（石砖），老存档升级后不变化 |
| S2 | 拿鸟瞰扳手右键打开 GUI，材质输入框可输入 `minecraft:stonebrick`，合法时显示绿色提示 + 预览图标 |
| S3 | 输入非法名字（`minecraft:notablock` / 空 / 不存在的 modid）时显示红色提示，预览为空白，且**无法**把非法值写入服务端 |
| S4 | 输入 `techguns:concrete` 这类**其他 mod 的方块**注册名后，世界里的方块立刻显示该方块的模型/贴图 |
| S5 | 改材质后**只有外观变**：挖掘速度/硬度/掉落物/音效/碰撞与原方块一致 |
| S6 | **多人同步**：A 玩家改材质后，附近所有玩家（含未打开 GUI 的玩家）都能看到新外观；新登录的玩家也能看到 |
| S7 | 破坏方块后重新放置，材质回到默认（石砖）；已设置的方块在区块卸载→重载、保存→退出→重进世界后材质**保持** |
| S8 | 两个并排的鸟瞰点方块可设置**不同**材质，互不影响 |
| S9 | `gradlew build`（经 mc_gradle）仍然 **BUILD SUCCESSFUL**，产物 `build/libs/TeamytzViewpoint-1.0.15.jar` 正常生成 |
| S10 | GUI 里「恢复默认」按钮能把材质清空回石砖；`Esc` 取消不保存，`Enter` = 确认 |

---

## 2. 结论摘要（先给结论）

| 决策点 | 结论 |
|---|---|
| 渲染路线 | **选定 TESR 路线**：`ViewpointBlock.getRenderType()` 返回 `EnumBlockRenderType.INVISIBLE` 隐藏原模型 + `TileEntitySpecialRenderer<ViewpointTileEntity>` 里用 `BlockRendererDispatcher.renderBlock` 画目标方块模型。（备选的自定义 IBakedModel 路线被否决，理由见 §3.1） |
| 默认外观 | `material` 空串 ⇒ 渲染 `ViewpointBlock.getDefaultState()`（自家模型 = 石砖），外观与现状 100% 一致 ⇒ 满足 S1 |
| 数据存储 | `ViewpointTileEntity` 新增 `private String material = "";`，NBT key = **`ViewpointMaterial`**（与 `ViewpointName` / `ViewpointUnlocked` 同风格）；空串 = 默认石砖 |
| 网络同步 | 扩展现有 2 个包同时携带 name + material；服务端改完后 **`sendToAllAround` 广播**（64 格）给编辑者 + 周围所有玩家；区块加载走**已有的** `getUpdateTag()` / `handleUpdateTag()`（已覆写，直接生效） |
| 是否引入 `@SidedProxy` | **不引入**。沿用现有 `TeamytzViewpoint.init()` 里 `if (event.getSide() == Side.CLIENT)` 分支注册的方式，改动最小、不引入新的类加载坑；新增的纯客户端类加 `@SideOnly(Side.CLIENT)` |
| 校验规则 | 仅接受 `Block.getRenderType(state) == EnumBlockRenderType.MODEL` 的方块；显式排除 `Blocks.AIR` 和自己的 `viewpoint_block`；客户端实时校验 + **服务端二次权威校验** |
| 编码 | 顺手把 `String.getBytes()` / `new String(bytes)` 改成显式 **UTF-8**（每个方法 1 行，ASCII 线格式完全兼容） |
| 资源文件 | `blockstates/viewpoint_block.json` 与 `models/block/viewpoint_block.json` **必须保留**（物品栏图标依赖 block 模型，见 §8） |
| 新增文件数 | **2 个新 Java 文件**（1 个双端安全的 helper + 1 个纯客户端 TESR） |
| 修改文件数 | **7 个 Java 文件 + 2 个 lang 文件**（资源 JSON 不需要改） |

---

## 3. 技术路线选型与理由

### 3.1 路线 A（选定）：TESR + `getRenderType() == INVISIBLE`

做法：
1. `ViewpointBlock.getRenderType(IBlockState)` → `EnumBlockRenderType.INVISIBLE`，原 blockstate JSON 模型在世界里不再渲染。
2. 客户端注册 `TileEntitySpecialRenderer<ViewpointTileEntity>`；`render(...)` 里取 `material` 对应的 `IBlockState`（空/非法 ⇒ 自家 `getDefaultState()`），用 `Minecraft.getBlockRendererDispatcher().renderBlock(state, pos, world, buffer)` 把它画出来。

**为什么选它：**

| 维度 | 评价 |
|---|---|
| 实现复杂度 | 低。一个类 ~60 行，不需要碰模型烘焙/`IModel`/`IModelState`/`IBakedModel`/`ModelLoaderRegistry`/`IForgeRegistryEntry` 任何一处 |
| 模型烘焙 | 复用原版已烘焙好的 `IBakedModel`（任意 mod 的方块模型都自动支持——这正是"填任意注册名"需求的关键）。**注意：TESR 渲染发生在模型烘焙之后，与模型注册时机无关**，不会出现"材质还没烘焙好"的问题 |
| 分块批渲染 | 有性能损失（TESR 每帧一次 draw call，无法进 chunk VBO）。本项目鸟瞰点方块数量极少，损失可忽略 |
| AO（环境光遮蔽） | 直接用**真实世界**做 `IBlockAccess`，邻居方块读取正确 ⇒ AO/光照表现与原版一致（无需写 `IBlockAccess` 包装类） |
| 位置相关模型（栅栏连接、楼梯朝向、原木轴向） | 按"世界里的邻居"计算 —— 对"伪装成某个方块"这个用途正好是**想要的**行为；朝向用目标方块的 `getDefaultState()`（不跟随鸟瞰点方块自身朝向，可接受） |
| 风险 | 低。唯一注意点是 GL 状态与坐标双重偏移（§4 已给出正确写法） |

### 3.2 路线 B（否决）：自定义 `IBakedModel` + `Block.getExtendedState` 包装

做法：`getExtendedState(state, world, pos)` 返回 `state.withProperty(FAKE_MATERIAL, index)`，配合自定义 `IModel`/`IBakedModel` 在烘焙时把每个 variant 烘成目标方块的 `IBakedModel`，让方块进 chunk VBO 批量渲染。

否决理由：
1. **无法承载"任意注册名"**：blockstate property 的数量必须在编译期确定，只能用"有限索引 + 注册表"间接映射，而 `getExtendedState` 的返回值必须能被 blockstate JSON 的 `multipart`/`variants` 命中 —— 动态注册名要在运行时改 blockstate 表，实现复杂且脆弱。
2. 需要写 `IModel`/`IModelState`/`IModelLoader` 一整套，**编译期正确性极难静态验证**（模型烘焙是运行期加载器行为，编译通过 ≠ 游戏里不崩），与"用最小改动交付"的目标冲突。
3. `getExtendedState` 在**服务端也会被调用**（`Block.getActualState` 路径），引入客户端模型引用极易触发 `NoClassDefFoundError`。
4. 收益只有"批渲染性能"，而本项目方块数量极少 —— 收益与风险严重不成比例。

**结论：走路线 A。** 若未来真的需要批渲染优化，可在不改动本 PLAN 数据/网络/校验层的前提下单独立项。

---

## 4. TESR 渲染代码骨架（关键片段，B/C 直接照此实现）

### 4.1 坐标与 GL 处理（**关键点，已推演确认**）

`BlockRendererDispatcher.renderBlock(state, pos, world, buffer)` 底层是 `BlockModelRenderer.renderModel(world, bakedModel, state, pos, buffer, false, ...)`，它会把 `pos.getX()/getY()/getZ()` **当作 float 加到每个顶点坐标上**，并据此计算 AO 邻居。因此：

- 顶点坐标里已包含**绝对世界坐标** → 必须用 `buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ())` 抵消掉；
- 同时 GL 矩阵要平移到方块的世界位置（TESR 的 `x, y, z` 参数**已经是相机相对坐标**，渲染管理器已处理，不要再减 `viewerPos`）；
- 净效果：矩阵把模型放到方块位置，buffer 平移把顶点拉回 0 附近（float 精度良好，不会抖动）；
- 最后必须 `setTranslation(0, 0, 0)` 复位，否则会污染后续使用同一 Tessellator 的渲染。

> 用户给的写法（`pushMatrix` → `translate(x,y,z)` → `bindTexture(BLOCKS_ATLAS)` → `begin(GL_QUADS, BLOCK)` → `setTranslation(-px,-py,-pz)` → `renderBlock` → `setTranslation(0,0,0)` → `draw()` → `popMatrix`）**在 1.12.2 是正确的**，按此实现。

> **host 补充（2026-09-22，已从"推演"升级为"字节码实证"）**：本条不再依赖推断。对 mapped jar 反汇编
> `net.minecraft.client.renderer.BlockModelRenderer` 的 `renderQuadsFlat(...)` 与 `renderQuadsSmooth(...)`，
> 两者字节码开头都是 `BlockPos.getX()/getY()/getZ()` → `i2f`，随后把结果加到每个顶点坐标上（`renderQuadsFlat` 偏移 bci 11/24/37，`i2f` 在 213/229/242；`renderQuadsSmooth` 同样 11/24/37，`i2f` 在 274/290/303）。
> **结论：顶点确实是绝对世界坐标，`setTranslation(-px,-py,-pz)` 必不可少；漏掉就会把方块画到世界原点附近。** 这条已进入 R1，不再是待验证假设。

### 4.2 `ViewpointBlockRenderer.render` 完整骨架

```java
package com.ytzzhu.teamytzviewpoint.client.render;

// 纯客户端类：只允许被客户端的 init 分支 / @SideOnly(Side.CLIENT) 代码引用
@SideOnly(Side.CLIENT)
public class ViewpointBlockRenderer extends TileEntitySpecialRenderer<ViewpointTileEntity> {

    @Override
    public void render(ViewpointTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        // 1) 解析外观：material 为空/非法 → 回落到本方块的默认状态（石砖）
        IBlockState state = ViewpointMaterial.getRenderStateOrDefault(te.getMaterial());

        World world = te.getWorld();
        BlockPos pos = te.getPos();
        if (world == null) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer(); // 1.12.2: getBuffer()

        GlStateManager.pushMatrix();                       // 保存 GL 状态
        GlStateManager.disableLighting();                  // BLOCK 顶点格式自带光照，关掉 GL 固定光照防干扰
        GlStateManager.translate(x, y, z);                 // x/y/z 已是相机相对坐标

        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE); // TESR 父类方法（= blocks 图集）

        try {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK); // GL11 = org.lwjgl.opengl.GL11
            buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ()); // 抵消 renderBlock 写入的绝对坐标

            // 用真实世界做 IBlockAccess → AO/光照/连接状态按世界邻居计算
            Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, world, buffer);

            buffer.setTranslation(0.0D, 0.0D, 0.0D);       // 必须复位
            tessellator.draw();                            // 无顶点时 draw() 自身会跳过，无需额外判空
        } catch (Throwable t) {
            // 任何 mod 的怪模型都不允许崩客户端：记录一次，之后按默认外观渲染
            LOGGER.error("Failed to render viewpoint material, falling back to default", t);
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
        } finally {
            GlStateManager.enableLighting();               // 恢复 GL 状态
            GlStateManager.popMatrix();
        }
    }
}
```

要点说明：

- `GL11` 常量来源：`org.lwjgl.opengl.GL11`（`GL11.GL_QUADS`）。`org.lwjgl` 由 Forge dev 环境/打包环境提供，无需额外依赖。
- 用 **`Tessellator.getInstance().getBuffer()`** 而不是 `WorldRenderer`（1.12.2 里 `Tessellator.getBuffer()` 返回 `BufferBuilder` 实例本身）。
- `bindTexture(UUID?)` 用的是 **`TileEntitySpecialRenderer.bindTexture(ResourceLocation)`**（父类方法），不要用 `TextureManager.bindTexture` 之外的自创写法。
- **不要**在 TESR 里手动设 `GlStateManager.enableBlend()` / `disableAlpha()` 后再自己恢复；`Tessellator.draw()` 内部在 vbo 模式下会重置 blend/depth/cull/alpha/lighting/colorMaterial/fog 状态，我们的 `disableLighting()/enableLighting()` 成对包裹已经足够。
- **destroy 进度裂纹（可选，非必须）**：`destroyStage >= 0` 时可以追加
  `getWorld().getBlockState(pos)` + `BlockRendererDispatcher.renderBlockDamage(IBlockState, BlockPos, IBlockAccess, BufferBuilder, TextureAtlasSprite)`。
  该方法签名 **UNVERIFIED**（本阶段未离线核对 javap，web 来源见 §9）。**默认不实现**，想做的话先按 §9 的 javap 命令核对签名。
- 亮度：`renderBlock` 用真实世界的 `getCombinedLight`。若实机发现方块在**白天露天**也偏暗，再在 `begin` 之后加"满亮"后备方案（`getCombinedLight` 返回的 `int` 高 16 位是天空光、低 16 位是方块光，把两者都置为 `0xF0` 的写法属于常见做法，但**请先实机确认问题存在再改**，不要预先加）。

### 4.3 为什么"未设置时默认石砖"一定成立

`ViewpointMaterial.getRenderStateOrDefault("")` → `ViewpointBlock` 的**注册表实例**的 `getDefaultState()`。默认状态用到的正是 `blockstates/viewpoint_block.json` + `models/block/viewpoint_block.json`（`parent: block/cube_all`，`textures.all = minecraft:blocks/stonebrick`）。所以：

- 走 TESR 时渲染的和现状 JSON 渲染的是**同一个 baked model、同一个贴图**；
- 差别仅在于：现在由 chunk 渲染器画，以后由 TESR 画。视觉上应当**完全无法区分**（这是 S1 的验收依据）。
- 即使在 TESR 注册失败/渲染抛异常的最坏情况下，`material=""` 分支也不会改变外观语义（只是"谁来画"的问题）。

---

## 5. 客户端 / 服务端边界

| 类 | 侧 | 理由 |
|---|---|---|
| `ViewpointBlock.getRenderType()` | **双端** | 返回 `EnumBlockRenderType.INVISIBLE` 常量枚举，类本身在双端都存在，**不引用任何客户端类**，安全。服务端也会调（区块判定/`getActualState` 路径），但只读常量 |
| `ViewpointMaterial`（新，`blocks` 包） | **双端安全** | 只用 `Block.getBlockFromName` / `Block.REGISTRY` / `Block.getDefaultState` / `getRenderType` / `EnumBlockRenderType` / `BlockPos`。**绝对不能** import 任何 `net.minecraft.client.*`。选它放在 `blocks` 包就是为了时刻提醒这一点 |
| `ViewpointTileEntity` / `ViewpointUpdatePacket`（服务端 handler） | **双端安全** | handler 在服务端执行，只能 import `ViewpointMaterial` |
| `ViewpointNameSyncPacket`（客户端 handler） | 双端类 + 客户端分支 | handler 里已有 `ctx.side == Side.CLIENT` 分支，沿用现有写法（`Minecraft.getMinecraft().addScheduledTask`） |
| `ViewpointBlockRenderer`（新，`client.render` 包） | **纯客户端** | 加 `@SideOnly(Side.CLIENT)`；只被 `TeamytzViewpoint.init()` 的 `event.getSide() == Side.CLIENT` 分支引用 |
| `ViewpointGui`（含预览） | **纯客户端** | 现有类已经是 `GuiScreen` 子类，只在客户端 GUI 分支被实例化（`TeamytzViewpoint.getClientGuiElement`，只被客户端调用） |

### 是否要引入 `@SidedProxy`？——**不引入**，理由：

1. 现有代码已经有一套**能正常工作**的模式：`@Mod` 主类同时实现 `IGuiHandler`，客户端专用类只在 `event.getSide() == Side.CLIENT` 分支里 `new` 出来。这套模式在 1.12.2 是被广泛使用的合法写法，`Event.getSide()` 的判断**在客户端和服务端各自独立求值**：服务端上 `FMLInitializationEvent.getSide()` 恒为 `SERVER`，因此该分支**永远不会在服务端执行**，不会触发客户端类的加载。
2. 引入 `@SidedProxy` 需要新增 2 个类（`CommonProxy` / `ClientProxy`）+ 改 `@Mod` 注解，还会改变现有 GUI 注册路径（`registerGuiHandler(this, this)` 依赖主类实现 `IGuiHandler`），**改动面远超本功能所需**。
3. 风险控制方式：给新类加 `@SideOnly(Side.CLIENT)`，并且**只**从客户端分支引用。为了防呆，B 需要在 TESR 注册处用局部变量 + try/catch 包一层（见 §6.7）。
4. **安全 import 白名单**（B/C 必须遵守）：
   - 从 `blocks`/`network`/`data` 包 import：随便（都已经双端安全）。
   - `ViewpointGui` 可以 import `ViewpointMaterial`（双端安全），**但不要** import `ViewpointBlockRenderer`。
   - `ViewpointUpdatePacket` / `ViewpointNameSyncPacket` **绝对不要** import TESR / `ViewpointGui` / 任何 `net.minecraft.client.*`（唯一例外：`ViewpointNameSyncPacket` handler 里现有的 `net.minecraft.client.Minecraft` 全限定名直用，保持现状）。

---

## 6. 逐文件改动清单

> 根目录 = `S:\mc\mod\TeamytzViewPoint-main`。下面路径均相对根目录；**每个文件也给出绝对路径**。
> 「负责」列：**B** = 骨架/接口/注册/数据层，**C** = 功能内容（TESR 主体、GUI、Lang），**D** = 复核/构建/游戏内验收。

### 6.1 `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointTileEntity.java` — 修改（B）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\blocks\ViewpointTileEntity.java`

改动：

1. 新增字段：
   ```java
   /** 目标方块注册名（如 "minecraft:stonebrick"）；空串 = 使用鸟瞰点方块自己的外观（石砖）。 */
   private String material = "";
   ```
2. `readFromNBT`：在 `ViewpointUnlocked` 之后追加
   ```java
   this.material = compound.hasKey("ViewpointMaterial") ? compound.getString("ViewpointMaterial") : "";
   ```
   （**注意**：这里读不到 key 时显式回落空串，避免复用同一实例时残留旧值；`hasKey` + `getString` 的老写法对老存档也安全）
3. `writeToNBT`：追加 `compound.setString("ViewpointMaterial", this.material);`（保证 `material` 不为 null）
4. 新增 getter/setter：
   ```java
   public String getMaterial() { return material; }

   public void setMaterial(String material) {
       String next = (material == null) ? "" : material;
       if (!next.equals(this.material)) {   // 避免无意义的 markDirty/网络风暴
           this.material = next;
           this.markDirty();
       }
   }
   ```
5. `getUpdateTag()` / `handleUpdateTag()`：**不改**。二者走 `writeToNBT`/`readFromNBT`，自动带上材质字段。
6. **不建议**在本阶段新增 `getUpdatePacket()` / `onDataPacket()`（见 §7.3 的取舍说明）。

### 6.2 `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointBlock.java` — 修改（B）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\blocks\ViewpointBlock.java`

改动：新增一个 override（放在 `createTileEntity` 之后）：

```java
@SuppressWarnings("deprecation")   // 见下方说明：覆写点是 @Deprecated 的，但它是 1.12.2 唯一的覆写入口
@Override
public EnumBlockRenderType getRenderType(IBlockState state) {
    // 世界中的外观完全交给 ViewpointBlockRenderer (TESR) 绘制
    return EnumBlockRenderType.INVISIBLE;
}
```

新增 import：`net.minecraft.util.EnumBlockRenderType`（`net.minecraft.block.state.IBlockState` 已在）。

> **host 补充（2026-09-22，javap 实证，务必照做）**：Forge 1.12.2 把 `Block` 上**全部 51 个"带 IBlockState 参数的读取型方法"**标成了 `@Deprecated`
> （`getRenderType(IBlockState)`、`isOpaqueCube(IBlockState)`、`getBoundingBox(...)`、`getMaterial(IBlockState)` …），
> 替代品是 `IBlockState`/`IBlockProperties` 上的**无参访问器**（如 `IBlockProperties.getRenderType()`，已核实存在且不过时）。
> 由此产生两条硬要求：
> 1. **调用侧**一律用无参访问器。`ViewpointMaterial` 里原本的 `block.getRenderType(state)` **已由 host 改成 `state.getRenderType()`**，不要再改回去（那会引入一条"使用了已过时的 API"编译注）。
> 2. **覆写侧**：`Block.getRenderType(IBlockState)` 虽然是 `@Deprecated`，但它**仍然是 1.12.2 唯一的覆写入口**（`Block` 并不实现 `IBlockProperties`，无参的那份由 `BlockState` 转发到它）。所以要覆写，但**必须加 `@SuppressWarnings("deprecation")`**，否则 `compileJava` 会多出"覆盖了已过时的 API"的注（本条已写进验收：除既有 Mixin 警告外不允许新增告警/注）。

**不要改**：`isOpaqueCube()` / `isFullCube()` / `getBoundingBox` / 任何 `Material`、`setHardness`、`setResistance`。理由：`isOpaqueCube()` 默认 `true` 时方块才算"不透光/可遮挡邻居面"，改成 `false` 会**改变光照传播与玩法行为**，违反约束 2。

签名核对：`public EnumBlockRenderType getRenderType(IBlockState)` — 用户已用 javap 验证；另见 [Forge 论坛确认 getRenderType 仍是 1.12.2 定义渲染方式的方法](https://forums.minecraftforge.net/topic/64425-1122-solved-updating-mods-replace-some-methods/)（来源 §9）。

### 6.3 `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointMaterial.java` — **新建**（B）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\blocks\ViewpointMaterial.java`

**双端安全**的工具类（服务端校验 + 客户端校验/预览 + TESR 渲染共用同一套判定逻辑，保证"客户端说合法、服务端也说合法"）。骨架：

```java
package com.ytzzhu.teamytzviewpoint.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;   // 或直接比较 Blocks.AIR
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n; // 服务端安全

/** 双端安全：不得引用任何 net.minecraft.client.* */
public final class ViewpointMaterial {

    /** GUI 输入框允许的最大长度（与服务端截断保持一致）。 */
    public static final int MAX_LENGTH = 64;

    /** 默认外观（空串）使用的方块。 */
    public static final String DEFAULT_MATERIAL = "";

    private ViewpointMaterial() {}

    /** 规范化：trim + toLowerCase(Locale.ROOT) + 按 MAX_LENGTH 截断 + 默认命名空间补全。 */
    public static String normalize(String raw) { ... }

    /**
     * 校验并解析。返回 null 表示"非法"（未知方块 / 非 MODEL 渲染类型 / AIR / 自作引用）。
     * 入参必须是已 normalize 的字符串；空串返回 null（调用方按"默认外观"处理）。
     */
    public static IBlockState resolve(String normalized) {
        if (normalized == null || normalized.isEmpty()) return null;
        Block block = Block.getBlockFromName(normalized);          // 找不到 → null
        if (block == null) return null;
        IBlockState state = block.getDefaultState();
        if (block == Blocks.AIR || block instanceof BlockAir) return null;
        if (block == ModBlocks.VIEWPOINT_BLOCK) return null;       // 防自我递归
        if (block.getRenderType(state) != EnumBlockRenderType.MODEL) return null; // 排除 LIQUID / ENTITYBLOCK_ANIMATED / INVISIBLE
        return state;
    }

    /** 渲染/GUI 预览用：空串或非法一律回落到鸟瞰点方块自己的默认状态（石砖）。 */
    public static IBlockState getRenderStateOrDefault(String material) {
        IBlockState state = resolve(normalize(material));
        return (state != null) ? state : ModBlocks.VIEWPOINT_BLOCK.getDefaultState();
    }

    /** 合法性分级，供 GUI 选提示文案。 */
    public enum Status { DEFAULT, VALID, UNKNOWN_BLOCK, NOT_MODEL, SELF }

    public static Status status(String normalized) { ... }
}
```

约束：
- `normalize` 要处理 `"stonebrick"`（无命名空间）→ 补成 `"minecraft:stonebrick"`；`Block.getBlockFromName` 本身对简写宽容，但规范化后服务端/客户端判定必然一致。
- 非法输入**不得抛异常**：`getBlockFromName` 对垃圾字符串返回 null（用户已 javap 验证），但 `normalize` 里仍要防 `null` 入参。
- `Status` 只用来选文案，不参与是否接受的判定（判定只看 `resolve(...) != null`）。

### 6.4 `src/main/java/com/ytzzhu/teamytzviewpoint/client/render/ViewpointBlockRenderer.java` — **新建**（C）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\client\render\ViewpointBlockRenderer.java`

内容 = §4.2 的完整骨架。额外要求：
- 加 `@SideOnly(Side.CLIENT)`（import `net.minecraftforge.fml.relauncher.Side` / `SideOnly`）。
- 类声明：`public class ViewpointBlockRenderer extends TileEntitySpecialRenderer<ViewpointTileEntity>`。
- **不要**覆写 `hasFastRenderer()` / 不要继承 `FastTESR`（FastTESR 不允许直接操作 GL，与本实现冲突）。
- 异常兜底：`catch (Throwable t)` 里记录日志并复原 buffer 平移，保证单个坏模型不崩客户端。
- 可选优化（**非必须**）：按 `material` 字符串缓存解析结果，避免每帧调 `Block.getBlockFromName`。若做，缓存必须是 `String -> IBlockState` 的 `HashMap`，并在解析失败时缓存 `null` 语义。

### 6.5 `src/main/java/com/ytzzhu/teamytzviewpoint/TeamytzViewpoint.java` — 修改（B）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\TeamytzViewpoint.java`

改动：在 `init(FMLInitializationEvent)` 的 `if (event.getSide() == Side.CLIENT)` 分支里加 TESR 注册 + 日志：

```java
if (event.getSide() == Side.CLIENT) {
    MinecraftForge.EVENT_BUS.register(new ViewpointClientEvents());
    MinecraftForge.EVENT_BUS.register(new ViewpointRenderHandler());
    try {
        ClientRegistry.bindTileEntitySpecialRenderer(
                ViewpointTileEntity.class, new ViewpointBlockRenderer());
        logger.info("Viewpoint TESR registered.");
    } catch (Throwable t) {
        logger.error("Failed to register Viewpoint TESR; viewpoint blocks will render invisible!", t);
    }
    registerAmbienceIntegration();
}
```

- 新增 import：`net.minecraftforge.fml.client.registry.ClientRegistry`、`com.ytzzhu.teamytzviewpoint.client.render.ViewpointBlockRenderer`。
- **注意 `ViewpointBlockRenderer` 这个 import 会出现在主类里**：因为主类在服务端也会被加载，理论上服务端启动时类加载器会尝试解析这个 import —— 但 Java 只在**符号被实际使用的方法执行时**才解析类引用，而 `new ViewpointBlockRenderer()` 只在客户端分支内，服务端永远不会执行到。这正是现有 `ViewpointRenderHandler` 已经在用的模式（`init` 里同分支 import + new），**照抄即可**。
- 若 D 在实机发现服务端类加载异常（极不可能），后备方案：把注册逻辑挪进一个 `@SideOnly(Side.CLIENT)` 静态方法，用 `Class.forName` + 反射调用；**先不要写**，只在真出问题时才用。

### 6.6 `src/main/java/com/ytzzhu/teamytzviewpoint/network/ViewpointUpdatePacket.java` — 修改（B/C）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\network\ViewpointUpdatePacket.java`

线格式见 §7.1。改动点：
1. 新增字段 `private String material;`，构造函数改为 `ViewpointUpdatePacket(BlockPos pos, String name, String material)`（保留 2 参重载可省——**不加**，避免调用方忘传材质）。
2. `toBytes` / `fromBytes` 按 §7.1 顺序实现；字符串一律 `StandardCharsets.UTF_8`；写出前对 `null` 做 `""` 兜底；读取时对长度做上限保护（> 1024 直接当空串处理，避免畸形包导致 OOM）。
3. handler（服务端线程）改为：

```java
player.getServer().addScheduledTask(() -> {
    ViewpointTileEntity te = (ViewpointTileEntity) world.getTileEntity(pos);
    if (te == null) return;
    // 服务端权威校验：玩家必须在场、必须持扳手、必须在交互距离内
    if (player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
    boolean holdingWrench = player.getHeldItemMainhand().getItem() instanceof WrenchItem
            || player.getHeldItemOffhand().getItem() instanceof WrenchItem;
    if (!holdingWrench) return;

    String rawName = ViewpointMaterial.normalize(message.name);
    if (rawName.length() > 64) rawName = rawName.substring(0, 64);
    String name = rawName.isEmpty() ? "鸟瞰点" : rawName;   // 与现有"名字不可空"行为一致

    String normalized = ViewpointMaterial.normalize(message.material);
    String material = (ViewpointMaterial.resolve(normalized) != null) ? normalized : ""; // 非法 → 空串(默认石砖)

    te.setName(name);
    te.setMaterial(material);
    te.markDirty();

    // 回发一份权威结果给编辑者 + 广播给附近所有玩家（修复"别人看不到外观变化"）
    ViewpointNameSyncPacket sync = new ViewpointNameSyncPacket(pos, name, material);
    TeamytzViewpoint.instance.network.sendTo(sync, player);
    TeamytzViewpoint.instance.network.sendToAllAround(sync, new NetworkRegistry.TargetPoint(
            world.provider.getDimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64.0));
});
```

4. 新增 import：`com.ytzzhu.teamytzviewpoint.blocks.ViewpointMaterial`、`net.minecraftforge.fml.common.network.NetworkRegistry`、`com.ytzzhu.teamytzviewpoint.items.WrenchItem`、`java.nio.charset.StandardCharsets`。
   - `WrenchItem` 是双端类（`items` 包），服务端 import 安全。
   - 如果觉得"同一玩家收两份包"不优雅，可以只保留 `sendToAllAround`（编辑者 100% 在 64 格内），**但显式 `sendTo` 更稳**，保留两条。

### 6.7 `src/main/java/com/ytzzhu/teamytzviewpoint/network/ViewpointNameSyncPacket.java` — 修改（B）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\network\ViewpointNameSyncPacket.java`

1. 新增 `private String material;`，构造函数 `ViewpointNameSyncPacket(BlockPos pos, String name, String material)`。
2. 线格式与 `ViewpointUpdatePacket` **完全一致**（§7.1），同样显式 UTF-8。
3. handler（客户端线程，`ctx.side == Side.CLIENT` 分支，**沿用现有 `Minecraft.getMinecraft().addScheduledTask(...)` 写法**）里：

```java
((ViewpointTileEntity) te).setName(message.name);
((ViewpointTileEntity) te).setMaterial(message.material);
```

4. 注意：handler 里只允许用**全限定名**或已被现有代码使用的客户端引用；不要新增 `import net.minecraft.client.Minecraft;` 之外的客户端 import（保持现状最小改动）。
5. **非法材质的兜底**：即使服务端已经校验过，客户端 `setMaterial` 也不做额外校验（`ViewpointMaterial.getRenderStateOrDefault` 会在渲染时兜底），保持简单。

### 6.8 `src/main/java/com/ytzzhu/teamytzviewpoint/gui/ViewpointGui.java` — 修改（C）

绝对路径：`S:\mc\mod\TeamytzViewPoint-main\src\main\java\com\ytzzhu\teamytzviewpoint\gui\ViewpointGui.java`

详见 §8 的布局与交互；要点：

1. **面板尺寸从 256×128 改为 256×168**；`centerX = (width - 256) / 2; centerY = (height - 168) / 2;`（`initGui` 与 `drawScreen` 两处必须同时改，否则布局错位）。
2. 新增 `GuiTextField materialField = new GuiTextField(3, fontRenderer, centerX + 20, centerY + 64, 176, 20)`，`setMaxStringLength(ViewpointMaterial.MAX_LENGTH)`，初始文本 = `tileEntity.getMaterial()`，`setFocused(false)`；`nameField` 保持 `setFocused(true)`（保持现有"打开即可打字"体验）。
3. 新增「恢复默认」按钮：`new GuiButton(4, centerX + 204, centerY + 64, 36, 20, I18n.format("viewpoint.gui.material.default"))`（文案 `Default` / `默认`），点击后 `materialField.setText("")` + 重新校验。
4. `handleConfirm()` 改为：
   ```java
   String newName = nameField.getText().trim();
   String material = ViewpointMaterial.normalize(materialField.getText());
   boolean materialOk = material.isEmpty() || ViewpointMaterial.resolve(material) != null;
   if (!materialOk) {
       // 非法材质：不发送，就地标红提示（不关窗，让玩家改）
       this.materialStatus = ViewpointMaterial.Status.UNKNOWN_BLOCK; // 或按 status() 细分
       return;
   }
   TeamytzViewpoint.instance.network.sendToServer(
       new ViewpointUpdatePacket(tileEntity.getPos(), newName.isEmpty() ? tileEntity.getName() : newName, material));
   this.mc.player.closeScreen();
   ```
   > **host 修正：无条件发包**（只要材质合法就往服务端发）。不要用"名字或材质任一非空"当门槛——玩家把名字清空、同时把材质清空以"恢复默认"时，那个条件会静默吞掉整次提交。名字为空由服务端回落默认名（§7.4 第 5 条），`material == ""` 是合法的"恢复默认"，必须发。
5. 焦点与按键（见 §8.3）：`Tab` 在两个输入框间切换；`mouseClicked` 里显式互斥焦点；`keyTyped` 里保留现有 `Return`/`Escape` 语义（`Escape` 由 `super.keyTyped` 关闭 GUI = 取消）。
6. 预览绘制（§8.2）放在 `drawScreen` 的 `super.drawScreen(...)` **之后**，避免被按钮/输入框遮挡。
7. 保持 `doesGuiPauseGame() == false` 不变（鸟瞰点动画需要继续跑）。

### 6.9 `src/main/resources/assets/teamytzviewpoint/lang/en_us.lang` / `zh_cn.lang` — 修改（C）

绝对路径：
- `S:\mc\mod\TeamytzViewPoint-main\src\main\resources\assets\teamytzviewpoint\lang\en_us.lang`
- `S:\mc\mod\TeamytzViewPoint-main\src\main\resources\assets\teamytzviewpoint\lang\zh_cn.lang`

新增 key 见 §9。**编码要求：UTF-8（不带 BOM），CRLF/LF 保持文件原有风格，`zh_cn.lang` 里已有的中文一个字符都不能动。**

### 6.10 不改的文件（明确列出，防范围蔓延）

- `src/main/resources/assets/teamytzviewpoint/blockstates/viewpoint_block.json`（保留，见 §8）
- `src/main/resources/assets/teamytzviewpoint/models/block/viewpoint_block.json`（保留，物品模型依赖它）
- `src/main/resources/assets/teamytzviewpoint/models/item/viewpoint_block.json`、`models/item/viewpoint_wrench.json`
- `build.gradle` / `gradlew*` / `gradle.properties` / `settings.gradle`（**不存在也不需要**）
- `gui/ViewpointContainer.java`（空容器，无需改）
- `items/ModItems.java`、`items/WrenchItem.java`
- `mixin/MixinAbstractClientPlayer.java`、`mixins.teamytzviewpoint.json`、`.refmap.json`
- `integration/AmbienceIntegration.java`、`integration/ViewpointTask*.java`
- `mcmod.info`、`pack.mcmeta`（模组版本号是否从 `1.0.15` 升到 `1.0.16` **由 host 决定**，本 PLAN 不强制；若升，需要同步改 `build.gradle` 的 `version`/`TeamytzViewpoint.VERSION`/`mcmod.info` 三处，属于独立小改动）

---

## 7. 数据、线格式与同步

### 7.1 两个网络包的线格式（逐字段读写顺序，**必须完全一致**）

`ViewpointUpdatePacket`（C→S）与 `ViewpointNameSyncPacket`（S→C）共用同一套编码：

| 顺序 | 类型 | 字段 | 说明 |
|---|---|---|---|
| 1 | `byte` | `protocolVersion` = `1` | 放在**最前面**。旧版客户端/服务端的第一个字节会被当成 `long` 的高位 → 解析必然失败并断开，好过静默解析出错位数据（这是一个"防混版"保护，非兼容手段） |
| 2 | `long` | `pos.toLong()` | 方块坐标（沿用现状） |
| 3 | `int` | `nameByteLength` | name 的 UTF-8 字节数 |
| 4 | `byte[]` | name 的 UTF-8 字节 | `writeBytes(...)`；`null` → `""` |
| 5 | `int` | `materialByteLength` | material 的 UTF-8 字节数 |
| 6 | `byte[]` | material 的 UTF-8 字节 | `writeBytes(...)`；`null` → `""` |

读侧保护：读取 `length` 后若 `length < 0 || length > 1024`，直接把该字段当 `""` 并 `skipBytes(min(剩余))` 或直接 `return`（畸形包不入库）。写侧：字符串在 `normalize` 时已按 `MAX_LENGTH = 64` 截断，UTF-8 字节数远小于 1024。

编码取舍（用户问的"要不要顺手改 UTF-8"）：**要改，改动 1 行/方法。**
- 现状 `name.getBytes()` / `new String(bytes)` 用**平台默认字符集**。服务端在 Windows 上（GBK/CP936）时中文名字符串实际是 UTF-8 线格式恰好能跑；**一旦服务端跑在 Linux（默认 UTF-8）或有 JVM `-Dfile.encoding` 差异的环境，中文名字节会与客户端不一致 → 乱码**。
- 改成 `getBytes(StandardCharsets.UTF_8)` / `new String(bytes, StandardCharsets.UTF_8)` 后：ASCII 字节完全相同（不影响 `minecraft:stonebrick` 这类），中文在网络两侧恒定一致。**唯一兼容性影响**：如果"新客户端 + 旧服务端（或反过来）"并且名字含非 ASCII，会乱码 —— 单机/配套发布场景可忽略，且前面加了 protocolVersion 保护。

### 7.2 NBT key 与字段

| 字段 | NBT key | 类型 | 默认值 | 写入位置 |
|---|---|---|---|---|
| `name` | `ViewpointName`（**已有**） | String | `"鸟瞰点"` | `writeToNBT` |
| `unlocked` | `ViewpointUnlocked`（**已有**） | boolean | `false` | `writeToNBT` |
| `material` | **`ViewpointMaterial`**（新增） | String | `""`（空串 = 石砖） | `writeToNBT` |

- 命名风格与现有两个 key 一致（`Viewpoint` 前缀 + 大驼峰）。
- 老存档没有该 key → `readFromNBT` 回落 `""` → 外观不变（S1）。
- `material` 存储的是**规范化后的注册名**（`minecraft:stonebrick`），不存 `IBlockState`/meta。目标方块带 meta 的变体（原木轴向等）统一用 `getDefaultState()`。
- **不引入 `nbttag` 版本号 / 不写迁移代码**（空串即是天然的"未设置"语义）。

### 7.3 同步范围方案（用户指出的必须修的点）

现状：服务端只 `sendTo(编辑者自己)`，且**没有** `getUpdatePacket()` 覆写，别的玩家看不到外观变化。

**选定方案（组合拳）：**

1. **即时同步（改完立刻可见）**：`ViewpointUpdatePacket` handler 里 `sendToAllAround(new ViewpointNameSyncPacket(...), new NetworkRegistry.TargetPoint(dim, x+0.5, y+0.5, z+0.5, 64.0))` + 额外 `sendTo(编辑者)`。
   - 签名：`SimpleNetworkWrapper.sendToAllAround(IMessage, NetworkRegistry.TargetPoint)` / `NetworkRegistry.TargetPoint(int dimension, double x, double y, double z, double range)`（用户已 javap 验证）。
   - 64 格与本 mod 现有 `ViewpointContainer.canInteractWith` 的 64（= 8 格距离平方）不是同一含义；这里是"渲染可见性半径"，取 64 格足够覆盖正常视野，且不会给远处玩家发无意义包。
2. **区块加载同步（新登录玩家 / 走远再回来）**：靠**已有的** `getUpdateTag()` / `handleUpdateTag()` 覆写。这两个方法已经走 `writeToNBT`/`readFromNBT`，新增字段自动带上，**无需任何改动**。
3. **不覆写 `getUpdatePacket()` / `onDataPacket()`**：
   - 它们的作用是"服务端主动推送单方块 TE 数据"（`SPacketUpdateTileEntity`），主要用途是**破坏方块时的 TE 移除**和自管理同步；我们已经有自定义包 + `getUpdateTag`，再加一套是重复劳动且引入双份线程调度路径。
   - 取舍说明写在 §12 风险表里：如果 D 在实机发现"切维度/传送到远处再回来外观不更新"，再补 `getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 1, getUpdateTag()); }` + `onDataPacket(...) { readFromNBT(net.getTagCompound()); }` 作为后备（**属于可选后备项，不在本次必做范围**）。
4. **不做** `world.notifyBlockUpdate(pos, state, state, 3)`：`getRenderType` 已是 `INVISIBLE`，TESR 每帧重画，不存在"方块模型需要重新渲染"的问题，服务端发送状态同步包没有意义。
   （`World.notifyBlockUpdate(BlockPos, IBlockState, IBlockState, int)` 签名用户已 javap 验证，此处只是不需要。）

### 7.4 服务端权威校验（必须）

服务端 handler 是**唯一**的可信入口，任何客户端都可能被改包。校验顺序：

1. `world.getTileEntity(pos)` 必须是 `ViewpointTileEntity`（不然直接 return）。
2. 玩家与 `pos` 的距离平方 ≤ `64.0`（与 `ViewpointContainer.canInteractWith` 同一门槛）。
3. 玩家主手或副手必须持有 `WrenchItem`（与 `ViewpointBlock.onBlockActivated` 的开门条件一致，防止"没拿扳手也能改"）。
4. `material`：`normalize` 后 `ViewpointMaterial.resolve(...) == null` ⇒ **回退空串（默认石砖）**，不报错、不踢人。这样即使被改包塞入 `minecraft:water`（LIQUID）或乱码，最终落库的也一定是合法值，客户端渲染永远安全。
5. `name`：`trim` 后为空 ⇒ 回落默认名 `"鸟瞰点"`（与现有 GUI 行为一致，GUI 侧也是不允许空名）。

---

## 8. GUI 布局、预览与输入冲突

### 8.1 新布局（面板放大到 256×168）

`centerX = (this.width - 256) / 2`，`centerY = (this.height - 168) / 2`（相对面板左上角，全部 +centerX/+centerY）：

| 元素 | 相对坐标 | 尺寸 | 说明 |
|---|---|---|---|
| 外框黑底 | (0,0)–(256,168) | `drawRect(0xFF000000)` | 现状风格 |
| 内框灰底 | (4,4)–(252,164) | `drawRect(0xFF444444)` | 现状风格 |
| 标题 `viewpoint.gui.title` | y=15 居中 | — | 保持不变 |
| 名称标签 `viewpoint.gui.name` | (20, 28) | — | 保持不变 |
| 名称输入框 `GuiTextField(0)` | (20, 40) w=216 h=20 | 216×20 | 保持不变（id=0，`setFocused(true)`） |
| 材质标签 `viewpoint.gui.material` | (20, 52) | — | 新增 |
| 材质输入框 `GuiTextField(3)` | (20, 64) w=176 h=20 | 176×20 | 新增，`maxLength=64` |
| 预览框背景 | (202, 62) w=38 h=24 | 38×24 | 新增，`0xFF202020` + 1px 边框 `0xFF888888` |
| 预览物品 | (214, 67) | 16×16 | 居中于预览框 |
| 「恢复默认」按钮 `GuiButton(4)` | (20, 88) w=60 h=20 | 60×20 | 新增，文案 `viewpoint.gui.material.default` |
| 校验状态行 | (20, 114) | 单行 | 新增，颜色随状态变化 |
| 提交按钮 `gui.done` `GuiButton(1)` | (20, 138) w=100 h=20 | 100×20 | 由 y=80 下移到 138 |
| 取消按钮 `gui.cancel` `GuiButton(2)` | (136, 138) w=100 h=20 | 100×20 | 由 y=80 下移到 138 |

按钮 id 分配：**0 = nameField、1 = done、2 = cancel（沿用现状）、3 = materialField、4 = 恢复默认**。

### 8.2 预览怎么画

```java
// drawScreen 里，super.drawScreen(mouseX, mouseY, partialTicks) 之后
GlStateManager.pushMatrix();
GlStateManager.translate(0.0F, 0.0F, 100.0F);        // 抬到 GUI 图层上方（renderItemAndEffectIntoGUI 自身 z=+100）
RenderHelper.enableGUIStandardItemLighting();          // 使图标有正确的 GUI 光照
ItemStack stack = previewStack();                      // 见下
if (!stack.isEmpty()) {
    this.itemRender.renderItemAndEffectIntoGUI(stack, centerX + 214, centerY + 67);
}
RenderHelper.disableStandardItemLighting();
GlStateManager.popMatrix();

private ItemStack previewStack() {
    if (this.materialStatus != ViewpointMaterial.Status.VALID) return ItemStack.EMPTY;
    IBlockState state = ViewpointMaterial.resolve(this.currentMaterial);
    if (state == null || state.getBlock() == Blocks.AIR) return ItemStack.EMPTY;
    Item item = Item.getItemFromBlock(state.getBlock());   // 可能返回 null（很多技术类方块没有 ItemBlock）
    return (item == null) ? ItemStack.EMPTY : new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
}
```

**降级显示规则（用户明确要求的"Item 为 null 时怎么降级"）：**
- `Item.getItemFromBlock(...) == null` ⇒ 只画空白预览框内框 + 状态行文字 `viewpoint.gui.material.preview.none`（"该方块无物品图标"），**不画任何图标、不报错**。
- `getRenderType == ENTITYBLOCK_ANIMATED`（箱子/潜影盒等）在 `resolve` 阶段就已经被拒 ⇒ 状态是 `NOT_MODEL`，提示 `viewpoint.gui.material.invalid.render`。
- 状态行分级文案：

| 状态 | 颜色 | 文案 key |
|---|---|---|
| `DEFAULT`（输入框为空） | `0xAAAAAA` | `viewpoint.gui.material.default_hint` |
| `VALID` | `0x55FF55` | `viewpoint.gui.material.valid`（`%s` = 规范化后的名字） |
| `UNKNOWN_BLOCK` | `0xFF5555` | `viewpoint.gui.material.invalid` |
| `NOT_MODEL` | `0xFF5555` | `viewpoint.gui.material.invalid.render` |
| `SELF` | `0xFF5555` | `viewpoint.gui.material.invalid.self` |

状态每次 `keyTyped` 之后重算（实时反馈），并保存到字段 `currentMaterial` / `materialStatus`。

### 8.3 两个输入框的焦点 / 输入冲突

1. **焦点互斥**：覆写 `mouseClicked(int mouseX, int mouseY, int mouseButton)`：
   ```java
   boolean nameClicked   = this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
   boolean materialClicked = this.materialField.mouseClicked(mouseX, mouseY, mouseButton);
   if (nameClicked)      this.materialField.setFocused(false);
   else if (materialClicked) this.nameField.setFocused(false);
   else if (mouseButton == 0 && !isOverAnyField(mouseX, mouseY)) {
       // 点空白处：保持当前焦点（不要两个都失焦，否则打不了字）
   }
   super.mouseClicked(mouseX, mouseY, mouseButton);   // 处理按钮
   ```
   `GuiTextField.mouseClicked(...)` 的映射为 `func_146192_a(int,int,int)`，`setFocused(boolean)` 为 `func_146195_b(boolean)`（mappings 验证，见 §9）。
2. **Tab 切换**：在 `keyTyped` 里，`keyCode == Keyboard.KEY_TAB` 时切换焦点并 `return`（不要传给输入框）：
   ```java
   if (keyCode == Keyboard.KEY_TAB) {
       boolean toName = !this.nameField.isFocused();
       this.nameField.setFocused(toName);
       this.materialField.setFocused(!toName);
       return;
   }
   ```
3. **Enter / Esc 语义**：**保持现有 `keyTyped` 结构，只做最小插入。**（host 修正，优先级高于上面第 2 点里"重写按键处理"的倾向）
   - 开头**照旧**调用 `super.keyTyped(typedChar, keyCode);`（现有代码就是这么写的；`Escape` 由 `GuiScreen` 关闭界面 = 等效取消，`Return` 语义保持现状）。**不要**改成"不再调 super + 自己处理 Esc/Return"的重构——那是在功能需求之外重写既有按键逻辑，收益为零、回归面不小（R10 的兜底方案就是本条，直接按兜底做）。
   - 在 `super.keyTyped(...)` 之后、`if (keyCode == Keyboard.KEY_RETURN)` **之前**插入 `TAB` 分支：
     ```java
     if (keyCode == Keyboard.KEY_TAB) {
         boolean toName = !this.nameField.isFocused();
         this.nameField.setFocused(toName);
         this.materialField.setFocused(!toName);
         return;
     }
     ```
   - 把原第 71 行 `this.nameField.textboxKeyTyped(typedChar, keyCode);` 改成"转给当前聚焦的输入框"，实时校验只是**在输入之后**重算状态，不新建一套按键处理：
     ```java
     if (this.nameField.isFocused()) {
         this.nameField.textboxKeyTyped(typedChar, keyCode);
     } else if (this.materialField.isFocused()) {
         this.materialField.textboxKeyTyped(typedChar, keyCode);
         this.refreshMaterialStatus();   // 逐字重算 VALID / UNKNOWN_BLOCK / NOT_MODEL / SELF
     }
     ```
   - 回归点（比原方案小得多）：Enter 确认、Esc 取消、两个框都能打字、Tab 能切换。仍由 V17/V18 覆盖。
4. **`initGui()` 幂等**：`GuiScreen.initGui()` 在窗口 resize 时会再次调用。如果不保存文本，材质输入框的内容会在 resize 后丢失。B/C 必须：在 `initGui()` 开头把 `materialField/nameField` 的当前文本（若已存在）保存到局部变量，重建控件后 `setText(...)` 回填。
5. **不做**输入法/剪贴板特殊处理（`GuiTextField` 自带 `Ctrl+C/V`），不做滚轮切换。

---

## 9. 语言文件（新增 key 全表）

**两个文件都要写**，key 完全一致：

- `S:\mc\mod\TeamytzViewPoint-main\src\main\resources\assets\teamytzviewpoint\lang\en_us.lang`
- `S:\mc\mod\TeamytzViewPoint-main\src\main\resources\assets\teamytzviewpoint\lang\zh_cn.lang`

| key | en_us | zh_cn |
|---|---|---|
| `viewpoint.gui.material` | `Material:` | `外观材质:` |
| `viewpoint.gui.material.default` | `Default` | `默认` |
| `viewpoint.gui.material.default_hint` | `Empty = default (stone bricks)` | `留空 = 默认（石砖）` |
| `viewpoint.gui.material.valid` | `Valid: %s` | `合法: %s` |
| `viewpoint.gui.material.invalid` | `Unknown block name` | `方块注册名不存在` |
| `viewpoint.gui.material.invalid.render` | `That block cannot be shown` | `该方块不支持作为外观` |
| `viewpoint.gui.material.invalid.self` | `Cannot use the viewpoint block itself` | `不能使用鸟瞰点方块自身` |
| `viewpoint.gui.material.preview.none` | `No icon` | `无物品图标` |
| `viewpoint.gui.material.hint` | `e.g. minecraft:stonebrick` | `例如 minecraft:stonebrick` |

注意事项：

- **不要**给 `%s` 加 `%1$s` 之外的定位符；`I18n.format(key, args)` 走 `String.format`，`%s` 可直接用。
- **不要**在 lang 值里写 `=`（会截断），也不要在值里写 `#`（部分加载器当注释）——上面文案已避开。
- **编码**：写文件时必须 UTF-8（不带 BOM）。zh_cn.lang 已有 12 行中文，**一个字符都不能变**；`viewpoint.message.unlocked` 里的 `§6` 之类的格式码不要动。
- 建议把新 key 插在 `viewpoint.gui.name` 之后、`viewpoint.task.*` 之前，保持文件可读性。

---

## 10. 资源文件结论（getRenderType 改成 INVISIBLE 之后）

| 文件 | 结论 | 理由 |
|---|---|---|
| `blockstates/viewpoint_block.json` | **保留，不改** | 世界渲染已经走 TESR，`normal`/`inventory` 两个 variant 里的 `normal` 变成死配置；但 `inventory` variant 仍被**物品栏/手持/掉落物**的 `ItemBlock` 模型解析链使用，删了会破坏 item 模型解析 |
| `models/block/viewpoint_block.json` | **保留，不改** | 两条依赖：(a) `blockstates` 的 `inventory` variant 指向它；(b) `models/item/viewpoint_block.json` 的 `parent` 指向它。**TESR 默认外观（石砖）也直接复用这个 baked model**，所以它根本不是"死文件"，是默认外观的唯一来源 |
| `models/item/viewpoint_block.json` | **保留，不改** | 物品栏图标继续正常显示石砖（用户需求只要求"方块在世界的**外观**可变"，**没有要求物品图标跟随**——物品图标跟随需要 `ItemMeshDefinition`/自定义 `IBakedModel`，属于范围外，见 §13） |
| `models/item/viewpoint_wrench.json` | 不改 | 与功能无关 |

**物品栏图标是否受影响？——不受影响。** `ItemBlock` 的模型由 `models/item/viewpoint_block.json` 决定，它**只依赖 block 模型的 parent**，**完全不经过** `Block.getRenderType()`（该方法只影响世界中的方块渲染）。所以：方块在世界里由 TESR 画、在物品栏里由 JSON 模型画，两条路径互不干扰。

**一个副作用要写进验收预期**：如果用户把材质设成 `minecraft:glass` / `minecraft:water` 之类，物品栏图标**仍然**是石砖（因为图标不跟随）。这是**已知且可接受**的行为，不要当成 bug。

---

## 11. 构建与验证方案（给 D）

### 11.1 构建（**必须走 mc_gradle**，不要直接 shell 调 `gradlew`）

| 步骤 | 命令（通过 mc_gradle） | 预期 | 超时建议 |
|---|---|---|---|
| 1 | task = `compileJava` | `BUILD SUCCESSFUL`；只有已知的 1 条 Mixin 描述符警告（见 `.tools/README.md`），**不允许出现新的警告/错误** | 600s（首次可能触发 MCP recompile） |
| 2 | task = `build` | `BUILD SUCCESSFUL in ~25s`；产出 `build/libs/TeamytzViewpoint-1.0.15.jar`（reobfJar 已跑） | 900s |
| 3 | task = `jar`（可选，快速迭代用） | 只打包不 reobf，用于快速看 class 是否生成 | 600s |

注意事项：
- 环境是 JDK 8（`.tools/jdk8u504-b01`，Temurin 1.8.0_504），wrapper 4.9。`org.gradle.daemon=false`，每次都是冷启动 JVM，**不要**因为"慢"就加 `--daemon`。
- 编译期用 MCP 名（`20171003-1.12`），**不要**写 SRG 名（`func_xxx`），除了已有 mixin 的既有写法。
- `build.gradle` 里的 `jar.finalizedBy('reobfJar')` 意味着 `build` 会自动 reobf；如果 `reobfJar` 报错，先看是不是新增代码引用了未映射的类。
- 本阶段（PLAN）**不执行任何构建**（见 §3 的说明与角色边界）。

### 11.2 游戏内验收（client run；每条都给预期结果）

前置：`run` 目录 + Forge 1.12.2 client；开局给一个 `teamytzviewpoint:viewpoint_block`（创造模式物品栏 MISC 页）和一个 `teamytzviewpoint:viewpoint_wrench`。**建议装 1 个有方块的第三方 mod**（项目 `libs/` 里有 FTBQuests 等，但它们不提供方块；用客户端的任意 tech mod 即可，或者退一步用 `minecraft:concrete` / `minecraft:planks` / `minecraft:redstone_block` 这类不同形态的原版方块）。

| # | 操作 | 预期结果 |
|---|---|---|
| V1 | 空手右键放置好的鸟瞰点方块 | 解锁提示（`§6[鸟瞰点] …已解锁！`），**外观仍是石砖**（S1 基线，确认 `getRenderType=INVISIBLE`+TESR 没有破坏默认外观） |
| V2 | 手持扳手右键 | 打开 GUI；面板比原来高（168），有"名称"和"外观材质"两个输入框、预览框、"恢复默认"按钮、底部 Done/Cancel |
| V3 | 在材质框里逐字输入 `minecraft:stonebrick` | 每敲一个字就重算：输入过程中显示红色（不存在的名字），打完变成**绿色** `合法: minecraft:stonebrick`，预览框出现石砖图标 |
| V4 | 点 Done | GUI 关闭，世界里的方块**外观无变化**（本来就是石砖） |
| V5 | 重新打开 GUI，输入 `minecraft:stone`，观察预览 | 绿色 + 预览图标 = 石头贴图；点 Done 后世界里的方块**立刻**变成石头 |
| V6 | 输入 `minecraft:water` | 红色 + `该方块不支持作为外观`（水面是 `LIQUID` 渲染类型，被正确拒绝）；点 Done **不会关窗、不会保存** |
| V7 | 输入 `minecraft:chest` | 红色 + `该方块不支持作为外观`（箱子是 `ENTITYBLOCK_ANIMATED`）→ **同时验证 §12 R2 的枚举假设是否成立** |
| V8 | 输入 `techguns:concrete` 或任意第三方方块注册名 | 绿色 + 预览图标；Done 后世界方块显示该 mod 方块的模型/贴图（**验证跨 mod 支持**） |
| V9 | 输入 `minecraft:notablock` / 空字符串 | 前者红色不保存；后者显示灰色 `留空 = 默认（石砖）`，点 Done 后世界方块**恢复石砖**（验证 S10 的恢复默认） |
| V10 | 只换外观不换玩法：对材质为 `minecraft:stone` 的鸟瞰点方块用铁镐挖 | 挖掘速度与原方块一致（硬 2.0，**不是**石头的 1.5）；掉落物是 `viewpoint_block` 本身；音效是 `Material.ROCK` 的石头音；碰撞箱同原方块（**验证 S5**） |
| V11 | 多人同步：客户端 A 改材质，客户端 B 站在旁边（**B 不打开 GUI**） | B 那边**立刻**看到外观变化（验证 S6 的即时同步） |
| V12 | 玩家 B **后登录**（或 A 用 `/tp` 离开再回来触发区块重载） | B 看到的仍是 A 设置的材质（验证 `getUpdateTag`/`handleUpdateTag` 路径，S6/S7） |
| V13 | A 设材质 → 退出世界 → 重进世界 | 材质保持（NBT 持久化验证） |
| V14 | 放置两个相邻的鸟瞰点方块，设不同材质（石砖 / 石） | 两块各自显示各自材质，互不影响（验证 S8） |
| V15 | 破坏设过材质的方块 → 重新放置 | 新方块是默认石砖（NBT 随方块销毁，验证 S7） |
| V16 | 名牌含中文的情形：输入一个中文名字确认，再改材质确认 | 中文不乱码（验证 §7.1 的 UTF-8 改动没有回归） |
| V17 | 反复按 `Tab` / 点击两个输入框 / 窗口 resize | 焦点正确切换；resize 后输入框内容不丢失（验证 §8.3） |
| V18 | 打开 GUI 后按 `Esc` | GUI 关闭且**没有保存**（材质不变） |
| V19 | 检查日志 | 无 `Missing model` / `NoClassDefFoundError` / `NullPointerException` / TESR 异常堆栈；`Viewpoint TESR registered.` 出现 |
| V20 | 计分板/迷你地图等旁路：把鸟瞰点材质设为 `minecraft:stone`，站在旁边看 F3 调试屏 | `biome`/光照正常；方块仍不透光（周围不出现漏光/贴图空洞） |

### 11.3 离线 API 核对命令（D 在构建前可先行核对，防止"编不过"）

```powershell
& 'S:\mc\mod\TeamytzViewPoint-main\.tools\jdk8u504-b01\bin\javap.exe' -cp `
  'C:\Users\admin\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12\forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar' `
  <全限定类名>
```

需要核对的类（本阶段未能离线执行 javap 的全部条目，列在 §12 的 UNVERIFIED 表）：

- `net.minecraft.item.Item`（找 `getItemFromBlock`）
- `net.minecraft.block.Block`（找 `getRenderType` / `getBlockFromName`）
- `net.minecraft.client.gui.GuiTextField`（找 `mouseClicked` / `setFocused` / `isFocused` / `setMaxStringLength`）
- `net.minecraft.client.gui.GuiScreen`（找 `mouseClicked`）
- `net.minecraft.client.renderer.BlockRendererDispatcher`（找 `renderBlock` / `renderBlockDamage`）
- `net.minecraft.client.renderer.BufferBuilder`（找 `begin` / `setTranslation` / `getVertexCount`）
- `net.minecraft.client.renderer.Tessellator`（找 `getBuffer` / `draw`）
- `net.minecraft.client.renderer.RenderHelper`（找 `enableGUIStandardItemLighting` / `disableStandardItemLighting`）
- `net.minecraft.client.renderer.texture.TextureMap`（找 `LOCATION_BLOCKS_TEXTURE`）
- `net.minecraft.client.renderer.texture.TextureManager`（找 `bindTexture`）
- `net.minecraft.client.renderer.GlStateManager`（找 `color` / `bindTexture` **不存在性**）
- `net.minecraft.item.ItemStack`（找 `isEmpty` 是否存在，避免用错）
- `net.minecraft.init.Blocks`（确认 `AIR` 类型，供 `BlockAir` 判定）

---

## 12. API 依据表（含来源与验证状态）

### 12.1 已确认（用户 javap 验证 或 本阶段 web 验证）

| API | 结论 | 来源 |
|---|---|---|
| `public static Block Block.getBlockFromName(String)` | 存在，找不到返回 `null` | 用户 javap（离线 mapped jar），本阶段未推翻 |
| `public EnumBlockRenderType Block.getRenderType(IBlockState)` | 存在且可覆写；返回值决定渲染方式（MODEL/INVISIBLE/LIQUID/ENTITYBLOCK_ANIMATED） | 用户 javap + [Forge 论坛：getRenderType 仍是 1.12.2 的渲染方式定义方法](https://forums.minecraftforge.net/topic/64425-1122-solved-updating-mods-replace-some-methods/) |
| `EnumBlockRenderType.INVISIBLE` | 常量存在 | 用户 javap |
| `Block.getDefaultState()` | `public final` | 用户 javap |
| `public boolean BlockRendererDispatcher.renderBlock(IBlockState, BlockPos, IBlockAccess, BufferBuilder)` | 存在；页面标注 `public boolean func_175018_a(IBlockState, BlockPos, IBlockAccess, BufferBuilder)` | 用户 javap + [mappings.dev 1.12.2 BlockRendererDispatcher](https://mappings.dev/1.12.2/net/minecraft/client/renderer/BlockRendererDispatcher.html) |
| `TileEntitySpecialRenderer<T>.render(T, double,double,double, float, int, float)` | 存在（Forge 文档写作 `renderTileEntityAt`，1.12.2 MCP 名为 `render`） | 用户 javap + [Forge 1.12.x TESR 文档](https://docs.minecraftforge.net/en/1.12.x/tileentities/tesr/) |
| `ClientRegistry.bindTileEntitySpecialRenderer(Class<T>, TileEntitySpecialRenderer<? super T>)` | 存在 | 用户 javap + 同 Forge TESR 文档 |
| `Minecraft.getBlockRendererDispatcher()` | 存在 | 用户 javap |
| `TextureMap.LOCATION_BLOCKS_TEXTURE` | 存在（`ResourceLocation`） | 用户 javap |
| `BufferBuilder.begin(int, VertexFormat)` / `setTranslation(double,double,double)` | 存在（`func_181668_a(int, VertexFormat)` / `func_178969_c(double,double,double)`） | 用户 javap + [mappings.dev BufferBuilder](https://mappings.dev/1.12.2/net/minecraft/client/renderer/BufferBuilder.html) |
| `BufferBuilder.getVertexCount()` | 存在（`func_178989_h()`），用于"有顶点才 draw"的兜底判断 | mappings.dev BufferBuilder |
| `DefaultVertexFormats.BLOCK` | 存在 | 用户 javap |
| `SimpleNetworkWrapper.sendTo(IMessage, EntityPlayerMP)` / `sendToAllAround(IMessage, TargetPoint)` | 存在 | 用户 javap |
| `NetworkRegistry.TargetPoint(int, double,double,double, double)` | 存在 | 用户 javap |
| `World.notifyBlockUpdate(BlockPos, IBlockState, IBlockState, int)` | 存在（本方案不使用） | 用户 javap |
| `TileEntity.getPos()/getWorld()/markDirty()/getUpdateTag()/handleUpdateTag(NBTTagCompound)` | 存在 | 用户 javap + 现有代码 |
| `GuiTextField(int, FontRenderer, int,int,int,int)` / `setMaxStringLength(int)` / `setText(String)` / `getText()` / `drawTextBox()` / `textboxKeyTyped(char,int)` | 存在 | 现有代码已在用 |
| `GuiTextField.setFocused(boolean)` / `isFocused()` | 存在（`func_146195_b(boolean)` / `func_146181_i()`） | [mappings.dev GuiTextField](https://mappings.dev/1.12.2/net/minecraft/client/gui/GuiTextField.html) |
| `GlStateManager.pushMatrix/popMatrix/translate(double,double,double)/disableLighting/enableLighting` | 存在（`func_179097_i`/`func_179101_C`/`func_179137_b(double,double,double)`/`func_179097_i`/`func_179145_e`） | [mappings.dev GlStateManager](https://mappings.dev/1.12.2/net/minecraft/client/renderer/GlStateManager.html) |
| **`GlStateManager.bindTexture(int)` 不存在** | **反例**：1.12.2 的 `GlStateManager` 没有可用的 `bindTexture`。必须用 `TileEntitySpecialRenderer.bindTexture(ResourceLocation)`（TESR 内）或 `Minecraft.getMinecraft().getTextureManager().bindTexture(...)` | mappings.dev GlStateManager（方法表里无 bindTexture） |
| **`net.minecraft.block.Block` 没有 `getParticleTexture()`** | **反例**（用户已核实）：1.12.2 只有 `addHitEffects`/`addDestroyEffects`/`blockParticleGravity`。**不要**尝试覆写粒子贴图 | 用户 javap |
| `Item.getItemFromBlock(Block)` | 存在，**可能返回 null** | 用户 javap |
| `RenderItem.renderItemAndEffectIntoGUI(ItemStack,int,int)` | 存在 | 用户 javap |
| `RenderHelper.enableGUIStandardItemLighting()` | 存在 | 用户 javap |
| `TextureMap.getAtlasSprite(String)` | 存在（本方案不使用，TESR 走 atlas 绑定） | 用户 javap |
| `EnumBlockRenderType` 是双端类（非 `@SideOnly(CLIENT)` 限制） | 推断成立：`Block.getRenderType` 的返回类型必须在服务端可加载，否则服务端区块逻辑无法运行 | 用户 javap 能列出该类 + 1.12.2 双端渲染判定逻辑 |

### 12.2 UNVERIFIED（本阶段无法离线核对，必须由 B/D 用 javap 或实机确认）

| # | 待确认项 | 待确认内容 | 兜底方案（确认失败时） |
|---|---|---|---|
| U1 | `Item.getItemFromBlock(Block)` 的**精确签名**（返回类型/是否 static） | 用 §11.3 的 javap 命令查 `net.minecraft.item.Item` | 改用 `ItemStack` 的另一个构造路径，或直接**不画预览图标**、只显示状态行文字（§8.2 的降级路径本来就要做） |
| U2 | `BlockRendererDispatcher.renderBlockDamage(IBlockState, BlockPos, IBlockAccess, BufferBuilder, TextureAtlasSprite)` | javap 查 `BlockRendererDispatcher` | 直接**不做**被破坏时的裂纹叠加（本 PLAN 的默认选择） |
| U3 | `ItemStack.isEmpty()` 在 1.12.2 是否存在 | javap 查 `net.minecraft.item.ItemStack` | 用 `stack != null && stack.getItem() != Items.AIR` 替代（`ItemStack.EMPTY` 在 1.12 也存在） |
| U4 | `net.minecraft.block.BlockAir` 的确切 FQN | javap 查 `net.minecraft.init.Blocks` 的 `AIR` 字段类型 | 直接 `block == Blocks.AIR`（最稳） |
| U5 | 实测：水/岩浆的 `getRenderType` 是否真的返回 `LIQUID`；箱子/潜影盒是否返回 `ENTITYBLOCK_ANIMATED`；空气是否返回 `INVISIBLE` | **游戏内**验证（V6/V7） | 无论如何代码都显式排除 `Blocks.AIR` 和 `viewpoint_block`；若某类方块漏判（例如某 mod 的"不可见"方块返回 MODEL 但实际不画），表现为"方块变透明"——用户可在 GUI 里改成别的名字，不会崩 |
| U6 | `GlStateManager.color(float,float,float,float)` 的精确 MCP 名 | javap 查 `GlStateManager` | **不使用** `GlStateManager.color`；TESR 里的顶点颜色由 `BLOCK` 顶点格式自带的 `color()` 决定，不需要手动设色 |
| U7 | TESR 里调用 `renderBlock` 时，若模型在 GL 状态下被其他 mod（如 OptiFine shader）改动，是否会出问题 | 实机（V1 之后目视检查 + 日志） | 加"渲染失败 → 默认外观"的 try/catch 兜底（已在 §4.2 骨架里） |
| U8 | `BufferBuilder.setTranslation(...)` 之后 `renderBlock` 的 AO/亮度表现 | 实机（V1/V5 目视：白天露天方块不应偏暗） | 若偏暗：在 `begin` 后用 `buffer.color(255,255,255,255)` 或整体提高亮度值（先诊断再改） |
| U9 | `IBlockState.getBlock().getMetaFromState(state)` 用于 `ItemStack` 变体是否必要 | javap/实机 | `new ItemStack(item, 1, meta)` 里 meta 不确定时直接 `new ItemStack(item)`（预览图标用 meta 0 的变体通常也可接受） |
| U10 | 第三方 mod 的方块（如 `techguns:concrete`）在 TESR 里渲染是否正常 | 实机 V8 | 异常会被 §4.2 的 try/catch 兜住；最坏情况是某个具体方块渲不出来，用户换个名字即可 |

---

## 13. 风险与坑清单

| # | 风险 | 等级 | 说明与对策 |
|---|---|---|---|
| R1 | **坐标双重偏移算错** → 方块渲到 (0,0,0) 或偏移一倍坐标 | 高（但已定论） | `renderBlock` 会把 `pos` 的**绝对值**加进顶点，所以必须 `setTranslation(-px,-py,-pz)`；GL 矩阵用 `translate(x,y,z)`（相机相对）。按 §4.1/§4.2 实现即可。验收 V5 一看就知道对不对 |
| R2 | **非 MODEL 渲染类型的方块**（水/岩浆 = `LIQUID`，箱子/潜影盒 = `ENTITYBLOCK_ANIMATED`，空气/屏障 = `INVISIBLE`） | 高 | 一律在 `ViewpointMaterial.resolve` 里拒绝（`U5` 需要实机确认具体返回值）。GUI 给专门文案（`...invalid.render`）。**绝对不能**把这些名字写进 NBT 后直接拿去渲染 |
| R3 | **自我递归**：把材质设成 `teamytzviewpoint:viewpoint_block` | 高 | `resolve` 里 `block == ModBlocks.VIEWPOINT_BLOCK` 直接拒绝；GUI 提示 `不能使用鸟瞰点方块自身`（`SELF` 状态）。这一条同时防住了"TESR 里渲染自己 → 无限递归/崩栈" |
| R4 | **1.12.2 没有 `Block.getParticleTexture()`**（用户已核实） | 中 | 不要写任何粒子贴图相关代码。副作用：挖掘/落地粒子用默认的（不跟随目标方块）。**这是已知限制**，不是 bug |
| R5 | **`GlStateManager.bindTexture(int)` 在 1.12.2 不存在** | 中 | 用 `bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)`（TESR 父类方法） |
| R6 | GL 状态泄漏（TESR 改成 `disableLighting` 后没恢复 / buffer 平移没复位） | 中 | §4.2 用 `try/finally` 保证 `setTranslation(0,0,0)` + `enableLighting()` + `popMatrix()` 一定执行 |
| R7 | **多人不同步**（用户明确指出的现存缺陷） | 中 | `sendToAllAround` + 现有 `getUpdateTag`/`handleUpdateTag`（§7.3）。验收 V11/V12 |
| R8 | **服务端被改包**（客户端塞非法注册名） | 中 | 服务端 handler 二次校验并**回退空串**，非法值永远进不了 NBT（§7.4） |
| R9 | **`String.getBytes()` 平台编码**导致中文名跨平台乱码 | 中 | 显式 `StandardCharsets.UTF_8`（§7.1）。这条是"顺手修"，但只改了 2 个类里的 4 行，风险极低 |
| R10 | **GUI `keyTyped` 结构调整**可能破坏 Esc/Enter 现有行为 | 中 | §8.3 给出明确顺序 + 验收 V18/V17 专测；若 D 发现回归，回退到"保留原 `super.keyTyped` 调用 + 只加 Tab 分支"的最小改法 |
| R11 | **`initGui()` 重入（窗口 resize）丢文本** | 低 | §8.3 第 4 点：重建控件前保存/回填文本 |
| R12 | **老存档/已放置方块**没有 `ViewpointMaterial` key | 低 | `readFromNBT` 回落 `""` → 外观不变（S1/V1/V13 验收） |
| R13 | **`lang` 文件被写出 BOM / 编码变化**破坏已有中文 | 中 | 写文件一律 UTF-8 无 BOM；改完 `git diff` 只应显示"新增行"，不应显示已有行被修改（D 用 `git diff --stat` 复核） |
| R14 | **`zh_cn.lang` 里已有 `§6` 格式码**（`viewpoint.message.unlocked`） | 低 | 不要动那一行；新 key 不要引入格式码 |
| R15 | **TESR 性能**：每个方块每帧 1 次 draw call + 1 次 `getBlockFromName` | 低 | 方块数量少可忽略；`getBlockFromName` 建议按 §6.4 加 `String -> IBlockState` 缓存 |
| R16 | **物品栏图标不跟随材质**（仍然是石砖） | 低（已知） | §10 已说明：用户只要求世界外观可变。跟随图标需要 `ItemMeshDefinition`/自定义 `IBakedModel`，**属于范围外** |
| R17 | **`destroyStage` 裂纹不显示**（TESR 接管后原版破坏进度纹理默认不画） | 低 | 可选实现（U2）。破坏动画仍有"方块消失"的反馈，且不影响玩法。若要实现，先核对签名 |
| R18 | **`getRenderType=INVISIBLE` 后粒子/破坏特效变化** | 低 | 与 R4 同源。**不要**为此去改 `isOpaqueCube`/`getBoundingBox`（会违反"不改玩法"约束） |
| R19 | **构建环境**：无系统 JDK、wrapper 4.9、`org.gradle.daemon=false` | 低 | 只用 mc_gradle 触发 `gradlew.bat`；JDK 8 由 `gradlew.bat` 头部回退逻辑自动注入；**不要**手写 `org.gradle.java.home`（换机器会直接拒绝启动） |
| R20 | **libs/ 里的 vendored jar**（FTBQuests/FTBLib/Ambience/ItemFilters/xaero 三件套/mixin） | 低 | `build.gradle` 里是 `compile fileTree(dir: 'libs', include: ['*.jar'])`，新增代码**不引用**它们，不受影响；**改动依赖时同步这一行**（相机改为独立实体接管后已不再需要任何第三方相机前置 jar） |
| R21 | **mixin 现有 1 条警告**（`MixinAbstractClientPlayer` 的 `@Inject` 描述符） | 低 | 既有问题，与本次改动无关；`defaultRequire = 1` 意味着运行期找不到目标会崩 —— **本次不改 mixin**，验收时若客户端启动即崩且日志指向该 mixin，应判定为既有问题而非本功能回归 |
| R22 | **同一个包/注册名的 `String` 大小写**：`Minecraft:StoneBrick` | 低 | `normalize` 里 `toLowerCase(Locale.ROOT)`；`Locale.ROOT` 避免土耳其语 `I/i` 问题 |
| R23 | **`BlockPos.fromLong` 与 `protocolVersion` 字节** | 低 | 放在最前面导致新旧混搭时解析失败并断连（**故意**，好过静默错位）。发布时必须客户端/服务端同版本 |
| R24 | **TESR 注册失败**（异常）导致方块在世界里完全不可见 | 中 | §6.5 用 try/catch + 明确日志（`Failed to register Viewpoint TESR; viewpoint blocks will render invisible!`）；V19 检查日志 |
| R25 | **在 `getRenderType` 里做重量级计算** | 低 | 必须只 `return EnumBlockRenderType.INVISIBLE;` —— 该方法会被区块构建高频调用 |

---

## 14. 明确的「不做」清单（防范围蔓延）

1. **不做**自定义 `IBakedModel` / `IModel` / `ModelLoaderRegistry` / `getExtendedState` 模型烘焙路线（§3.2 已否决）。
2. **不做**可滚动列表 / 预设调色板 / 颜色选择器 / 拖拽选块 —— 用户已拍板"输入框填注册名"。
3. **不做**方块选择器/搜索 UI、不做"最近使用"历史记录。
4. **不做**全局默认材质、不做配置文件（`ForgeConfigSpec`/`.cfg`）、不做"应用于该玩家所有鸟瞰点" —— 用户已拍板"每个方块单独设置，存 TE NBT"。
5. **不做**材质与方块朝向的绑定（不存 meta/`IBlockState`，统一 `getDefaultState()`）。
6. **不做**随机材质、材质轮播、材质渐变等效果。
7. **不碰**鸟瞰点解锁逻辑（`ViewpointUnlockData`）、鸟瞰相机（`ViewpointCameraManager`）、`WrenchItem` 的既有行为。
8. **不碰** `gui/ViewpointContainer.java`（不加槽位、不做物品栏）。
9. **不碰** mixin（`MixinAbstractClientPlayer`、`mixins.teamytzviewpoint.json`、`.refmap.json`）。
10. **不碰** FTBQuests / Ambience 集成（`integration/**`）。
11. **不碰** `build.gradle` / `gradlew*` / `gradle.properties` / wrapper 版本（1.12.2 线固定 Gradle 4.9 + JDK 8）。
12. **不引入** `@SidedProxy` / `CommonProxy` / `ClientProxy`（§5 已论证不必要）。
13. **不覆写** `getUpdatePacket()` / `onDataPacket()`（§7.3 说明；仅在实机发现维度切换不同步时才作为后备补上）。
14. **不调用** `world.notifyBlockUpdate(...)`（§7.3 第 4 点）。
15. **不改** `isOpaqueCube()` / `isFullCube()` / `getBoundingBox()` / `Material` / 硬度 / 抗性 / 音效 / 掉落表 —— 约束 2 的红线。
16. **不做**物品栏/手持/掉落物图标跟随材质（R16）。
17. **不做**粒子贴图跟随（R4：1.12.2 根本没有 `getParticleTexture()` 这个 API，不是"实现难"而是"没这个口"）。
18. **不做**破坏裂纹纹理叠加（R17 可选，默认不做）。
19. **不做** GUI 缩放/拖拽/主题化改造；只做"面板从 128 高改成 168 高 + 新增一行控件"。
20. **不做**网络协议向前兼容（跨版本混搭）；版本不一致直接断连（R23）。
21. **不做**服务端 GUI（布告牌/命令），不做 `/viewpointmaterial` 之类的命令。
22. **不改** `mcmod.info` 里的模组元数据 / 不升版本号（是否升版由 host 决定，见 §6.10）。

---

## 15. 文件归属总表（B / C / D）

| 文件（相对根目录） | 动作 | 负责 | 关键产出 |
|---|---|---|---|
| `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointMaterial.java` | **新建** | B | 双端安全的 `normalize` / `resolve` / `getRenderStateOrDefault` / `Status` |
| `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointTileEntity.java` | 修改 | B | `material` 字段 + `ViewpointMaterial` NBT key + getter/setter |
| `src/main/java/com/ytzzhu/teamytzviewpoint/blocks/ViewpointBlock.java` | 修改 | B | `getRenderType` → `INVISIBLE` |
| `src/main/java/com/ytzzhu/teamytzviewpoint/network/ViewpointUpdatePacket.java` | 修改 | B | 扩展线格式 + 服务端权威校验 + `sendToAllAround` |
| `src/main/java/com/ytzzhu/teamytzviewpoint/network/ViewpointNameSyncPacket.java` | 修改 | B | 扩展线格式 + 客户端写入 name+material |
| `src/main/java/com/ytzzhu/teamytzviewpoint/TeamytzViewpoint.java` | 修改 | B | 客户端分支注册 TESR（+日志兜底） |
| `src/main/java/com/ytzzhu/teamytzviewpoint/client/render/ViewpointBlockRenderer.java` | **新建** | C | TESR `render` 主体（§4.2） |
| `src/main/java/com/ytzzhu/teamytzviewpoint/gui/ViewpointGui.java` | 修改 | C | 256×168 布局 + 材质输入框 + 实时校验 + 预览 + 恢复默认 + 焦点/按键（§8） |
| `src/main/resources/assets/teamytzviewpoint/lang/en_us.lang` | 修改 | C | 9 个新 key（§9），UTF-8 无 BOM |
| `src/main/resources/assets/teamytzviewpoint/lang/zh_cn.lang` | 修改 | C | 同 9 个 key（§9），UTF-8 无 BOM，已有中文不动 |
| `src/main/resources/assets/teamytzviewpoint/model*/**`、`blockstates/**` | **不改** | — | 保留（§10） |
| （全部改动）构建 `compileJava` / `build` | 验证 | **D** | mc_gradle 执行，§11.1 |
| （全部改动）游戏内 V1–V20 | 验证 | **D** | §11.2；重点 V1/V6/V7/V10/V11/V12 |
| （全部改动）API javap 核对 U1–U10 | 验证 | **D**（或用 B 编译错误暴露） | §11.3 命令 |
| `build.gradle`、`gradlew*`、`gradle.properties`、mixin、integration、libs/ | **不改** | — | 见 §14 |

**实施顺序建议（B → C → D）**：

1. B 先做 §6.1–6.3（数据层 + 校验层），此时**可单独编译通过**，功能上"材质字段存在但没人用"→ 无行为变化。
2. B 再做 §6.5–6.7（打包 + 注册），可编译通过，此时"设置材质能同步到所有客户端，但世界里还看不到"（因为 `getRenderType` 还没改）。
3. C 做 §6.4 TESR + §6.8 GUI + §6.9 lang。
4. B/C 都完成后**才**把 `ViewpointBlock.getRenderType` 改成 `INVISIBLE`（§6.2）——这一步是"外观开关"，最后打开，避免中间态出现"方块全隐形"难以定位问题。
   > 也就是说：**§6.2 是最后一个落地的小改动**，D 的第一次完整验收应在它之后进行。

---

## 16. 交付总结（给 host）

- 选定渲染路线：**TESR + `Block.getRenderType() == INVISIBLE`**（自定义 IBakedModel 路线已否决）。
- 改动规模：**新建 2 个 Java 文件**（`ViewpointMaterial`、`ViewpointBlockRenderer`），**修改 7 个 Java 文件 + 2 个 lang 文件**，资源 JSON 与构建脚本不改。
- 最大风险：**TESR 里 `renderBlock` 的坐标双重偏移 + AO/光照表现**（写错就整块渲到原点或偏暗），以及**非 MODEL 渲染类型方块（水/箱子）必须被拒绝**（否则渲染黑洞或什么都不显示）。
- 验收方式：mc_gradle 跑 `compileJava` + `build`，然后在 1.12.2 客户端按 §11.2 的 V1–V20 逐条走（重点：V1 默认外观不变、V6/V7 非法/非 MODEL 被拒、V10 玩法不变、V11/V12 多人同步、V13/V15 持久化与销毁）。
