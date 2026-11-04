package com.timerewind;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Клиент -> сервер: клавиша отмотки зажата (true) или отпущена (false). */
public record RewindPayload(boolean held) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(TimeRewindMod.MOD_ID, "rewind");
	public static final CustomPacketPayload.Type<RewindPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, RewindPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, RewindPayload::held, RewindPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
