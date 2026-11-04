package com.timerewind.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timerewind.CloseScreenPayload;
import com.timerewind.RewindPayload;
import com.timerewind.TimeRewindMod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.DeathScreen;
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

	/** Что мы последним отправили серверу. */
	private boolean sentDown = false;
	/** Клавиша зажата, пока открыт экран смерти (там обычные клавиши не работают). */
	private boolean screenHeld = false;

	@Override
	public void onInitializeClient() {
		// Экран смерти: ловим зажатие/отпускание Z вручную.
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof DeathScreen)) return;

			ScreenEvents.remove(screen).register(s -> screenHeld = false);
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
				if (REWIND_KEY.matches(keyEvent)) screenHeld = true;
			});
			ScreenKeyboardEvents.afterKeyRelease(screen).register((s, keyEvent) -> {
				if (REWIND_KEY.matches(keyEvent)) screenHeld = false;
			});
		});

		// Сервер просит закрыть экран смерти (время отмотано).
		ClientPlayNetworking.registerGlobalReceiver(CloseScreenPayload.TYPE, (payload, context) -> {
			screenHeld = false;
			context.client().gui.setScreen(null);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean down = client.player != null && (REWIND_KEY.isDown() || screenHeld);
			if (client.player == null) screenHeld = false;

			if (down != sentDown) {
				sentDown = down;
				// canSend = false, если на сервере нет мода.
				if (client.player != null && ClientPlayNetworking.canSend(RewindPayload.TYPE)) {
					ClientPlayNetworking.send(new RewindPayload(down));
				}
			}
		});
	}
}
