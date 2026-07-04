package com.charybdis.Neon_Lights;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Neon_Lights.MODID)
public class Neon_Lights {
    public static final String MODID = "neonlight";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<NeonSignBlock> NEON_SIGN = BLOCKS.register("neon_sign",
            () -> new NeonSignBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(NeonSignBlock.LIT) ? NeonSignBlock.LIT_LIGHT_LEVEL : 0)));
    public static final DeferredItem<BlockItem> NEON_SIGN_ITEM = ITEMS.registerSimpleBlockItem("neon_sign", NEON_SIGN);

    public static final DeferredBlock<NeonSignBlock> RAINBOW_NEON_SIGN = BLOCKS.register("rainbow_neon_sign",
            () -> new NeonSignBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(NeonSignBlock.LIT) ? NeonSignBlock.LIT_LIGHT_LEVEL : 0)));
    public static final DeferredItem<BlockItem> RAINBOW_NEON_SIGN_ITEM = ITEMS.registerSimpleBlockItem("rainbow_neon_sign", RAINBOW_NEON_SIGN);

    public static final Map<DyeColor, DeferredBlock<NeonSignBlock>> COLORED_SIGNS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredItem<BlockItem>> COLORED_SIGN_ITEMS = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName() + "_neon_sign";
            DeferredBlock<NeonSignBlock> block = BLOCKS.register(name,
                    () -> new NeonSignBlock(BlockBehaviour.Properties.of()
                            .strength(5.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(NeonSignBlock.LIT) ? NeonSignBlock.LIT_LIGHT_LEVEL : 0)));
            COLORED_SIGNS.put(color, block);
            COLORED_SIGN_ITEMS.put(color, ITEMS.registerSimpleBlockItem(name, block));
        }
    }

    public static final DeferredItem<Item> NEON_DESIGN_TOOL = ITEMS.register("neon_design_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeonSignBlockEntity>> NEON_SIGN_BE =
            BLOCK_ENTITIES.register("neon_sign", () -> {
                Block[] all = Stream.concat(
                        Stream.of(NEON_SIGN.get(), RAINBOW_NEON_SIGN.get()),
                        COLORED_SIGNS.values().stream().map(DeferredBlock::get)
                ).toArray(Block[]::new);
                return BlockEntityType.Builder.of(NeonSignBlockEntity::new, all).build(null);
            });

    public static DyeColor colorOf(Block block) {
        for (Map.Entry<DyeColor, DeferredBlock<NeonSignBlock>> entry : COLORED_SIGNS.entrySet()) {
            if (entry.getValue().get() == block) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean isRainbow(Block block) {
        return block == RAINBOW_NEON_SIGN.get();
    }

    /** Resolves the dye color for frame/cluster matching; base neon_sign counts as white. */
    public static DyeColor resolveSignColor(Block block) {
        if (!(block instanceof NeonSignBlock)) {
            return null;
        }
        if (isRainbow(block)) {
            return null;
        }
        DyeColor color = colorOf(block);
        return color == null ? DyeColor.WHITE : color;
    }

    public static boolean sameSignColor(Block a, Block b) {
        if (!(a instanceof NeonSignBlock) || !(b instanceof NeonSignBlock)) {
            return false;
        }
        if (isRainbow(a) || isRainbow(b)) {
            return isRainbow(a) && isRainbow(b);
        }
        return resolveSignColor(a) == resolveSignColor(b);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEON_TAB = CREATIVE_MODE_TABS.register("neon_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.neonlight"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> NEON_SIGN_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(NEON_SIGN_ITEM.get());
                for (DyeColor color : DyeColor.values()) {
                    output.accept(COLORED_SIGN_ITEMS.get(color).get());
                }
                output.accept(RAINBOW_NEON_SIGN_ITEM.get());
                output.accept(NEON_DESIGN_TOOL.get());
            }).build());

    public Neon_Lights(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(SetSignLetterPayload::register);
        modEventBus.addListener(SetSignCanvasPayload::register);
        modEventBus.addListener(SetGlyphColorPayload::register);
    }
}
