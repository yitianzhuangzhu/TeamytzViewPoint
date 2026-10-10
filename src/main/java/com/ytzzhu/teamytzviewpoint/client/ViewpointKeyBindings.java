package com.ytzzhu.teamytzviewpoint.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * 本模组的键位定义。
 * <p>
 * 分类名与按键说明都在 lang 里（{@code key.categories.teamytzviewpoint} / {@code key.teamytzviewpoint.trigger}），
 * 会出现在游戏的"控制"界面里，玩家可以自行改键。
 * <p>
 * 想拿"玩家当前设的那个键"用于显示时，用 {@link KeyBinding#getDisplayName()}：
 * 它的实现是 {@code getKeyModifier().getLocalizedComboName(getKeyCode())}，
 * 会跟随改键、带上修饰键、并按语言本地化——正是设置界面里显示的那个名字。
 * 不要用 {@code Keyboard.getKeyName(...)} 自己拼（那样会丢掉修饰键与本地化）。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointKeyBindings {

    /** 键位分类（"控制"界面里的分组名）。 */
    public static final String CATEGORY = "key.categories.teamytzviewpoint";

    /** 站在鸟瞰点方块上时按这个键触发鸟瞰。默认 V。 */
    public static final KeyBinding TRIGGER = new KeyBinding("key.teamytzviewpoint.trigger", Keyboard.KEY_V, CATEGORY);

    private ViewpointKeyBindings() {
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(TRIGGER);
    }
}
