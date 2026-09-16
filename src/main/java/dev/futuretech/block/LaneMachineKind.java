package dev.futuretech.block;

import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.menu.LaneMachineMenu;
import dev.futuretech.recipe.LaneMachineRecipe;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The machines that run one single-item recipe per lane: the crusher and the sawmill share the
 * block, block entity, menu and screen; only the recipe book, the name and the particles differ.
 * Registry lookups are methods, not fields, so the enum can exist before anything is registered.
 */
public enum LaneMachineKind {
    CRUSHER("crusher"),
    SAWMILL("sawmill");

    public final String id;
    /** The translation key of the block and the title of its screen. */
    public final String nameKey;

    LaneMachineKind(String id) {
        this.id = id;
        this.nameKey = "block.futuretech." + id;
    }

    public LaneMachineBlock block() {
        return switch (this) {
            case CRUSHER -> ModBlocks.CRUSHER.get();
            case SAWMILL -> ModBlocks.SAWMILL.get();
        };
    }

    public BlockEntityType<LaneMachineBlockEntity> blockEntityType() {
        return switch (this) {
            case CRUSHER -> ModBlockEntities.CRUSHER.get();
            case SAWMILL -> ModBlockEntities.SAWMILL.get();
        };
    }

    public MenuType<LaneMachineMenu> menuType() {
        return switch (this) {
            case CRUSHER -> ModMenus.CRUSHER.get();
            case SAWMILL -> ModMenus.SAWMILL.get();
        };
    }

    public RecipeType<LaneMachineRecipe> recipeType() {
        return switch (this) {
            case CRUSHER -> ModRecipes.CRUSHING.get();
            case SAWMILL -> ModRecipes.SAWING.get();
        };
    }

    public RecipeSerializer<LaneMachineRecipe> serializer() {
        return switch (this) {
            case CRUSHER -> ModRecipes.CRUSHING_SERIALIZER.get();
            case SAWMILL -> ModRecipes.SAWING_SERIALIZER.get();
        };
    }

    public RecipeBookCategory book() {
        return switch (this) {
            case CRUSHER -> ModRecipes.CRUSHER_BOOK.get();
            case SAWMILL -> ModRecipes.SAWMILL_BOOK.get();
        };
    }

    /** What a working machine throws out at the intake: sparks off the rollers, sawdust off the blade. */
    public void addWorkingParticle(Level level, BlockPos pos, Direction facing, RandomSource random) {
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.53;
        double y = pos.getY() + 0.35;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.53;
        switch (this) {
            case CRUSHER -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
            case SAWMILL -> level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    x, y, z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        }
    }
}
