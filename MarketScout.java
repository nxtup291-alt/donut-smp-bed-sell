package com.marketscout;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MarketScout implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.INSTANCE = Config.load();
        Flips.load();
        Deals.load();
        PriceLog.load();
        Scout.load();
        Chat.compile();

        // Default key: Left Alt (change it in Options > Controls)
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.marketscout.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.marketscout"));

        Overlay.register();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) Chat.onMessage(net.minecraft.client.MinecraftClient.getInstance(), message.getString());
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new MarketScreen());
            }
            Scout.tick(mc);
        });
    }
}
