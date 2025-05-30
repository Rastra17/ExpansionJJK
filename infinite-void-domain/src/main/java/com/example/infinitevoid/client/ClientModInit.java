// src/main/java/com/example/infinitevoid/client/ClientModInit.java
package com.example.infinitevoid.client;

import com.example.infinitevoid.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ClientModInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientKeybinds.register();
        
        // Only register S2C packets on the client
        NetworkHandler.registerS2CPackets();
        
        ClientTickEvents.END_CLIENT_TICK.register(t -> {
            ClientKeybinds.tick();
            ClientDomainState.tick();
        });
    }
}