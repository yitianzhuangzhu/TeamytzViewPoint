package com.ytzzhu.teamytzviewpoint.gui;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.network.ViewpointUpdatePacket;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class ViewpointGui extends GuiScreen {
    private ViewpointTileEntity tileEntity;
    private GuiTextField nameField;
    private GuiButton confirmButton;
    private GuiButton cancelButton;
    
    public ViewpointGui(ViewpointTileEntity tileEntity) {
        this.tileEntity = tileEntity;
    }
    
    @Override
    public void initGui() {
        super.initGui();
        
        int centerX = (this.width - 256) / 2;
        int centerY = (this.height - 128) / 2;
        
        this.nameField = new GuiTextField(0, this.fontRenderer, centerX + 20, centerY + 40, 216, 20);
        this.nameField.setMaxStringLength(32);
        this.nameField.setText(tileEntity.getName());
        this.nameField.setFocused(true);
        
        this.confirmButton = new GuiButton(1, centerX + 20, centerY + 80, 100, 20, I18n.format("gui.done"));
        this.buttonList.add(confirmButton);
        
        this.cancelButton = new GuiButton(2, centerX + 136, centerY + 80, 100, 20, I18n.format("gui.cancel"));
        this.buttonList.add(cancelButton);
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        int centerX = (this.width - 256) / 2;
        int centerY = (this.height - 128) / 2;
        
        this.drawRect(centerX, centerY, centerX + 256, centerY + 128, 0xFF000000);
        this.drawRect(centerX + 4, centerY + 4, centerX + 252, centerY + 124, 0xFF444444);
        
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.title"), centerX + 128 - this.fontRenderer.getStringWidth(I18n.format("viewpoint.gui.title")) / 2, centerY + 15, 0xFFFFFF);
        this.fontRenderer.drawString(I18n.format("viewpoint.gui.name"), centerX + 20, centerY + 28, 0xAAAAAA);
        
        this.nameField.drawTextBox();
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        
        if (keyCode == Keyboard.KEY_RETURN) {
            this.handleConfirm();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.handleCancel();
        }
        
        this.nameField.textboxKeyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == confirmButton) {
            handleConfirm();
        } else if (button == cancelButton) {
            handleCancel();
        }
    }
    
    private void handleConfirm() {
        String newName = nameField.getText().trim();
        if (!newName.isEmpty()) {
            TeamytzViewpoint.instance.network.sendToServer(new ViewpointUpdatePacket(tileEntity.getPos(), newName));
        }
        this.mc.player.closeScreen();
    }
    
    private void handleCancel() {
        this.mc.player.closeScreen();
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
