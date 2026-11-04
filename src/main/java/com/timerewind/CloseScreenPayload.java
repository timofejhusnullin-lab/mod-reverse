package com.timerewind;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Сервер -> клиент: закрыть экран смерти (время отмотано, игрок снова в игре). */
public record CloseScreenPayload() implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(TimeRewindMod.MOD_ID, "close_screen");
	public static final CustomPacketPayload.Type<CloseScreenPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, CloseScreenPayload> CODEC =
			StreamCodec.unit(new CloseScreenPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
