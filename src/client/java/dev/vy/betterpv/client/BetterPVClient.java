package dev.vy.betterpv.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.api.BetterPVConfig;
import dev.vy.betterpv.client.api.BetterPvSessionAuth;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.gui.LoadingEggFinale;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.nav.PvTab;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.neu.SkyBlockPackCache;
import dev.vy.betterpv.client.price.ItemPricer;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.User;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class BetterPVClient implements ClientModInitializer {
	/** Runs after {@link Event#DEFAULT_PHASE} so we replace Skyblocker's {@code /pv}. */
	private static final Identifier PV_COMMAND_PHASE = Identifier.fromNamespaceAndPath("betterpv", "override_pv");
	private static final AtomicBoolean SESSION_AUTH_PREFETCHED = new AtomicBoolean(false);

	@Override
	public void onInitializeClient() {
		BetterPVConfig.load();
		ItemPricer.start();
		NeuRepoCache.start();
		SkyBlockPackCache.start();
		BetterPvCosmetics.initialize();

		// /pv command registration disabled here to keep /pv and /bpv untouched.
		// Handled via /b pv in BomboaddonsClient.

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			new SimpleSynchronousResourceReloadListener() {
				@Override
				public Identifier getFabricId() {
					return Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, "icon_probe_cache");
				}

				@Override
				public void onResourceManagerReload(ResourceManager resourceManager) {
					SkyBlockIconRenderer.invalidateProbeCache();
				}
			}
		);

		PlayerInteractPvOpener.register();
		HypixelProfileSpyButton.register();
		PartyJoinPvNotifier.register();
		ClientTickEvents.END_CLIENT_TICK.register(ProfileViewerOpener::tick);
		ClientTickEvents.END_CLIENT_TICK.register(LoadingEggFinale::tick);
		ClientTickEvents.END_CLIENT_TICK.register(BetterPVClient::prefetchSessionAuthOnce);
		BetterPV.LOGGER.info("BetterPV client ready - /pv");
	}

	/**
	 * After Minecraft exposes a real user session, warm the BetterPV JWT off-thread
	 * so cold {@code /pv} is less likely to pay joinServer + /hypixel/auth on the critical path.
	 */
	private static void prefetchSessionAuthOnce(net.minecraft.client.Minecraft client) {
		if (SESSION_AUTH_PREFETCHED.get() || client == null) {
			return;
		}
		User user = client.getUser();
		if (user == null) {
			return;
		}
		String accessToken = user.getAccessToken();
		if (accessToken == null || accessToken.isBlank() || user.getProfileId() == null) {
			return;
		}
		if (!SESSION_AUTH_PREFETCHED.compareAndSet(false, true)) {
			return;
		}
		BetterPvSessionAuth.prefetchAsync();
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> buildPvCommand(
		com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root
	) {
		return root
			.executes(ctx -> {
				ProfileViewerOpener.openSelfOr(null);
				return 1;
			})
			.then(
				ClientCommands.argument("player", StringArgumentType.word())
					.executes(ctx -> {
						ProfileViewerOpener.handleTypedArg(StringArgumentType.getString(ctx, "player"));
						return 1;
					})
					.then(
						ClientCommands.argument("page", StringArgumentType.word())
							.suggests((ctx, builder) -> suggestPages(builder))
							.executes(ctx -> {
								String player = StringArgumentType.getString(ctx, "player");
								String page = StringArgumentType.getString(ctx, "page");
								ProfileViewerOpener.handleTypedArg(player + " " + page);
								return 1;
							})
					)
			);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPages(
		com.mojang.brigadier.suggestion.SuggestionsBuilder builder
	) {
		String remaining = builder.getRemainingLowerCase();
		for (String alias : PvTab.commandAliases()) {
			if (remaining.isEmpty() || alias.startsWith(remaining)) {
				builder.suggest(alias);
			}
		}
		return builder.buildFuture();
	}

	@SuppressWarnings("unchecked")
	private static void removeLiteral(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		try {
			CommandNode<FabricClientCommandSource> root = dispatcher.getRoot();
			Field childrenField = CommandNode.class.getDeclaredField("children");
			childrenField.setAccessible(true);
			Map<String, CommandNode<FabricClientCommandSource>> children =
				(Map<String, CommandNode<FabricClientCommandSource>>) childrenField.get(root);
			children.remove(name);

			Field literalsField = CommandNode.class.getDeclaredField("literals");
			literalsField.setAccessible(true);
			Map<String, LiteralCommandNode<FabricClientCommandSource>> literals =
				(Map<String, LiteralCommandNode<FabricClientCommandSource>>) literalsField.get(root);
			literals.remove(name);
		} catch (ReflectiveOperationException exception) {
			BetterPV.LOGGER.warn("Could not remove existing /{} before override", name, exception);
		}
	}
}
