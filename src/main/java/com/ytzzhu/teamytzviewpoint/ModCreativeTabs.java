package com.ytzzhu.teamytzviewpoint;

import com.ytzzhu.teamytzviewpoint.items.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

/**
 * 本模组自己的创造模式物品栏。
 * <p>
 * 用 {@code CreativeTabs(String)} 单参构造：它内部会调 {@code getNextID()} 自己分配索引，
 * 不要手写 CreativeTabs(int, String) 的固定索引（会和别的 mod 撞页）。
 * <p>
 * 标签页文案的 lang key 由 {@code CreativeTabs.getTranslatedTabLabel()} 拼成
 * {@code "itemGroup." + label}，也就是 {@code itemGroup.teamytzviewpoint}，
 * en_us.lang / zh_cn.lang 里各有一条。
 * <p>
 * 注意：{@link #getTabIconItem()} 里引用 {@code ModItems} 只会在"创造模式界面第一次画标签页"时
 * 才被求值，所以构造函数本身不持有任何物品引用 —— 这样 ModBlocks / ModItems 的静态初始化
 * 与本类的静态初始化之间不会形成循环。
 */
public class ModCreativeTabs {

    public static final CreativeTabs VIEWPOINT = new CreativeTabs("teamytzviewpoint") {
        @Override
        public ItemStack getTabIconItem() {
            return new ItemStack(ModItems.VIEWPOINT_WRENCH);
        }
    };

    private ModCreativeTabs() {
    }
}
