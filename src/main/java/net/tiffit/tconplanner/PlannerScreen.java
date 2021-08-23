package net.tiffit.tconplanner;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputMappings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.tiffit.tconplanner.buttons.*;
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.tables.client.SlotInformationLoader;
import slimeknights.tconstruct.tables.client.inventory.library.slots.SlotInformation;
import slimeknights.tconstruct.tables.client.inventory.library.slots.SlotPosition;
import slimeknights.tconstruct.tables.client.inventory.table.TinkerStationScreen;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PlannerScreen extends Screen {

    public static ResourceLocation TEXTURE = new ResourceLocation(TConPlanner.MODID, "textures/gui/planner.png");

    private final TinkerStationScreen child;
    private final List<SlotInformation> tools = new ArrayList<>();
    private final PlannerData data;
    public Blueprint blueprint;
    public int selectedPart = 0;
    public int materialPage = 0;
    public MaterialSort<?> sorter;
    private int left, top, guiWidth, guiHeight;

    private static final int partsOffsetX = 13, partsOffsetY = 15;
    public static final int materialPageSize = 3*9;

    public PlannerScreen(TinkerStationScreen child) {
        super(new StringTextComponent("Tinker's Planner"));
        this.child = child;
        data = TConPlanner.DATA;
        for (SlotInformation info : SlotInformationLoader.getSlotInformationList()) {
            if (!info.isRepair()) {
                tools.add(info);
            }
        }
        try {
            data.load();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    protected void init() {
        guiWidth = 175;
        guiHeight = 204;
        left = width / 2 - guiWidth/2;
        top = height / 2 - guiHeight/2;
        refresh();
    }

    public void refresh(){
        buttons.clear();
        children.clear();
        int toolSpace = 20;
        for (int i = 0; i < tools.size(); i++) {
            SlotInformation info = tools.get(i);
            addButton(new ToolTypeButton(i, left - toolSpace * 5 + (i % 5) * toolSpace, top + (i / 5) * toolSpace + 23, info, this));
        }
        if(blueprint != null){
            List<SlotPosition> positions = blueprint.toolSlotInfo.getPoints();
            for(int i = 0; i < blueprint.parts.length; i++){
                SlotPosition pos = positions.get(i);
                IToolPart part = blueprint.parts[i];
                addButton(new ToolPartButton(i, left + pos.getX() + partsOffsetX, top + pos.getY() + partsOffsetY, part, blueprint.materials[i], this));
            }
            if(selectedPart != -1){
                IToolPart part = blueprint.parts[selectedPart];
                List<IMaterial> usable = MaterialRegistry.getMaterials().stream().filter(part::canUseMaterial).collect(Collectors.toList());
                MaterialStatsId statsId = part.getStatType();
                if(sorter != null)usable.sort((o1, o2) -> sorter.compare(o1, o2, statsId) * -1);
                int loopMin = materialPage*materialPageSize;
                int loopMax = Math.min(usable.size(), (materialPage+1)*materialPageSize);
                for (int i = loopMin; i < loopMax; i++) {
                    int posIndex = i - loopMin;
                    IMaterial mat = usable.get(i);
                    MaterialButton data = new MaterialButton(i, mat, part.withMaterialForDisplay(mat.getIdentifier()), left + (posIndex % 9) * 18 + 8, top + guiHeight - 87 + (posIndex / 9) * 18, this);
                    if(blueprint.materials[selectedPart] == mat)data.selected = true;
                    addButton(data);
                }
                MatPageButton leftPage = new MatPageButton(left + 6, top + guiHeight - 30, -1, this);
                MatPageButton rightPage = new MatPageButton(left + guiWidth - 6 - 37, top + guiHeight - 30, 1, this);
                leftPage.active = materialPage > 0;
                rightPage.active = loopMax < usable.size();
                addButton(leftPage);
                addButton(rightPage);

                Class<? extends IMaterialStats> statClass = MaterialRegistry.getClassForStat(part.getStatType());
                if(statClass != null){
                    List<MaterialSort<?>> sorts = MaterialSort.MAP.getOrDefault(statClass, Lists.newArrayList());
                    int startX = left + guiWidth/2 - 6*sorts.size();
                    for (int i = 0; i < sorts.size(); i++) {
                        MaterialSort<?> sort = sorts.get(i);
                        addButton(new IconButton(startX + i*12, leftPage.y + 3, sort.iconU, sort.iconV, new StringTextComponent("Sort: " + sort.text), this, e -> sort(sort))
                                .withColor(sort == sorter ? Color.WHITE : new Color(0.4f, 0.4f, 0.4f)).withSound(SoundEvents.PAINTING_PLACE));
                    }
                }
            }
            ItemStack result = blueprint.createOutput();
            if(!result.isEmpty()) {
                addButton(new OutputToolButton(left + guiWidth - 34, top + 58, result, this));
                boolean bookmarked = data.isBookmarked(blueprint);
                addButton(new IconButton(left + guiWidth - 33, top + 88, 190 + (bookmarked ? 12 : 0), 78,
                        new StringTextComponent(bookmarked ? "Remove Bookmark" : "Bookmark Item"), this, e -> {if(bookmarked) unbookmarkCurrent(); else bookmarkCurrent();})
                        .withSound(bookmarked ? SoundEvents.UI_STONECUTTER_TAKE_RESULT : SoundEvents.BOOK_PAGE_TURN));
            }
            addButton(new IconButton(left + guiWidth - 70, top + 88, 176, 104,
                    new StringTextComponent("Randomize Materials"), this, e -> randomize())
                    .withSound(SoundEvents.ENDERMAN_TELEPORT));
        }
        for (int i = 0; i < data.saved.size(); i++) {
            Blueprint bookmarked = data.saved.get(i);
            addButton(new BookmarkedButton(i, left + guiWidth + 2 + (i % 5) * toolSpace, top + (i / 5) * toolSpace + 23, bookmarked, this));
        }

    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float p_230430_4_) {
        renderBackground(stack);
        bindTexture();
        this.blit(stack, left, top, 0, 0, guiWidth, guiHeight);
        int sideCenter = (20*5)/2;
        boolean hasBookmarks = data.saved.size() > 0;
        this.blit(stack, left - sideCenter - 45, top, 0, 205, 90, 19);
        if(hasBookmarks)this.blit(stack, left + guiWidth + sideCenter - 45, top, 0, 205, 90, 19);
        drawCenteredString(stack, font, "Tools", left - sideCenter, top + 5, 0xff_90_90_ff);
        if(hasBookmarks)drawCenteredString(stack, font, "Bookmarked", left + guiWidth + sideCenter, top + 5, 0xff_90_90_ff);
        if(blueprint != null) {
            RenderSystem.pushMatrix();
            RenderSystem.translated(left + partsOffsetX + 7, top + partsOffsetY + 22, -100);
            RenderSystem.scalef(3.7F, 3.7F, 1.0F);
            minecraft.getItemRenderer().renderGuiItem(blueprint.toolStack, 0, 0);
            RenderSystem.popMatrix();
            bindTexture();
            RenderSystem.pushMatrix();
            RenderSystem.translated(0, 0, 1);
            int boxX = 13, boxY = 24, boxL = 81;
            if(mouseX > boxX + left && mouseY > boxY + top && mouseX < boxX + left + boxL && mouseY < boxY + top + boxL)
            RenderSystem.color4f(1f, 1f, 1f, 0.75f);
            else RenderSystem.color4f(1f, 1f, 1f, 0.5f);
            RenderSystem.enableBlend();
            this.blit(stack, left + boxX, top + boxY, boxX, boxY, boxL, boxL);
            RenderSystem.popMatrix();
        }
        String title = blueprint == null ? "Select Tool" : blueprint.toolSlotInfo.getToolForRendering().getHoverName().getString();
        drawCenteredString(stack, font, title, left + guiWidth / 2, top + 7, 0xffffffff);

        super.render(stack, mouseX, mouseY, p_230430_4_);
    }

    public void setSelectedTool(int index) {
        blueprint = new Blueprint(tools.get(index));
        this.materialPage = 0;
        sorter = null;
        setSelectedPart(-1);
        refresh();
    }

    public void setSelectedPart(int index) {
        this.selectedPart = index;
        this.materialPage = 0;
        sorter = null;
        refresh();
    }

    public void setPart(IMaterial material){
        blueprint.materials[selectedPart] = material;
        refresh();
    }

    @Override
    public boolean keyPressed(int key, int p_231046_2_, int p_231046_3_) {
        InputMappings.Input mouseKey = InputMappings.getKey(key, p_231046_2_);
        if (super.keyPressed(key, p_231046_2_, p_231046_3_)) {
            return true;
        } else if (minecraft.options.keyInventory.isActiveAndMatches(mouseKey)) {
            this.onClose();
            return true;
        }
        if(key == GLFW.GLFW_KEY_B && blueprint != null && blueprint.isComplete()){
            if(data.isBookmarked(blueprint))unbookmarkCurrent();
            else bookmarkCurrent();
            return true;
        }
        return false;
    }

    public void renderItemTooltip(MatrixStack mstack, ItemStack stack, int x, int y) {
        renderTooltip(mstack, stack, x, y);
    }


    @Override
    public void onClose() {
        minecraft.setScreen(child);
    }

    public static void bindTexture(){
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().getTextureManager().bind(TEXTURE);
    }

    public void bookmarkCurrent(){
        if(blueprint.isComplete()){
            data.saved.add(blueprint);
            try {
                data.refresh();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        refresh();
    }

    public void unbookmarkCurrent(){
        if(blueprint.isComplete()){
            data.saved.removeIf(blueprint1 -> blueprint1.equals(blueprint));
            try {
                data.refresh();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        refresh();
    }

    public void randomize(){
        if(blueprint != null){
            Random r = new Random();
            List<IMaterial> materials = new ArrayList<>(MaterialRegistry.getMaterials());
            for (int i = 0; i < blueprint.parts.length; i++) {
                IToolPart part = blueprint.parts[i];
                List<IMaterial> usable = materials.stream().filter(part::canUseMaterial).collect(Collectors.toList());
                if(usable.size() > 0)blueprint.materials[i] = usable.get(r.nextInt(usable.size()));
            }
            refresh();
        }
    }

    public void sort(MaterialSort<?> sort){
        if(sorter == sort)sorter = null;
        else sorter = sort;
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
