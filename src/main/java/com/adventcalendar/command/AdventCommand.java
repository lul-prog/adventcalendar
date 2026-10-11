package com.adventcalendar.command;

import com.adventcalendar.data.AdventRewardsData;
import com.adventcalendar.entity.AdventNpcEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Comandos del mod (requieren permiso 2):
 * - /advent skin <url|usuario> ......... skin del NPC más cercano (10 bloques)
 * - /advent setrecompensa <dia 1-31> ... guarda el ítem en mano como premio del día
 */
public class AdventCommand {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("advent")
                .then(Commands.literal("skin")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("valor", StringArgumentType.string())
                                .executes(c -> setSkin(c, StringArgumentType.getString(c, "valor")))))
                .then(Commands.literal("setrecompensa")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("dia", IntegerArgumentType.integer(1, 31))
                                .executes(c -> setReward(c, IntegerArgumentType.getInteger(c, "dia"))))));
    }

    /** Aplica la skin (URL o nombre de usuario) al NPC de adviento más cercano en 10 bloques. */
    private static int setSkin(CommandContext<CommandSourceStack> ctx, String valor) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdventNpcEntity npc = null;
        double best = 10.0;
        for (AdventNpcEntity e : player.serverLevel().getEntitiesOfClass(
                AdventNpcEntity.class, player.getBoundingBox().inflate(10), e -> true)) {
            double dist = e.distanceTo(player);
            if (dist < best) {
                best = dist;
                npc = e;
            }
        }
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No hay ningún NPC de adviento en 10 bloques."));
            return 0;
        }
        npc.setSkin(valor);
        ctx.getSource().sendSuccess(() -> Component.literal("Skin del NPC actualizada."), true);
        return 1;
    }

    /** Guarda una copia del ítem en la mano como recompensa del día indicado. */
    private static int setReward(CommandContext<CommandSourceStack> ctx, int dia) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Toma un ítem en la mano primero."));
            return 0;
        }
        AdventRewardsData.get(player.getServer().overworld()).setReward(dia, held.copy());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Recompensa del día " + dia + " guardada: " + held.getHoverName().getString()), true);
        return 1;
    }
}
