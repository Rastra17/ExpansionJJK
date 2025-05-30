// src/main/java/com/example/infinitevoid/network/NetworkHandler.java
package com.example.infinitevoid.network;

import com.example.infinitevoid.DomainManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class NetworkHandler {

    public static void registerC2SPackets() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(DomainPayloads.TryCastPayload.ID, DomainPayloads.TryCastPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DomainPayloads.BreakDomainPayload.ID, DomainPayloads.BreakDomainPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DomainPayloads.CheckCooldownPayload.ID, DomainPayloads.CheckCooldownPayload.CODEC);

        // Register handlers
        ServerPlayNetworking.registerGlobalReceiver(DomainPayloads.TryCastPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                DomainManager.get().requestCast(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DomainPayloads.BreakDomainPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                DomainManager.get().requestBreak(context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DomainPayloads.CheckCooldownPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                DomainManager.get().checkCooldown(context.player());
            });
        });
    }

    public static void registerS2CPackets() {
        // Register payload types
        PayloadTypeRegistry.playS2C().register(DomainPayloads.StartCastPayload.ID, DomainPayloads.StartCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DomainPayloads.DomainActivatedPayload.ID, DomainPayloads.DomainActivatedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DomainPayloads.DomainDeactivatedPayload.ID, DomainPayloads.DomainDeactivatedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DomainPayloads.CooldownOkPayload.ID, DomainPayloads.CooldownOkPayload.CODEC);

        // Register handlers
        ClientPlayNetworking.registerGlobalReceiver(DomainPayloads.StartCastPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.infinitevoid.client.ClientDomainState.onStartCast();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(DomainPayloads.DomainActivatedPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.infinitevoid.client.ClientDomainState.onDomainActivated();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(DomainPayloads.DomainDeactivatedPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.infinitevoid.client.ClientDomainState.setInactive();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(DomainPayloads.CooldownOkPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                com.example.infinitevoid.client.ClientDomainState.startActualHolding();
            });
        });
    }
}