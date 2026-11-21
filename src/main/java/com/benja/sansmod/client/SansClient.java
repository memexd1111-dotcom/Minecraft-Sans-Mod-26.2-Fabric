package com.benja.sansmod.client;

import com.benja.sansmod.CastPayload;
import com.benja.sansmod.SansMod;
import com.benja.sansmod.SyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;

public class SansClient implements ClientModInitializer {
    private static KeyMapping castKey;
    private static KeyMapping menuKey;
    private static KeyMapping nextKey;
    private static KeyMapping prevKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(SansMod.id("main"));

        castKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sansmod.cast", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sansmod.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));
        nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sansmod.next", InputConstants.Type.KEYSYM, InputConstants.KEY_X, category));
        prevKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sansmod.prev", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, category));

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE,
                (payload, context) -> ClientState.apply(payload));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.tick();
            if (client.player == null) return;

            while (castKey.consumeClick()) {
                ClientPlayNetworking.send(new CastPayload(ClientState.selected));
            }
            while (menuKey.consumeClick()) {
                client.setScreenAndShow(new AbilityScreen());
            }
            while (nextKey.consumeClick()) {
                ClientState.cycle(1);
            }
            while (prevKey.consumeClick()) {
                ClientState.cycle(-1);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SansMod.id("hud"), AbilityHud::extract);
    }
}
