package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.BoilerBlockEntity;
import dev.futuretech.menu.BoilerMenu;
import dev.futuretech.registry.ModFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class BoilerScreen extends AbstractContainerScreen<BoilerMenu> implements TabbedScreen {
    private static final int TANK_Y = 31, TANK_WIDTH = 16, TANK_HEIGHT = 46;
    /** The heat source's column takes the fuel slot's place, the same height as the tanks beside it. */
    private static final int SOURCE_X = 80, SOURCE_Y = 31, SOURCE_HEIGHT = TANK_HEIGHT;
    private final AnimatedBar water = new AnimatedBar(), steam = new AnimatedBar();
    private final TabStrip tabs;
    private static final String[] STATUS = {"active","no_water","no_fuel","full","disabled","no_lava","no_energy"};
    private final AnimatedBar reserve = new AnimatedBar();
    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title,176,184);
        titleLabelX = inventoryLabelX = 8;
        inventoryLabelY = 90;
        tabs = new TabStrip(new UpgradeTab(menu,font),new SideConfigTab<>(menu,font),new RedstoneControlTab<>(menu,font),new BoilerInfoTab(menu,font));
    }
    @Override public TabStrip tabs() { return tabs; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(graphics,mouseX,mouseY,partialTick);
        int x=leftPos,y=topPos;
        drawPanel(graphics,x,y,imageWidth,imageHeight);
        drawSlots(graphics,x,y,menu.slots);
        tank(graphics,x+36,y+TANK_Y,water,menu.waterStored(),BoilerBlockEntity.WATER_CAPACITY,Fluids.WATER);
        tank(graphics,x+140,y+TANK_Y,steam,menu.steamStored(),BoilerBlockEntity.STEAM_CAPACITY,ModFluids.STEAM.get());
        graphics.fill(x+12,y+49,x+20,y+51,TEXT);
        graphics.fill(x+14,y+51,x+18,y+54,TEXT);
        graphics.fill(x+15,y+54,x+17,y+56,TEXT);
        heatSource(graphics,x,y);
        tabs.render(graphics,x,y,imageWidth,mouseX,mouseY);
    }
    /**
     * What heats the water, drawn where the fuel slot is: on solid fuel the slot stays and a bar
     * under it shows the heat left in the lit item; a lava upgrade puts a lava tank there, an
     * energy upgrade an energy column, and the slot itself is hidden by the menu.
     */
    private void heatSource(GuiGraphicsExtractor graphics,int x,int y) {
        switch(menu.fuelMode()) {
            case BoilerBlockEntity.LAVA -> reserveColumn(graphics,x+SOURCE_X,y+SOURCE_Y,Fluids.LAVA,BoilerBlockEntity.LAVA_CAPACITY);
            case BoilerBlockEntity.ENERGY -> {
                graphics.fill(x+SOURCE_X-1,y+SOURCE_Y-1,x+SOURCE_X+TANK_WIDTH+1,y+SOURCE_Y+SOURCE_HEIGHT+1,0xFF56616D);
                graphics.fill(x+SOURCE_X,y+SOURCE_Y,x+SOURCE_X+TANK_WIDTH,y+SOURCE_Y+SOURCE_HEIGHT,BAR_BACK);
                float fill=reserve.width(menu.reserve(),BoilerBlockEntity.ENERGY_CAPACITY,SOURCE_HEIGHT-2,menu.isSynced());
                drawVerticalGradientBar(graphics,x+SOURCE_X+1,y+SOURCE_Y+SOURCE_HEIGHT-1,TANK_WIDTH-2,fill,ENERGY_START,ENERGY_END);
            }
            default -> {
                graphics.fill(x+74,y+64,x+102,y+70,BAR_BACK);
                int heat=Math.clamp(menu.burnRemaining()*26/menu.burnTotal(),0,26);
                graphics.fill(x+75,y+65,x+75+heat,y+69,0xFFF0A148);
            }
        }
    }
    /** The lava tank in the fuel slot's place: the same column as water and steam. */
    private void reserveColumn(GuiGraphicsExtractor graphics,int x,int y,Fluid fluid,int capacity) {
        graphics.fill(x-1,y-1,x+TANK_WIDTH+1,y+SOURCE_HEIGHT+1,0xFF56616D);
        graphics.fill(x,y,x+TANK_WIDTH,y+SOURCE_HEIGHT,BAR_BACK);
        int height=Math.round(reserve.width(menu.reserve(),capacity,SOURCE_HEIGHT,menu.isSynced()));
        if(height>0) {
            var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            var sprite=model.stillMaterial().sprite();
            int color=model.fluidTintSource()==null ? 0xFFFFFFFF
                    : model.fluidTintSource().colorAsStack(new FluidStack(fluid,FluidType.BUCKET_VOLUME));
            graphics.enableScissor(x,y+SOURCE_HEIGHT-height,x+TANK_WIDTH,y+SOURCE_HEIGHT);
            for(int row=0;row<SOURCE_HEIGHT;row+=16) graphics.blitSprite(RenderPipelines.GUI_TEXTURED,sprite,x,y+row,16,16,color);
            graphics.disableScissor();
        }
        graphics.fill(x,y,x+1,y+SOURCE_HEIGHT,0x30FFFFFF);
        for(int i=1;i<4;i++) { int tickY=y+SOURCE_HEIGHT*i/4; graphics.fill(x+TANK_WIDTH-4,tickY,x+TANK_WIDTH,tickY+1,0xA0FFFFFF); }
    }
    /** A tank column drawn with the fluid's own texture and tint, like the fluid tank's bar. */
    private void tank(GuiGraphicsExtractor graphics,int x,int y,AnimatedBar bar,int amount,int capacity,Fluid fluid) {
        graphics.fill(x-1,y-1,x+TANK_WIDTH+1,y+TANK_HEIGHT+1,0xFF56616D);
        graphics.fill(x,y,x+TANK_WIDTH,y+TANK_HEIGHT,BAR_BACK);
        int height=Math.round(bar.width(amount,capacity,TANK_HEIGHT,menu.isSynced()));
        if(height>0) {
            var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            var sprite=model.stillMaterial().sprite();
            int color=model.fluidTintSource()==null ? 0xFFFFFFFF
                    : model.fluidTintSource().colorAsStack(new FluidStack(fluid,FluidType.BUCKET_VOLUME));
            graphics.enableScissor(x,y+TANK_HEIGHT-height,x+TANK_WIDTH,y+TANK_HEIGHT);
            for(int row=0;row<TANK_HEIGHT;row+=16) graphics.blitSprite(RenderPipelines.GUI_TEXTURED,sprite,x,y+row,16,16,color);
            graphics.disableScissor();
        }
        graphics.fill(x,y,x+1,y+TANK_HEIGHT,0x30FFFFFF);
        for(int i=1;i<4;i++) { int tickY=y+TANK_HEIGHT*i/4; graphics.fill(x+TANK_WIDTH-4,tickY,x+TANK_WIDTH,tickY+1,0xA0FFFFFF); }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        drawMachineTitle(graphics,font,title,menu.mk(),8,titleLabelY,161);
        graphics.text(font,playerInventoryTitle,8,90,TEXT,false);
        centered(graphics,Component.translatable("gui.futuretech.boiler.water"),44,21);
        centered(graphics,Component.translatable("fluid.futuretech.steam"),148,21);
        switch(menu.fuelMode()) {
            case BoilerBlockEntity.LAVA -> centered(graphics,Component.translatable("gui.futuretech.boiler.lava"),88,21);
            case BoilerBlockEntity.ENERGY -> centered(graphics,Component.translatable("gui.futuretech.energy"),88,21);
            default -> centered(graphics,Component.translatable("gui.futuretech.boiler.fuel"),88,33);
        }
        // One line under the columns: the status at the left, the steam rate under the steam tank.
        graphics.text(font,Component.translatable("gui.futuretech.boiler."+STATUS[menu.status()]),8,79,TEXT,false);
        centered(graphics,Component.literal(menu.productionRate()+" mB/t"),148,79);
    }
    private void centered(GuiGraphicsExtractor graphics,Component text,int x,int y) { graphics.text(font,text,x-font.width(text)/2,y,TEXT,false); }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        super.extractTooltip(graphics,mouseX,mouseY);
        if(mouseY>=topPos+TANK_Y-1 && mouseY<topPos+TANK_Y+TANK_HEIGHT+1) {
            if(mouseX>=leftPos+35 && mouseX<leftPos+53) graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",Component.translatable("gui.futuretech.boiler.water"),menu.waterStored(),BoilerBlockEntity.WATER_CAPACITY),mouseX,mouseY);
            if(mouseX>=leftPos+139 && mouseX<leftPos+157) graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",Component.translatable("fluid.futuretech.steam"),menu.steamStored(),BoilerBlockEntity.STEAM_CAPACITY),mouseX,mouseY);
        }
        tabs.extractTooltip(graphics,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) { return tabs.mouseClicked(event) || super.mouseClicked(event,doubleClick); }
}

