package com.timerewind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class TimeRewindMod implements ModInitializer {
	public static final String MOD_ID = "timerewind";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(RewindPayload.TYPE, RewindPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RewindPayload.TYPE,
				(payload, context) -> RewindManager.setHolding(context.player().getUUID(), payload.held()));

		ServerTickEvents.END_SERVER_TICK.register(RewindManager::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register(RewindManager::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RewindManager::allowDamage);
	}
}
