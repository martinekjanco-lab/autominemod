package com.automine;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutomineClient implements ClientModInitializer {
	private static KeyBinding toggleKey;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.automine.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				KeyBinding.Category.create(Identifier.of("automine", "main"))
		));

		// /mine <block>   e.g.  /mine red_sand   or   /mine red sand
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommandManager.literal("mine")
				.then(ClientCommandManager.argument("block", StringArgumentType.greedyString())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(
							Registries.BLOCK.getIds().stream()
									.filter(id -> id.getNamespace().equals("minecraft"))
									.map(Identifier::getPath)
									.toList(),
							builder))
					.executes(ctx -> {
						String raw = StringArgumentType.getString(ctx, "block")
								.trim().toLowerCase().replace(' ', '_');
						Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
						if (id == null || !Registries.BLOCK.containsId(id) || Registries.BLOCK.get(id) == Blocks.AIR) {
							ctx.getSource().sendFeedback(Text.literal("§cUnknown block: " + raw));
							return 0;
						}
						Block block = Registries.BLOCK.get(id);
						AutoMiner.target = block;
						ctx.getSource().sendFeedback(Text.literal("§7Automine target set to §f" + block.getName().getString()
								+ "§7. Press X to start/stop."));
						return 1;
					}))));

		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			while (toggleKey.wasPressed()) {
				if (mc.player == null || mc.currentScreen != null) continue;
				if (AutoMiner.enabled) {
					AutoMiner.stop(mc, "§cAutomine bot stopped");
				} else {
					AutoMiner.start(mc);
				}
			}
			AutoMiner.tick(mc);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			AutoMiner.enabled = false;
		});
	}
}
