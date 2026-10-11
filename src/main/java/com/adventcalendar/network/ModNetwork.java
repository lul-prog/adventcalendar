package com.adventcalendar.network;

import com.adventcalendar.AdventCalendar;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod (API de Forge 1.20.1). */
public class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AdventCalendar.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ClaimDayPacket.class,
                ClaimDayPacket::encode, ClaimDayPacket::decode, ClaimDayPacket::handle);
        CHANNEL.registerMessage(id++, OpenCalendarPacket.class,
                OpenCalendarPacket::encode, OpenCalendarPacket::decode, OpenCalendarPacket::handle);
        CHANNEL.registerMessage(id++, NpcConfigPacket.class,
                NpcConfigPacket::encode, NpcConfigPacket::decode, NpcConfigPacket::handle);
        CHANNEL.registerMessage(id++, OpenNpcConfigPacket.class,
                OpenNpcConfigPacket::encode, OpenNpcConfigPacket::decode, OpenNpcConfigPacket::handle);
    }
}
