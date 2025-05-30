// src/main/java/com/example/infinitevoid/DomainExpansionMod.java
package com.example.infinitevoid;

import com.example.infinitevoid.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class DomainExpansionMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Only register C2S packets on the server
        NetworkHandler.registerC2SPackets();

        // register server tick for domain manager
        ServerTickEvents.END_SERVER_TICK.register(world -> {
            DomainManager.get().tick();
        });
    }
}