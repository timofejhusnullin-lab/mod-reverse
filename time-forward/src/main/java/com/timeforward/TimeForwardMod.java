package com.timeforward;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class TimeForwardMod implements ModInitializer {
	public static final String MOD_ID = "timeforward";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ForwardPayload.TYPE, ForwardPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ForwardPayload.TYPE,
				(payload, context) -> ForwardManager.set(context.player().getUUID(), payload.held(), payload.multiplier()));

		ServerTickEvents.END_SERVER_TICK.register(ForwardManager::tick);
	}
}
