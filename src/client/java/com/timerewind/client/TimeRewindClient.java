package com.timerewind.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timerewind.RewindPayload;
import com.timerewind.TimeRewindMod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

public class TimeRewindClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TimeRewindMod.MOD_ID, "main"));

	/** Клавиша по умолчанию — Z. Меняется в настройках управления. */
	private static final KeyMapping REWIND_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.timerewind.rewind",
			InputConstants.Type.KEYBOARD,
			SDLScancode.SDL_SCANCODE_Z,
			CATEGORY
	));

	private boolean wasDown = false;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Если игрока нет (главное меню) — считаем клавишу отпущенной.
			boolean down = client.player != null && REWIND_KEY.isDown();

			if (down != wasDown) {
				wasDown = down;
				// canSend = false, если на сервере нет мода.
				if (client.player != null && ClientPlayNetworking.canSend(RewindPayload.TYPE)) {
					ClientPlayNetworking.send(new RewindPayload(down));
				}
			}
		});
	}
}
