// src/main/java/com/example/infinitevoid/network/DomainPayloads.java
package com.example.infinitevoid.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class DomainPayloads {
    
    public record TryCastPayload() implements CustomPayload {
        public static final CustomPayload.Id<TryCastPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "try_cast"));
        public static final PacketCodec<PacketByteBuf, TryCastPayload> CODEC = 
            PacketCodec.unit(new TryCastPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BreakDomainPayload() implements CustomPayload {
        public static final CustomPayload.Id<BreakDomainPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "break_domain"));
        public static final PacketCodec<PacketByteBuf, BreakDomainPayload> CODEC = 
            PacketCodec.unit(new BreakDomainPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record StartCastPayload() implements CustomPayload {
        public static final CustomPayload.Id<StartCastPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "start_cast"));
        public static final PacketCodec<PacketByteBuf, StartCastPayload> CODEC = 
            PacketCodec.unit(new StartCastPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DomainActivatedPayload() implements CustomPayload {
        public static final CustomPayload.Id<DomainActivatedPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "domain_activated"));
        public static final PacketCodec<PacketByteBuf, DomainActivatedPayload> CODEC = 
            PacketCodec.unit(new DomainActivatedPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DomainDeactivatedPayload() implements CustomPayload {
        public static final CustomPayload.Id<DomainDeactivatedPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "domain_deactivated"));
        public static final PacketCodec<PacketByteBuf, DomainDeactivatedPayload> CODEC = 
            PacketCodec.unit(new DomainDeactivatedPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record CheckCooldownPayload() implements CustomPayload {
        public static final CustomPayload.Id<CheckCooldownPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "check_cooldown"));
        public static final PacketCodec<PacketByteBuf, CheckCooldownPayload> CODEC = 
            PacketCodec.unit(new CheckCooldownPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record CooldownOkPayload() implements CustomPayload {
        public static final CustomPayload.Id<CooldownOkPayload> ID = 
            new CustomPayload.Id<>(Identifier.of("infinitevoid", "cooldown_ok"));
        public static final PacketCodec<PacketByteBuf, CooldownOkPayload> CODEC = 
            PacketCodec.unit(new CooldownOkPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}