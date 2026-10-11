package com.adventcalendar.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Christmas flavoured starter rewards used by the "Fill sample rewards" button of the admin
 * GUI and by {@code /advent sample}. Only the empty days are filled.
 */
public final class SampleRewards {
    private record Sample(Item item, int count, String name, int color) {
    }

    private static final List<Sample> SAMPLES = List.of(
            new Sample(Items.COOKIE, 5, "Christmas Cookie", 0),
            new Sample(Items.SWEET_BERRIES, 8, "Frost Berries", 0),
            new Sample(Items.BREAD, 4, "Fresh Bread", 0),
            new Sample(Items.COCOA_BEANS, 6, "Hot Chocolate Mix", 0),
            new Sample(Items.SNOWBALL, 16, "Snowball Stash", 0),
            new Sample(Items.MELON_SLICE, 8, "Melon Slices", 0),
            new Sample(Items.HONEY_BOTTLE, 2, "Honey Jar", 0),
            new Sample(Items.PUMPKIN_PIE, 2, "Pumpkin Pie", 0),
            new Sample(Items.CANDLE, 4, "Winter Candles", 0),
            new Sample(Items.LANTERN, 2, "Winter Lantern", 0),
            new Sample(Items.WRITABLE_BOOK, 1, "Wish List", 0),
            new Sample(Items.MUSIC_DISC_CAT, 1, "Advent Carol", 0),
            new Sample(Items.BELL, 1, "Jingle Bell", 0),
            new Sample(Items.LEATHER_CHESTPLATE, 1, "Knitted Sweater", 0xB01B24),
            new Sample(Items.FIREWORK_ROCKET, 8, "Fireworks", 0),
            new Sample(Items.GOLD_INGOT, 6, "Bag of Gold", 0),
            new Sample(Items.AMETHYST_SHARD, 8, "Ice Crystals", 0),
            new Sample(Items.NAME_TAG, 1, "Name Tag", 0),
            new Sample(Items.NOTE_BLOCK, 2, "Jingle Blocks", 0),
            new Sample(Items.GLOWSTONE, 4, "Glowstone", 0),
            new Sample(Items.EMERALD, 8, "Emeralds", 0),
            new Sample(Items.DIAMOND, 4, "Diamonds", 0),
            new Sample(Items.ENCHANTED_GOLDEN_APPLE, 1, "Golden Apple", 0),
            new Sample(Items.ELYTRA, 1, "Sleigh Wings", 0));

    private SampleRewards() {
    }

    /** Fills every empty day up to {@code days}. Returns how many rewards were created. */
    public static int fill(RewardsFile rewards, int days) {
        int created = 0;
        for (int day = 1; day <= days; day++) {
            if (rewards.get(day) != null) {
                continue;
            }
            rewards.set(day, Reward.of(build(SAMPLES.get((day - 1) % SAMPLES.size()))));
            created++;
        }
        return created;
    }

    private static ItemStack build(Sample sample) {
        ItemStack stack = new ItemStack(sample.item(), Math.max(1, sample.count()));
        if (sample.color() != 0 && stack.getItem() instanceof DyeableLeatherItem dyeable) {
            dyeable.setColor(stack, sample.color());
        }
        stack.setHoverName(Component.literal(sample.name()).withStyle(ChatFormatting.GOLD));
        return stack;
    }
}
