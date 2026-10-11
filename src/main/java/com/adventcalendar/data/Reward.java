package com.adventcalendar.data;

import com.adventcalendar.util.ItemStackJson;
import com.adventcalendar.util.JsonIO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * One door of the calendar.
 *
 * <p>A reward is either an <b>item</b> (the usual case, shown with its real icon in the GUI)
 * or a <b>command</b> that is executed with the claiming player as the command source.
 * Both can be combined: the command runs and the item is given.</p>
 *
 * <p>JSON shape inside {@code rewards.json}:</p>
 * <pre>
 * "1": { "item": { "id": "minecraft:cookie", "count": 3 }, "label": "Cookie", "hidden": false }
 * "24": { "command": "give @p minecraft:diamond 3", "item": { "id": "minecraft:diamond_block" } }
 * </pre>
 */
public final class Reward {
    private ItemStack stack = ItemStack.EMPTY;
    private String command = "";
    private String label = "";
    private boolean hidden;
    /** Optional skin for player heads (username or skin URL). */
    private String skin = "";

    public Reward() {
    }

    public static Reward of(ItemStack stack) {
        Reward reward = new Reward();
        reward.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        return reward;
    }

    public static Reward ofCommand(String command) {
        Reward reward = new Reward();
        reward.command = command == null ? "" : command;
        return reward;
    }

    @Nullable
    public static Reward fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            ItemStack stack = ItemStackJson.fromJson(element);
            return stack.isEmpty() ? null : Reward.of(stack);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        Reward reward = new Reward();
        reward.stack = ItemStackJson.fromJson(json.has("item") ? json.get("item")
                : json.has("stack") ? json.get("stack") : json);
        reward.command = JsonIO.string(json, "command", "");
        reward.label = JsonIO.string(json, "label", "");
        reward.hidden = JsonIO.bool(json, "hidden", false);
        reward.skin = JsonIO.string(json, "skin", "");
        if (reward.stack.isEmpty() && reward.command.isEmpty()) {
            return null;
        }
        return reward;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject item = ItemStackJson.toJson(stack);
        if (item != null) {
            json.add("item", item);
        }
        if (!command.isEmpty()) {
            json.addProperty("command", command);
        }
        if (!label.isEmpty()) {
            json.addProperty("label", label);
        }
        if (hidden) {
            json.addProperty("hidden", true);
        }
        if (!skin.isEmpty()) {
            json.addProperty("skin", skin);
        }
        return json;
    }

    public boolean isEmpty() {
        return stack.isEmpty() && command.isEmpty();
    }

    /** True when this reward executes a command instead of (or in addition to) an item. */
    public boolean isCommand() {
        return !command.isEmpty();
    }

    public ItemStack item() {
        return stack;
    }

    public void setItem(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public String command() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String skin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin == null ? "" : skin;
    }

    /** Stack used to paint the door in the GUIs (a command block for command rewards). */
    public ItemStack displayStack() {
        return stack.isEmpty() ? new ItemStack(Items.COMMAND_BLOCK) : stack;
    }

    /** Player facing name of the reward. */
    public Component displayName() {
        if (!label.isEmpty()) {
            return Component.literal(label);
        }
        if (isCommand()) {
            return Component.literal("/" + command);
        }
        return displayStack().getHoverName();
    }

    public Reward copy() {
        Reward copy = new Reward();
        copy.stack = stack.copy();
        copy.command = command;
        copy.label = label;
        copy.hidden = hidden;
        copy.skin = skin;
        return copy;
    }
}
