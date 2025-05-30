// src/main/java/com/example/infinitevoid/client/ClientKeybinds.java
package com.example.infinitevoid.client;

import com.example.infinitevoid.network.DomainPayloads;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ClientKeybinds {
    public static KeyBinding CAST;

    public static void register() {
        CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Domain Expansion",  // Display name
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "Jujutsu Kaisen"     // Category name
        ));
    }

    public static void tick() {
        if (CAST.wasPressed()) {
            if (ClientDomainState.isActive()) {
                ClientPlayNetworking.send(new DomainPayloads.BreakDomainPayload());
            } else {
                ClientPlayNetworking.send(new DomainPayloads.TryCastPayload());
            }
        }
    }
}