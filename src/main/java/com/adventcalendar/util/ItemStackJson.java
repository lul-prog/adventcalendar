package com.adventcalendar.util;

import com.adventcalendar.AdventCalendar;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Converts {@link ItemStack}s to and from the small JSON shape used inside the mod's data
 * files:
 *
 * <pre>
 * "item": { "id": "minecraft:cookie", "count": 3, "nbt": "{display:{Name:'...'}}" }
 * </pre>
 *
 * The shorthand {@code "item": "minecraft:cookie"} is accepted as well, which keeps the
 * files pleasant to edit by hand.
 */
public final class ItemStackJson {
    private ItemStackJson() {
    }

    @Nullable
    public static JsonObject toJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        JsonObject json = new JsonObject();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        json.addProperty("id", id == null ? "minecraft:air" : id.toString());
        json.addProperty("count", stack.getCount());
        CompoundTag tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            json.addProperty("nbt", tag.toString());
        }
        return json;
    }

    public static ItemStack fromJson(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        if (element.isJsonPrimitive()) {
            return simple(element.getAsString(), 1);
        }
        if (!element.isJsonObject()) {
            return ItemStack.EMPTY;
        }
        JsonObject json = element.getAsJsonObject();
        String id = JsonIO.string(json, "id", JsonIO.string(json, "item", JsonIO.string(json, "stack", "")));
        if (id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int count = Math.max(1, JsonIO.intValue(json, "count", 1));
        ItemStack stack = simple(id, count);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String nbt = JsonIO.string(json, "nbt", JsonIO.string(json, "tag", ""));
        if (!nbt.isEmpty()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbt);
                stack.setTag(tag);
            } catch (Exception e) {
                AdventCalendar.LOGGER.warn("Ignoring invalid nbt '{}' for item {}: {}", nbt, id, e.toString());
            }
        }
        return stack;
    }

    private static ItemStack simple(String idText, int count) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            AdventCalendar.LOGGER.warn("Ignoring unknown item id '{}'", idText);
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            AdventCalendar.LOGGER.warn("Ignoring unknown item id '{}'", idText);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, Math.min(count, item.getMaxStackSize() * 8)));
    }
}
