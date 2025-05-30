// src/main/java/com/example/infinitevoid/DomainManager.java
package com.example.infinitevoid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import com.example.infinitevoid.network.DomainPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DomainManager {
    private static final DomainManager INSTANCE = new DomainManager();

    public static DomainManager get() {
        return INSTANCE;
    }

    private final List<Domain> domains = new CopyOnWriteArrayList<>();
    private final Map<UUID, Boolean> playerDomainStates = new HashMap<>();

    // Check cooldown immediately when key is pressed
    public void checkCooldown(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        boolean hasActiveDomain = playerDomainStates.getOrDefault(playerId, false);

        // If player has active domain, allow them to break it (no cooldown check
        // needed)
        if (hasActiveDomain) {
            ServerPlayNetworking.send(player, new DomainPayloads.CooldownOkPayload());
            return;
        }

        // Only check cooldown if trying to cast a new domain
        if (!Domain.canCast(player)) {
            player.sendMessage(
                    Text.literal("§cYou are still exhausted from your last Domain Expansion!"));
            return;
        }

        // If all checks pass, allow holding to proceed
        ServerPlayNetworking.send(player, new DomainPayloads.CooldownOkPayload());
    }

    public void requestCast(ServerPlayerEntity player) {
        // Check cooldown first and show message immediately
        if (!Domain.canCast(player)) {
            player.sendMessage(
                    Text.literal("§cYou are still exhausted from your last Domain Expansion!"));
            return;
        }

        UUID playerId = player.getUuid();
        boolean hasActiveDomain = playerDomainStates.getOrDefault(playerId, false);

        if (hasActiveDomain) {
            player.sendMessage(Text.literal("§cYou already have an active Domain Expansion!"));
            return;
        }

        domains.add(new Domain(player));
        playerDomainStates.put(playerId, true);
        player.sendMessage(Text.literal("§dCasting Domain Expansion..."));
    }

    public void requestBreak(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        boolean hasActiveDomain = playerDomainStates.getOrDefault(playerId, false);

        if (!hasActiveDomain) {
            player.sendMessage(
                    Text.literal("§cYou don't have an active Domain Expansion to break!"));
            return;
        }
        boolean foundDomain = false;
        for (Domain d : domains) {
            if (d.isActive() && d.getCaster() == player) {
                d.requestBreak();
                foundDomain = true;
                player.sendMessage(Text.literal("§6Breaking Domain Expansion..."));
                break;
            }
        }

        if (!foundDomain) {
            player.sendMessage(
                    Text.literal("§cYou don't have an active Domain Expansion to break!"));
        }
    }

    public void tick() {
        for (Domain d : domains) {
            d.tick();
        }

        domains.removeIf(d -> {
            if (d.isFinished()) {
                UUID casterId = d.getCaster().getUuid();
                playerDomainStates.put(casterId, false);
                System.out.println("Removed finished domain for player: "
                        + d.getCaster().getName().getString());
                return true;
            }
            return false;
        });
    }

    public int getActiveDomainCount() {
        return domains.size();
    }

    public boolean hasActiveDomain(ServerPlayerEntity player) {
        return playerDomainStates.getOrDefault(player.getUuid(), false);
    }
}
