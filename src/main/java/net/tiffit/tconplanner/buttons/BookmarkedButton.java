package net.tiffit.tconplanner.buttons;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.tiffit.tconplanner.Blueprint;
import net.tiffit.tconplanner.PlannerScreen;

public class BookmarkedButton extends Button {

    private final PlannerScreen parent;
    private final ItemStack stack;
    private final int index;
    private final Blueprint blueprint;
    private boolean selected;

    public BookmarkedButton(int index, int x, int y, Blueprint blueprint, PlannerScreen parent){
        super(x, y, 18, 18, new StringTextComponent(""), button -> {
            parent.blueprint = blueprint.clone();
            parent.setSelectedPart(-1);
            parent.refresh();
        });
        this.index = index;
        this.blueprint = blueprint;
        this.parent = parent;
        stack = blueprint.createOutput();
        this.selected = parent.blueprint != null && parent.blueprint.equals(blueprint);
    }

    @Override
    public void renderButton(MatrixStack stack, int mouseX, int mouseY, float p_230431_4_) {
        PlannerScreen.bindTexture();
        RenderSystem.enableBlend();
        parent.blit(stack, x, y, 213, 41 + (selected ? 18 : 0), 18, 18);
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        renderer.renderGuiItem(this.stack, x + 1, y + 1);
        if(isHovered){
            renderToolTip(stack, mouseX, mouseY);
        }
    }

    @Override
    public void renderToolTip(MatrixStack stack, int mouseX, int mouseY) {
        parent.renderItemTooltip(stack, this.stack, mouseX, mouseY);
    }
}
