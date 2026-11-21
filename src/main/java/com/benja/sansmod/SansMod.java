package com.benja.sansmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class SansMod implements ModInitializer {
    public static final String MOD_ID = "sansmod";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** El amuleto cuenta si está en cualquier lugar del inventario (incluida la mano secundaria). */
    public static boolean hasCharm(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.SANS_CHARM)) return true;
        }
        return false;
    }

    @Override
    public void onInitialize() {
        SansConfig.load();
        ModItems.initialize();

        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CastPayload.TYPE, CastPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CastPayload.TYPE,
                (payload, context) -> AbilityLogic.cast(context.player(), payload.ability()));

        ServerTickEvents.END_SERVER_TICK.register(AbilityLogic::serverTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(AbilityLogic::allowDamage);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Scheduler.clear();
            PlayerState.clear();
        });
    }
}
