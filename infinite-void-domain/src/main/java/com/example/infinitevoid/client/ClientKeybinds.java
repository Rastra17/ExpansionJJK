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
    private static boolean wasPressed = false;
    private static boolean sentActivation = false;
    private static boolean awaitingCooldownCheck = false;

    public static void register() {
        CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Domain Expansion",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "Jujutsu Kaisen"
        ));
    }

    public static void tick() {
        boolean isCurrentlyPressed = CAST.isPressed();
        
        // Key just pressed down
        if (isCurrentlyPressed && !wasPressed) {
            // Check cooldown first before allowing hold
            ClientDomainState.startHolding();
            sentActivation = false;
            awaitingCooldownCheck = false;
        }
        
        // Key is being held
        if (isCurrentlyPressed && wasPressed) {
            // Check if held for 3 seconds and haven't sent activation yet
            if (ClientDomainState.isFullyHeld() && !sentActivation) {
                // Send appropriate packet based on current state
                if (ClientDomainState.isActive()) {
                    ClientPlayNetworking.send(new DomainPayloads.BreakDomainPayload());
                } else {
                    ClientPlayNetworking.send(new DomainPayloads.TryCastPayload());
                }
                sentActivation = true;
                ClientDomainState.stopHolding();
            }
        }
        
        // Key just released
        if (!isCurrentlyPressed && wasPressed) {
            // If released before 3 seconds, cancel
            if (!ClientDomainState.isFullyHeld()) {
                ClientDomainState.stopHolding();
            }
            awaitingCooldownCheck = false;
        }
        
        wasPressed = isCurrentlyPressed;
    }
}