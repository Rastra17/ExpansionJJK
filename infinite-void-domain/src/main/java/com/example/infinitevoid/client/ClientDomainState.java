// src/main/java/com/example/infinitevoid/client/ClientDomainState.java
package com.example.infinitevoid.client;

import com.example.infinitevoid.network.DomainPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ClientDomainState {
    private static boolean active = false;
    private static long castStart = 0;
    private static long activationStart = 0;
    private static final long CAST_TIME = 3_000;
    private static final long ACTIVATION_DISPLAY_TIME = 5_000;

    // Hold duration tracking
    private static long holdStartTime = 0;
    private static boolean isHolding = false;
    private static String currentLoadingText = "";
    private static boolean waitingForCooldownResponse = false;

    public static void onStartCast() {
        castStart = System.currentTimeMillis();
        active = false;
        activationStart = 0;
    }

    public static void onDomainActivated() {
        activationStart = System.currentTimeMillis();
        active = true;
        // Clear holding state when domain activates
        isHolding = false;
        holdStartTime = 0;
    }

    public static boolean isActive() {
        return active;
    }

    // Called when key is pressed down
    public static void startHolding() {
        if (!isHolding && !waitingForCooldownResponse) {
            // If domain is already active, skip cooldown check and start holding immediately
            if (active) {
                holdStartTime = System.currentTimeMillis();
                isHolding = true;
            } else {
                // Check cooldown for new domain cast
                waitingForCooldownResponse = true;
                ClientPlayNetworking.send(new DomainPayloads.CheckCooldownPayload());
            }
        }
    }

    // Called when cooldown check returns - start actual holding if not on cooldown
    public static void startActualHolding() {
        waitingForCooldownResponse = false;
        if (!isHolding) {
            holdStartTime = System.currentTimeMillis();
            isHolding = true;
        }
    }

    // Called when key is released
    public static void stopHolding() {
        isHolding = false;
        holdStartTime = 0;
        currentLoadingText = "";
        waitingForCooldownResponse = false;
    }

    // Get current hold duration in seconds
    public static int getHoldDuration() {
        if (!isHolding || holdStartTime == 0)
            return 0;
        return (int) ((System.currentTimeMillis() - holdStartTime) / 1000);
    }

    // Check if held for full 3 seconds
    public static boolean isFullyHeld() {
        return isHolding && (System.currentTimeMillis() - holdStartTime) >= 3000;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        // Handle holding progress display
        if (isHolding && holdStartTime > 0) {
            long holdTime = now - holdStartTime;

            if (holdTime < 1000) {
                currentLoadingText = "§e0§bDomain Expansion§e0";
            } else if (holdTime < 2000) {
                currentLoadingText = "§6O§e0§bDomain Expansion§e0§6O";
            } else if (holdTime < 3000) {
                currentLoadingText = "§co§6O§e0§bDomain Expansion§e0§6O§co";
            } else {
                currentLoadingText = "§a✓ Activating Domain ✓";
            }

            mc.inGameHud.setOverlayMessage(Text.literal(currentLoadingText), false);
        }

        // Show casting message
        if (castStart > 0 && now < castStart + CAST_TIME) {
            mc.inGameHud.setOverlayMessage(Text.literal("§dCasting Domain Expansion..."), false);
        }
        // Show big activation message
        else if (activationStart > 0 && now < activationStart + ACTIVATION_DISPLAY_TIME) {
            Text overlayText = Text.literal("")
                    .append(Text.literal("Domain Expansion: ")
                            .styled(style -> style.withColor(0x87CEEB)))
                    .append(Text.literal("Unlimited Void")
                            .styled(style -> style.withColor(0x9370DB)));

            mc.inGameHud.setOverlayMessage(overlayText, false);
        }
        // Clear casting state after display time
        else if (castStart > 0 && activationStart > 0
                && now >= activationStart + ACTIVATION_DISPLAY_TIME) {
            castStart = 0;
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
        isHolding = false;
        holdStartTime = 0;
        currentLoadingText = "";
        waitingForCooldownResponse = false;
    }

    public static void setInactive() {
        active = false;
    }

    // Called when on cooldown - just reset the waiting state
    public static void onCooldownDenied() {
        waitingForCooldownResponse = false;
        isHolding = false;
        holdStartTime = 0;
        currentLoadingText = "";
    }
}
