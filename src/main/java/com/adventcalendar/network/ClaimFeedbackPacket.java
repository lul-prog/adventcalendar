package com.adventcalendar.network;

import com.adventcalendar.client.ClientHooks;
import com.adventcalendar.data.ClaimResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: result of a claim attempt plus the claimed stack, which drives the
 * happy animation of the calendar GUI (the sound and the chat line are sent by the server).
 */
public class ClaimFeedbackPacket {
    public final int day;
    public final ClaimResult result;
    public final int claimedMask;
    public final int pendingCount;
    public final ItemStack granted;

    public ClaimFeedbackPacket(int day, ClaimResult result, int claimedMask, int pendingCount, ItemStack granted) {
        this.day = day;
        this.result = result;
        this.claimedMask = claimedMask;
        this.pendingCount = pendingCount;
        this.granted = granted == null ? ItemStack.EMPTY : granted;
    }

    public static void encode(ClaimFeedbackPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.day);
        buf.writeVarInt(msg.result.ordinal());
        buf.writeInt(msg.claimedMask);
        buf.writeVarInt(msg.pendingCount);
        buf.writeItem(msg.granted);
    }

    public static ClaimFeedbackPacket decode(FriendlyByteBuf buf) {
        int day = buf.readVarInt();
        int ordinal = buf.readVarInt();
        ClaimResult[] values = ClaimResult.values();
        ClaimResult result = ordinal >= 0 && ordinal < values.length ? values[ordinal] : ClaimResult.SUCCESS;
        int claimedMask = buf.readInt();
        int pendingCount = buf.readVarInt();
        ItemStack granted = buf.readItem();
        return new ClaimFeedbackPacket(day, result, claimedMask, pendingCount, granted);
    }

    public static void handle(ClaimFeedbackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.onClaimFeedback(msg)));
        ctx.get().setPacketHandled(true);
    }
}
