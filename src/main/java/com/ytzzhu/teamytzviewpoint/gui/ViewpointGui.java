package com.ytzzhu.teamytzviewpoint.gui;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.ViewpointWaypointColors;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointMaterial;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.network.ViewpointUpdatePacket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class ViewpointGui extends GuiScreen {
    private ViewpointTileEntity tileEntity;
    private GuiTextField nameField;
    private GuiTextField materialField;
    private GuiButton confirmButton;
    private GuiButton cancelButton;
    private GuiButton defaultMaterialButton;
    private GuiButton colorButton;
    /** 材质输入框内容的规范化结果（实时校验用）。 */
    private String currentMaterial = "";
    /** 当前材质的校验状态，只用来选文案/颜色。 */
    private ViewpointMaterial.Status materialStatus = ViewpointMaterial.Status.DEFAULT;
    /** 路径点颜色的预设索引（始终是合法的预设索引，见构造函数）。 */
    private int colorIndex = ViewpointWaypointColors.DEFAULT_INDEX;
    /** 颜色选择栏是否展开。 */
    private boolean colorPanelOpen = false;

    // ---- 颜色选择栏的布局，全部是"相对 GUI 面板左上角"的偏移（和面板一起算绝对坐标） ----
    /** 选择栏面板相对面板左上角的偏移与尺寸。 */
    private static final int PANEL_OFFSET_X = 14;
    private static final int PANEL_OFFSET_Y = 150;
    private static final int PANEL_WIDTH = 228;
    /**
     * 面板高度 = 内边距 ×2 + 行数 × 行高。
     * 行数 = ceil(颜色数 / 列数)；当前 19 色 6 列 = 4 行，4×11 + 2×2 = 48。
     * <p>
     * 纵向占 +150…+198（含 1px 外框是 +149…+199），整张列表都在 200px 高的面板内，
     * 不会再像改版前那样伸到面板外面被截掉。底部只压到"完成/取消"（+176）的最上面一点点，
     * 而选择栏是最后绘制的，展开时在最上层、收起后按钮完全可用。
     * <p>
     * <b>列数是这里的关键</b>：行数每多一行就多 11px。改版前是 3 列 = 7 行，
     * 底边 +261 直接伸到面板外被截断。5 列虽然也是 4 行，但并排下来会顶到面板右缘；
     * 6 列 × 4 行是 256×200 面板下既放得下、又不会挤扁标签的排法。
     * 加色时记得同步高度，否则最后一行的色块会被面板裁掉。
     */
    private static final int PANEL_HEIGHT = 48;
    /** 面板内边距。 */
    private static final int PANEL_PAD = 2;
    /** 每格尺寸：宽 38 = 色块 13 + 间隙 2 + 文字约 23px（最长颜色名 18px，够用）。 */
    private static final int CELL_WIDTH = 38;
    private static final int CELL_HEIGHT = 11;
    /** 列数；行数由颜色总数算出来。 */
    private static final int COLOR_COLUMNS = 6;
    /** 色块尺寸。 */
    private static final int SWATCH_WIDTH = 13;
    private static final int SWATCH_HEIGHT = 9;

    public ViewpointGui(ViewpointTileEntity tileEntity) {
        this.tileEntity = tileEntity;
        // 只在打开界面时从方块读一次：initGui() 在窗口 resize 时会重跑，不能在那里重置玩家已选的色
        int stored = ViewpointWaypointColors.indexOf(tileEntity.getWaypointColor());
        // 存档里的颜色不是调色板里的色时（只可能来自手改 NBT 或更早的版本），这里直接对齐到默认色：
        // 服务端同样会把它回落成默认色，这样"界面预览"和"最终存进去的值"不会不一致。
        this.colorIndex = (stored >= 0) ? stored : ViewpointWaypointColors.DEFAULT_INDEX;
    }

    @Override
    public void initGui() {
        super.initGui();
        
        // 窗口 resize 会重新调用 initGui()：先存下当前输入内容，重建控件后回填，避免玩家已输入的文字丢失
        String previousName = (this.nameField == null) ? this.tileEntity.getName() : this.nameField.getText();
        String previousMaterial = (this.materialField == null) ? this.tileEntity.getMaterial() : this.materialField.getText();
        boolean nameHadFocus = (this.nameField == null) || this.nameField.isFocused();
        
        int centerX = (this.width - 256) / 2;
        int centerY = (this.height - 200) / 2;
        
        this.nameField = new GuiTextField(0, this.fontRenderer, centerX + 20, centerY + 40, 216, 20);
        this.nameField.setMaxStringLength(ViewpointTileEntity.MAX_NAME_LENGTH);
        this.nameField.setText(previousName);
        this.nameField.setFocused(nameHadFocus);
        
        this.materialField = new GuiTextField(3, this.fontRenderer, centerX + 20, centerY + 64, 176, 20);
        this.materialField.setMaxStringLength(ViewpointMaterial.MAX_LENGTH);
        this.materialField.setText(previousMaterial);
        this.materialField.setFocused(!nameHadFocus);
        
        this.confirmButton = new GuiButton(1, centerX + 20, centerY + 176, 100, 20, I18n.format("gui.done"));
        this.buttonList.add(confirmButton);
        
        this.cancelButton = new GuiButton(2, centerX + 136, centerY + 176, 100, 20, I18n.format("gui.cancel"));
        this.buttonList.add(cancelButton);
        
        this.defaultMaterialButton = new GuiButton(4, centerX + 20, centerY + 88, 60, 18, I18n.format("viewpoint.gui.material.default"));
        this.buttonList.add(defaultMaterialButton);
        
        this.colorButton = new GuiButton(5, centerX + 20, centerY + 130, 100, 20, this.colorButtonLabel());
        this.buttonList.add(colorButton);
        
        this.refreshMaterialStatus();
    }
    
    /**
     * 颜色按钮的文案：只写当前颜色名。
     * <p>
     * 下拉指示的"箭头"不用字符：MC 默认字体里没有 "▼"/"▾" 这类字形，只有启用了 unicode font
     * 才会去加载 CJK 字形（中文语言环境会启用，英文环境不会）—— 用字符就会出现
     * "中文下正常、英文下变乱码/方块"的不一致。所以箭头改成用 {@link #drawDropdownArrow}
     * 按 {@code drawRect} 画出来的实心三角，与字体/语言环境完全无关。
     */
    private String colorButtonLabel() {
        return I18n.format("viewpoint.color." + ViewpointWaypointColors.NAME_KEYS[
                ViewpointWaypointColors.clampIndex(this.colorIndex)]);
    }
    
    /** 当前选中的颜色值（提交给服务端用）。 */
    private int currentWaypointColor() {
        return ViewpointWaypointColors.colorAt(this.colorIndex);
    }
    
    /** 选择栏面板左上角的绝对 X（居中用的同一个 centerX + 固定偏移）。 */
    private int colorPanelX() {
        return (this.width - 256) / 2 + PANEL_OFFSET_X;
    }
    
    /** 选择栏面板左上角的绝对 Y。 */
    private int colorPanelY() {
        return (this.height - 200) / 2 + PANEL_OFFSET_Y;
    }
    
    /** 第 i 个颜色格子的绝对 X。 */
    private int colorCellX(int index) {
        return this.colorPanelX() + PANEL_PAD + (index % COLOR_COLUMNS) * CELL_WIDTH;
    }
    
    /** 第 i 个颜色格子的绝对 Y。 */
    private int colorCellY(int index) {
        return this.colorPanelY() + PANEL_PAD + (index / COLOR_COLUMNS) * CELL_HEIGHT;
    }
    
    /** 鼠标是否落在选择栏面板里（含边框）。 */
    private boolean isOverColorPanel(int mouseX, int mouseY) {
        int x = this.colorPanelX();
        int y = this.colorPanelY();
        return mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= y && mouseY < y + PANEL_HEIGHT;
    }
    
    /**
     * 选中鼠标位置下的颜色。
     *
     * @return true 表示点到了某个颜色格子（已经消费掉这次点击）
     */
    private boolean handleColorPanelClick(int mouseX, int mouseY) {
        for (int i = 0; i < ViewpointWaypointColors.PRESETS.length; i++) {
            int x = this.colorCellX(i);
            int y = this.colorCellY(i);
            if (mouseX >= x && mouseX < x + CELL_WIDTH && mouseY >= y && mouseY < y + CELL_HEIGHT) {
                this.colorIndex = i;
                this.colorButton.displayString = this.colorButtonLabel();
                this.colorPanelOpen = false;
                return true;
            }
        }
        // 点到了面板空白处（比如最后一行的空位）：只关掉，不改颜色
        return false;
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        int centerX = (this.width - 256) / 2;
        int centerY = (this.height - 200) / 2;
        
        this.drawRect(centerX, centerY, centerX + 256, centerY + 200, 0xFF000000);
        this.drawRect(centerX + 4, centerY + 4, centerX + 252, centerY + 196, 0xFF444444);
        
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.title"), centerX + 128 - this.fontRenderer.getStringWidth(I18n.format("viewpoint.gui.title")) / 2, centerY + 15, 0xFFFFFF);
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.name"), centerX + 20, centerY + 28, 0xAAAAAA);
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.material"), centerX + 20, centerY + 52, 0xAAAAAA);
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.waypoint_color"), centerX + 20, centerY + 118, 0xAAAAAA);
        
        this.nameField.drawTextBox();
        this.materialField.drawTextBox();
        
        // 空的材质输入框里显示灰色示例（只在不聚焦时画，避免压住光标）
        if (this.materialField.getText().isEmpty() && !this.materialField.isFocused()) {
            this.fontRenderer.drawString(I18n.format("viewpoint.gui.material.hint"), centerX + 24, centerY + 70, 0x808080);
        }
        
        this.drawMaterialStatus(centerX, centerY);
        
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        // 下面这些都必须放在 super.drawScreen 之后 —— 按钮是在 super 里画的，
        // 先画就会被按钮的底图盖掉（之前"当前色块"就是这么消失的）。
        this.drawMaterialPreview(centerX, centerY);
        this.drawColorButtonContent();
        
        // 颜色选择栏要盖在按钮之上（它展开时会压住"完成/取消"那一行），所以必须最后画
        if (this.colorPanelOpen) {
            this.drawColorPanel(mouseX, mouseY);
        }
    }
    
    /**
     * 在颜色按钮上补画"当前颜色块 + 下拉箭头"。
     * <p>
     * 这两样都不用文字/glyph：MC 默认字体没有 "▼"/"▾" 字形，只有启用了 unicode font
     * 才会加载 CJK 字形（中文环境会启用、英文环境不会），用字符会出现"中文正常、英文乱码"
     * 的不一致，所以箭头直接用 {@code drawRect} 画实心三角。
     * <p>
     * 位置固定相对按钮右缘，与按钮文案（颜色名）居中显示互不干扰：最长颜色名 18px，
     * 居中后离右缘还有约 40px，色块 14 + 箭头 9 排得下。
     */
    private void drawColorButtonContent() {
        if (this.colorButton == null || !this.colorButton.visible) {
            return;
        }
        int right = this.colorButton.x + this.colorButton.width;
        int midY = this.colorButton.y + this.colorButton.height / 2;
        
        // 当前颜色的色块（1px 黑边 + 实色）
        int swatchX = right - 28;
        int swatchY = midY - 5;
        this.drawRect(swatchX - 1, swatchY - 1, swatchX + 15, swatchY + 10, 0xFF000000);
        this.drawRect(swatchX, swatchY, swatchX + 14, swatchY + 9, 0xFF000000 | this.currentWaypointColor());
        
        // 下拉箭头：3 行递减的实心三角（5 → 3 → 1 px 宽），画在按钮右缘内侧
        this.drawDropdownArrow(right - 12, midY - 2, 5, 0xFF000000);
    }
    
    /**
     * 画一个向下收窄的实心三角，用来表示"点击展开"。
     * <p>
     * 不用 "▼"/"v" 这类字符：MC 默认字体没有三角字形，只有启用了 unicode font 才会加载
     * CJK 字形（中文环境会启用、英文环境不会），字符方案会出现"中文正常、英文乱码"的不一致。
     *
     * @param left   三角最上面一行的左边界
     * @param top    三角最上面一行的 y
     * @param width  三角最上面一行的宽度（每往下一行左右各收 1px）
     * @param argb   颜色（含 alpha）
     */
    private void drawDropdownArrow(int left, int top, int width, int argb) {
        int x0 = left;
        int x1 = left + width;
        int row = 0;
        while (x1 - x0 > 0) {
            this.drawRect(x0, top + row, x1, top + row + 1, argb);
            x0++;
            x1--;
            row++;
        }
    }
    
    /** 颜色选择栏：6 列网格，每格 = 色块 + 颜色名，鼠标悬停高亮、当前选中加亮底。 */
    private void drawColorPanel(int mouseX, int mouseY) {
        int x = this.colorPanelX();
        int y = this.colorPanelY();
        
        // 面板：1px 外框 + 深色内底
        this.drawRect(x - 1, y - 1, x + PANEL_WIDTH + 1, y + PANEL_HEIGHT + 1, 0xFF888888);
        this.drawRect(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF101010);
        
        for (int i = 0; i < ViewpointWaypointColors.PRESETS.length; i++) {
            int cellX = this.colorCellX(i);
            int cellY = this.colorCellY(i);
            boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT;
            
            // 底色：悬停用深蓝、当前选中用深灰，便于一眼看出选的是哪个
            if (hovered) {
                this.drawRect(cellX, cellY, cellX + CELL_WIDTH - 1, cellY + CELL_HEIGHT - 1, 0xFF2A4A7A);
            } else if (i == this.colorIndex) {
                this.drawRect(cellX, cellY, cellX + CELL_WIDTH - 1, cellY + CELL_HEIGHT - 1, 0xFF505050);
            }
            
            // 色块：1px 黑边 + 实色，在格子里垂直居中
            int swatchX = cellX + 2;
            int swatchY = cellY + (CELL_HEIGHT - SWATCH_HEIGHT) / 2;
            this.drawRect(swatchX - 1, swatchY - 1, swatchX + SWATCH_WIDTH + 1, swatchY + SWATCH_HEIGHT + 1, 0xFF000000);
            this.drawRect(swatchX, swatchY, swatchX + SWATCH_WIDTH, swatchY + SWATCH_HEIGHT,
                    0xFF000000 | ViewpointWaypointColors.colorAt(i));
            
            // 颜色名：与色块中线对齐；当前选中画成白色，其余灰色
            String label = I18n.format("viewpoint.color." + ViewpointWaypointColors.NAME_KEYS[i]);
            int textY = cellY + (CELL_HEIGHT - this.fontRenderer.FONT_HEIGHT) / 2;
            this.fontRenderer.drawString(label, swatchX + SWATCH_WIDTH + 2, textY,
                    (i == this.colorIndex) ? 0xFFFFFF : 0xCCCCCC);
        }
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // 选择栏展开时，它是"最上层"的控件：先自己处理点击，再决定要不要往下传
        if (this.colorPanelOpen) {
            boolean overPanel = this.isOverColorPanel(mouseX, mouseY);
            if (overPanel) {
                this.handleColorPanelClick(mouseX, mouseY);
            }
            // 点在面板外 = 关闭；点在里面但没选中任何格子（空位）= 也关闭
            if (!overPanel || !this.colorPanelOpen) {
                this.colorPanelOpen = false;
            }
            if (overPanel) {
                // 不要让这次点击穿透到面板底下的按钮/输入框，否则会误触"完成"或被输入框抢焦点
                return;
            }
        }
        
        boolean nameWasFocused = this.nameField.isFocused();
        boolean materialWasFocused = this.materialField.isFocused();
        
        boolean nameClicked = this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        boolean materialClicked = this.materialField.mouseClicked(mouseX, mouseY, mouseButton);
        
        if (nameClicked) {
            this.materialField.setFocused(false);          // 焦点互斥
        } else if (materialClicked) {
            this.nameField.setFocused(false);
        } else if (nameWasFocused || materialWasFocused) {
            // 点到空白处/按钮时 GuiTextField 会顺手把焦点清掉，这里恢复，保证还能继续打字
            this.nameField.setFocused(nameWasFocused);
            this.materialField.setFocused(materialWasFocused);
        }
        
        this.refreshMaterialStatus();
        
        super.mouseClicked(mouseX, mouseY, mouseButton);   // 交给按钮
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // 选择栏展开时 Esc 只收起选择栏，不关整个界面 —— 这是展开式控件的通用预期
        if (this.colorPanelOpen && keyCode == Keyboard.KEY_ESCAPE) {
            this.colorPanelOpen = false;
            return;
        }
        
        super.keyTyped(typedChar, keyCode);
        
        // Tab 只在两个输入框之间切换焦点，不交给输入框处理
        if (keyCode == Keyboard.KEY_TAB) {
            boolean toName = !this.nameField.isFocused();
            this.nameField.setFocused(toName);
            this.materialField.setFocused(!toName);
            return;
        }
        
        if (keyCode == Keyboard.KEY_RETURN) {
            this.handleConfirm();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.handleCancel();
        }
        
        // 输入转给当前聚焦的输入框；材质框输入后实时重算校验状态
        if (this.nameField.isFocused()) {
            this.nameField.textboxKeyTyped(typedChar, keyCode);
        } else if (this.materialField.isFocused()) {
            this.materialField.textboxKeyTyped(typedChar, keyCode);
            this.refreshMaterialStatus();
        }
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == confirmButton) {
            handleConfirm();
        } else if (button == cancelButton) {
            handleCancel();
        } else if (button == defaultMaterialButton) {
            // 恢复默认：清空输入框 = 回到石砖外观
            this.materialField.setText("");
            this.refreshMaterialStatus();
        } else if (button == colorButton) {
            // 点颜色按钮 = 展开全部颜色（选择栏里的每一格才是真正的选择入口）。
            // 这里刻意不用 `!colorPanelOpen` 去 toggle：展开状态下点这个按钮时，
            // mouseClicked 已经在调用 super（→ 走到这里）之前把选择栏关掉了，
            // 若再取反就会立刻重新打开，表现为"按钮点不动"。
            this.colorPanelOpen = true;
        }
    }
    
    private void handleConfirm() {
        String newName = nameField.getText().trim();
        // 材质：空串是合法的"恢复默认"；只有非法（名字不存在/非 MODEL/自我引用）才拦下
        String material = ViewpointMaterial.normalize(materialField.getText());
        boolean materialOk = material.isEmpty() || ViewpointMaterial.resolve(material) != null;
        if (!materialOk) {
            // 非法材质：不发包、不关窗，就地标红提示（refreshMaterialStatus 会细分 UNKNOWN_BLOCK/NOT_MODEL/SELF）
            this.refreshMaterialStatus();
            return;
        }
        // 只要材质合法就无条件发包：名字清空由服务端回落默认名，
        // 否则"清空名字 + 清空材质"这种恢复默认的提交会被静默吞掉
        TeamytzViewpoint.instance.network.sendToServer(
                new ViewpointUpdatePacket(tileEntity.getPos(), newName.isEmpty() ? tileEntity.getName() : newName,
                        material, this.currentWaypointColor()));
        this.mc.player.closeScreen();
    }
    
    private void handleCancel() {
        this.mc.player.closeScreen();
    }
    
    /** 重算实时校验状态；所有可能改变输入框内容的路径（打字/粘贴/恢复默认/鼠标点击）都必须调用它。 */
    private void refreshMaterialStatus() {
        this.currentMaterial = ViewpointMaterial.normalize(this.materialField.getText());
        this.materialStatus = ViewpointMaterial.status(this.currentMaterial);
    }
    
    /** 校验状态行：文案与颜色随 materialStatus 变化（PLAN §8.2）。 */
    private void drawMaterialStatus(int centerX, int centerY) {
        String text;
        int color;
        switch (this.materialStatus) {
            case VALID:
                if (this.previewStack().isEmpty()) {
                    // 合法但该方块没有 ItemBlock（很多技术类方块都是这样）：只提示"无物品图标"
                    text = I18n.format("viewpoint.gui.material.preview.none");
                    color = 0xAAAAAA;
                } else {
                    text = I18n.format("viewpoint.gui.material.valid", this.currentMaterial);
                    color = 0x55FF55;
                }
                break;
            case UNKNOWN_BLOCK:
                text = I18n.format("viewpoint.gui.material.invalid");
                color = 0xFF5555;
                break;
            case NOT_MODEL:
                text = I18n.format("viewpoint.gui.material.invalid.render");
                color = 0xFF5555;
                break;
            case SELF:
                text = I18n.format("viewpoint.gui.material.invalid.self");
                color = 0xFF5555;
                break;
            case DEFAULT:
            default:
                text = I18n.format("viewpoint.gui.material.default_hint");
                color = 0xAAAAAA;
                break;
        }
        // 校验状态行放在"默认"按钮（+88..+106）正下方：+108 起是颜色区，不能再低
        this.fontRenderer.drawString(text, centerX + 20, centerY + 108, color);
    }
    
    /** 预览框 + 方块物品图标（PLAN §8.2）。取不到图标时只画空白预览框，不报错。 */
    private void drawMaterialPreview(int centerX, int centerY) {
        // 预览框相对面板 (202,62)，尺寸 38x24：1px 边框 + 深色内底
        this.drawRect(centerX + 202, centerY + 62, centerX + 240, centerY + 86, 0xFF888888);
        this.drawRect(centerX + 203, centerY + 63, centerX + 239, centerY + 85, 0xFF202020);
        
        ItemStack stack = this.previewStack();
        if (stack.isEmpty()) {
            return;
        }
        
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 100.0F);       // 抬到 GUI 图层上方（renderItemAndEffectIntoGUI 自身还有 z=+100）
        RenderHelper.enableGUIStandardItemLighting();       // 让图标带上正确的 GUI 光照
        
        this.itemRender.renderItemAndEffectIntoGUI(stack, centerX + 214, centerY + 67);
        
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
    
    /** 预览用的物品堆：材质非法/该方块没有对应物品时返回 ItemStack.EMPTY（= 不画图标）。 */
    private ItemStack previewStack() {
        if (this.materialStatus != ViewpointMaterial.Status.VALID) {
            return ItemStack.EMPTY;
        }
        IBlockState state = ViewpointMaterial.resolve(this.currentMaterial);
        if (state == null || state.getBlock() == Blocks.AIR) {
            return ItemStack.EMPTY;
        }
        // 方块没有对应物品时，getItemFromBlock 返回的是 Items.AIR（**不是 null**），
        // 直接构造会得到一个 isEmpty() 为 true 的物品栈，下面照常走"无物品图标"分支。
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        // 物品用的 meta 必须取 damageDropped()，**不能**取 getMetaFromState()：
        // 后者的位含义属于"方块状态"，物品那边不一定接受。典型反例就是树叶——
        //   BlockOldLeaf.getMetaFromState(默认状态) = 0 | (CHECK_DECAY ? 8 : 0) = 8
        //   ItemLeaves.getMetadata(8) 会再 `| 4` 变成 12 → getStateFromMeta(12) 得到
        //   check_decay=true,decayable=false 这个组合，物品模型解析不到 → 回落到原版
        //   missingno 占位图，预览里就显示成一个平铺的紫黑"面"。
        // damageDropped() 是"该方块状态掉落成物品时使用的 meta"，原版创造栏
        // (BlockOldLeaf.getSubBlocks) 用的也是同一个值，所以它构造出来的正是游戏自己认得的那份物品栈。
        return new ItemStack(item, 1, state.getBlock().damageDropped(state));
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
