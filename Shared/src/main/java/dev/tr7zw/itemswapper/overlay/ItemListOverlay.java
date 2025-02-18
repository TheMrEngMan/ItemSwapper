package dev.tr7zw.itemswapper.overlay;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.tr7zw.itemswapper.ConfigManager;
import dev.tr7zw.itemswapper.ItemSwapperMod;
import dev.tr7zw.itemswapper.util.ItemUtil;
import dev.tr7zw.itemswapper.util.NetworkLogic;
import dev.tr7zw.itemswapper.util.ItemUtil.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

public class ItemListOverlay extends XTOverlay {

    private static final ResourceLocation SELECTION_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/selection.png");
    private static final ResourceLocation BOTTOM_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_bottom_slot.png");
    private static final ResourceLocation MIDDLE_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_middle_slot.png");
    private static final ResourceLocation TOP_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_top_slot.png");
    private static final ResourceLocation SINGLE_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_single_slot.png");
    private static final ResourceLocation MIDDLE_TOP_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_middle_continue_top_slot.png");
    private static final ResourceLocation MIDDLE_BOTTOM_LOCATION = new ResourceLocation("itemswapper",
            "textures/gui/list_middle_continue_bottom_slot.png");
    private static final double entrySize = 33;
    private static final int yOffset = 75;
    private static final int slotSize = 18;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final ItemRenderer itemRenderer = minecraft.getItemRenderer();
    private Item[] itemSelection;
    private List<Slot> entries = new ArrayList<>();
    private int selectedEntry = 0;
    private double selectY = 0;

    public ItemListOverlay(Item[] itemSelection) {
        this.itemSelection = itemSelection;
        refreshList();
    }

    @Override
    public void render(PoseStack poseStack, int paramInt1, int paramInt2, float paramFloat) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        List<Runnable> itemRenderList = new ArrayList<>();
        List<Runnable> lateRenderList = new ArrayList<>();
        int limit = Math.max(5, (minecraft.getWindow().getGuiScaledHeight() - yOffset) / slotSize / 2);
        int originX = minecraft.getWindow().getGuiScaledWidth() / 2 - slotSize * 5;
        int originY = minecraft.getWindow().getGuiScaledHeight() - yOffset + (Math.max(0, selectedEntry - limit/2) * slotSize);
        int start = Math.max(0, selectedEntry - limit/2);
        for (int i = start; i < entries.size() && i < start + limit; i++) {
            boolean endTop = i == entries.size() - 1;
            boolean endBottom = i == 0;
            boolean midBottom = i == start;
            boolean midTop = i == start + limit - 1;
            if(endTop && endBottom) {
                RenderSystem.setShaderTexture(0, SINGLE_LOCATION);
            } else if (endBottom){
                RenderSystem.setShaderTexture(0, BOTTOM_LOCATION);
            } else if (endTop) {
                RenderSystem.setShaderTexture(0, TOP_LOCATION);
            } else if (midTop) {
                RenderSystem.setShaderTexture(0, MIDDLE_TOP_LOCATION);
            } else if (midBottom) {
                RenderSystem.setShaderTexture(0, MIDDLE_BOTTOM_LOCATION);
            } else {
                RenderSystem.setShaderTexture(0, MIDDLE_LOCATION);
            }
            renderEntry(poseStack, i, originX, originY - slotSize * i, itemRenderList, lateRenderList);
        }
        itemRenderList.forEach(Runnable::run);
        float blit = this.itemRenderer.blitOffset;
        this.itemRenderer.blitOffset += 300;
        lateRenderList.forEach(Runnable::run);
        this.itemRenderer.blitOffset = blit;
    }

    @Override
    public void handleInput(double x, double y) {
        selectY -= y;
        selectY = Mth.clamp(selectY, 0, entries.size() * entrySize - 1);
        refreshList();
    }
    
    @Override
    public void onScroll(double signum) {
        selectY += signum * entrySize;
        selectY = Mth.clamp(selectY, 0, entries.size() * entrySize - 1);
        refreshList();
    }

    private void refreshList() {
        entries.clear();
        // first slot is always the current item
        entries.add(new Slot(-1, minecraft.player.getInventory().selected, minecraft.player.getInventory().getSelected()));
        for (Item item : itemSelection) {
            List<Slot> ids = ItemUtil.findSlotsMatchingItem(item, false, ConfigManager.getInstance().getConfig().ignoreHotbar);
            for (Slot id : ids) {
                if (!entries.contains(id)) {
                    entries.add(id);
                }
            }
        }
        selectY = Mth.clamp(selectY, 0, entries.size() * entrySize - 1);
        selectedEntry = (int) (selectY / entrySize);
    }

    @Override
    public void handleSwitchSelection() {

    }

    @Override
    public void onClose() {
        if (selectedEntry != 0) {
            Slot slot = entries.get(selectedEntry);
            if(slot.inventory() == -1) {
                int hudSlot = ItemUtil.inventorySlotToHudSlot(slot.slot());
                this.minecraft.gameMode.handleInventoryMouseClick(minecraft.player.inventoryMenu.containerId, hudSlot,
                        minecraft.player.getInventory().selected,
                        ClickType.SWAP, this.minecraft.player);
            } else {
                NetworkLogic.swapItem(slot.inventory(), slot.slot());
            }
        }
    }

    private void renderEntry(PoseStack poseStack, int id, int x, int y, List<Runnable> itemRenderList, List<Runnable> lateRenderList) {
        blit(poseStack, x, y, 0, 0, 24, 24, 24, 24);
        // dummy item code
        Slot slot = entries.get(id);
        if (selectedEntry == id) {
            itemRenderList = lateRenderList;
            lateRenderList.add(() -> {
                float blit = getBlitOffset();
                setBlitOffset((int) this.itemRenderer.blitOffset);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, SELECTION_LOCATION);
                blit(poseStack, x, y, 0, 0, 24, 24, 24, 24);
                setBlitOffset((int) blit);
            });
        }
        itemRenderList.add(() -> {
            renderSlot(x + 4, y + 4, minecraft.player, slot.item(), 1);
            drawString(poseStack, minecraft.font, getDisplayname(slot.item()),
                    x + 25, y + 11, -1);
        });
    }
    
    private Component getDisplayname(ItemStack item) {
        if(item.hasCustomHoverName()) {
            return item.getHoverName();
        }
        if(item.getItem() == Items.POTION || item.getItem() == Items.SPLASH_POTION || item.getItem() == Items.LINGERING_POTION) {
            List<MobEffectInstance> effects = PotionUtils.getPotion(item).getEffects();
            if(!effects.isEmpty()) {
                MutableComponent comp = formatEffect(effects.get(0));
                if(effects.size() >= 2) {
                    comp.append(", ").append(formatEffect(effects.get(1)));
                }
                return comp;
            }
        }
        return item.getHoverName();
    }
    
    private MutableComponent formatEffect(MobEffectInstance effect) {
        MutableComponent comp = Component.empty().append(effect.getEffect().getDisplayName());
        if(effect.getAmplifier() > 1) {
            comp.append(" ").append(Component.translatable("potion.potency." + effect.getAmplifier()));
        }
        if(effect.getDuration() > 1) {
            comp.append(" (").append(Component.literal(StringUtil.formatTickDuration(effect.getDuration()))).append(")");
        }
        return comp;
    }

    private void renderSlot(int x, int y, Player arg, ItemStack arg2, int k) {
        if (!arg2.isEmpty()) {
            this.itemRenderer.renderAndDecorateItem(arg, arg2, x, y, k);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            this.itemRenderer.renderGuiItemDecorations(this.minecraft.font, arg2, x, y);
        }
    }

}
