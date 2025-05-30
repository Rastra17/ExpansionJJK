// src/main/java/com/example/infinitevoid/client/ClientDomainState.java
package com.example.infinitevoid.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ClientDomainState {
    private static boolean active = false;
    private static long castStart = 0;
    private static long activationStart = 0;
    private static final long CAST_TIME = 3_000;
    private static final long ACTIVATION_DISPLAY_TIME = 5_000; // Show big text for 5 seconds

    public static void onStartCast() {
        castStart = System.currentTimeMillis();
        active = false;
        activationStart = 0;
    }

    public static void onDomainActivated() {
        activationStart = System.currentTimeMillis();
        active = true;
    }

    public static boolean isActive() { return active; }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        
        // Show casting message
        if (castStart > 0 && now < castStart + CAST_TIME) {
            mc.inGameHud.setOverlayMessage(
                Text.literal("§dCasting Domain Expansion..."), false
            );
        }
        // Show big activation message (using overlay instead of title for size control)
        else if (activationStart > 0 && now < activationStart + ACTIVATION_DISPLAY_TIME) {
            // Create colored overlay text (smaller than title)
            Text overlayText = Text.literal("")
                .append(Text.literal("Domain Expansion: ").styled(style -> style.withColor(0x87CEEB))) // Light blue
                .append(Text.literal("Unlimited Void").styled(style -> style.withColor(0x9370DB))); // Violet
            
            mc.inGameHud.setOverlayMessage(overlayText, false);
        }
        // Clear casting state after display time
        else if (castStart > 0 && activationStart > 0 && now >= activationStart + ACTIVATION_DISPLAY_TIME) {
            castStart = 0;
            // Don't clear activationStart or active - domain stays active until manually broken
        }
        // Clear just casting state
        else if (castStart > 0 && now >= castStart + CAST_TIME && activationStart == 0) {
            castStart = 0;
        }
    }

    public static void reset() {
        active = false;
        castStart = 0;
        activationStart = 0;
    }
}