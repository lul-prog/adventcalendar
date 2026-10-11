package com.adventcalendar.network;

import com.adventcalendar.AdventCalendar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's network channel and the small send helpers used all over the code. */
public final class ModNetwork {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AdventCalendar.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        // server -> client
        CHANNEL.registerMessage(id++, CalendarSyncPacket.class,
                CalendarSyncPacket::encode, CalendarSyncPacket::decode, CalendarSyncPacket::handle);
        CHANNEL.registerMessage(id++, ClaimFeedbackPacket.class,
                ClaimFeedbackPacket::encode, ClaimFeedbackPacket::decode, ClaimFeedbackPacket::handle);
        CHANNEL.registerMessage(id++, OpenGuiPacket.class,
                OpenGuiPacket::encode, OpenGuiPacket::decode, OpenGuiPacket::handle);
        // client -> server
        CHANNEL.registerMessage(id++, CalendarActionPacket.class,
                CalendarActionPacket::encode, CalendarActionPacket::decode, CalendarActionPacket::handle);
        CHANNEL.registerMessage(id++, AdminEditPacket.class,
                AdminEditPacket::encode, AdminEditPacket::decode, AdminEditPacket::handle);
        CHANNEL.registerMessage(id++, DebugActionPacket.class,
                DebugActionPacket::encode, DebugActionPacket::decode, DebugActionPacket::handle);
        CHANNEL.registerMessage(id++, NpcConfigPacket.class,
                NpcConfigPacket::encode, NpcConfigPacket::decode, NpcConfigPacket::handle);
    }

    public static <MSG> void sendTo(ServerPlayer player, MSG message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToAll(MSG message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }
}
