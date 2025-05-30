// src/main/java/com/example/infinitevoid/DomainManager.java
package com.example.infinitevoid;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.entity.effect.StatusEffects;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DomainManager {
    private static final DomainManager INSTANCE = new DomainManager();
    public static DomainManager get() { return INSTANCE; }

    private final List<Domain> domains = new CopyOnWriteArrayList<>();

    // call when player requests cast
    public void requestCast(ServerPlayerEntity player) {
        // Debug: Check current status effects
        boolean hasMiningFatigue = player.hasStatusEffect(StatusEffects.MINING_FATIGUE);
        System.out.println("Player " + player.getName().getString() + " has Mining Fatigue: " + hasMiningFatigue);
        
        // Check if player can cast (not on cooldown)
        if (!Domain.canCast(player)) {
            player.sendMessage(Text.literal("§cYou are still exhausted from your last Domain Expansion!"));
            return;
        }
        
        // Check if player already has an active domain (including casting ones)
        boolean hasActiveDomain = domains.stream()
            .anyMatch(d -> (d.isActive() || !d.isFinished()) && d.getCaster() == player);
            
        if (hasActiveDomain) {
            player.sendMessage(Text.literal("§cYou already have an active Domain Expansion!"));
            return;
        }
        
        domains.add(new Domain(player));
        player.sendMessage(Text.literal("§dCasting Domain Expansion..."));
    }

    // call when player requests break
    public void requestBreak(ServerPlayerEntity player) {
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
            player.sendMessage(Text.literal("§cYou don't have an active Domain Expansion to break!"));
        }
    }

    // server tick
    public void tick() {
        // First tick all domains
        for (Domain d : domains) {
            d.tick();
        }
        
        // Then remove finished domains using removeIf (safe for CopyOnWriteArrayList)
        domains.removeIf(d -> {
            if (d.isFinished()) {
                // Debug message to confirm removal
                System.out.println("Removed finished domain for player: " + d.getCaster().getName().getString());
                return true;
            }
            return false;
        });
    }
    
    // Debug method to check domain count
    public int getActiveDomainCount() {
        return domains.size();
    }
}