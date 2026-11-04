package com.timeforward;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Клиент -> сервер: клавиша ускорения зажата/отпущена и выбранная скорость (во сколько раз). */
public record ForwardPayload(boolean held, float multiplier) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(TimeForwardMod.MOD_ID, "forward");
	public static final CustomPacketPayload.Type<ForwardPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ForwardPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ForwardPayload::held,
			ByteBufCodecs.FLOAT, ForwardPayload::multiplier,
			ForwardPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
