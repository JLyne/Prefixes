/*
 * Prefixes, a Velocity prefix plugin
 *
 * Copyright (c) 2022 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.co.notnull.prefixes;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import uk.co.notnull.platformdetection.PlatformDetectionVelocity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Prefixes {
	private static Prefixes instance;

	private Map<String, Prefix> prefixes = new HashMap<>();
	private Map<String, PrefixColour> colours = new HashMap<>();

	private final Map<UUID, Prefix> currentPrefixes = new ConcurrentHashMap<>();
	private final Map<UUID, PrefixColour> currentColours = new ConcurrentHashMap<>();

	private final PrefixColour fallbackColour = new PrefixColour("fallback", "<white>");

	@Inject
	private Logger logger;

	@Inject
	private ProxyServer proxy;

	@DataDirectory
	@Inject
	private Path dataDirectory;

	private LuckPerms luckperms;
	private UserManager userManager;
	private final static int ITEMS_PER_PAGE = 5;
	private boolean platformDetectionEnabled;
	private PlatformDetectionVelocity platformDetection;

	public Prefixes() {
		instance = this;
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		loadConfig();
		luckperms = LuckPermsProvider.get();
		userManager = luckperms.getUserManager();
		proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("prefix").build(), new Command());

		Optional<PluginContainer> platformDetection = proxy.getPluginManager().getPlugin("platform-detection");
        platformDetectionEnabled = platformDetection.isPresent();

        if(platformDetectionEnabled) {
            this.platformDetection = (PlatformDetectionVelocity) platformDetection.get().getInstance().get();
        }
	}

	@Subscribe
	public void onProxyReload(ProxyReloadEvent event) {
		loadConfig();
	}

	@Subscribe
	public void onPlayerConnect(PlayerChooseInitialServerEvent event) {
		checkPrefix(event.getPlayer());
	}

	@Subscribe
	public void onPlayerDisconnect(DisconnectEvent event) {
		currentPrefixes.remove(event.getPlayer().getUniqueId());
	}

	private boolean loadConfig() {
		// Setup config
		loadResource("config.yml");
		loadResource("messages.yml");

		LinkedHashMap<String, Prefix> prefixes = new LinkedHashMap<>();
		LinkedHashMap<String, PrefixColour> colours = new LinkedHashMap<>();

		try {
			ConfigurationNode configuration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "config.yml")).build().load();

			Map<Object, ? extends ConfigurationNode> prefixConfig = configuration.getNode("prefixes").getChildrenMap();
			Map<Object, ? extends ConfigurationNode> colourConfig = configuration.getNode("colours").getChildrenMap();

			if (!colourConfig.isEmpty()) {
				colourConfig.forEach((Object id, ConfigurationNode child) -> {
					String colourStart = child.getNode("start").getString();
					String colourEnd = child.getNode("end").getString("");
					String permission = child.getNode("permission").getString();
					String description = child.getNode("description").getString();
					boolean unlockable = child.getNode("unlockable").getBoolean(false);
					boolean retired = child.getNode("retired").getBoolean(false);

					if (colourStart == null) {
						logger.warn("Ignoring colour " + id + " as it has no defined start");
						return;
					}

					colours.put(id.toString(), new PrefixColour(
							id.toString(), colourStart, colourEnd, permission, description, unlockable, retired));
				});
			}

			if (!prefixConfig.isEmpty()) {
				prefixConfig.forEach((Object id, ConfigurationNode child) -> {
					String prefix = child.getNode("prefix").getString();
					String permission = child.getNode("permission").getString();
					String description = child.getNode("description").getString();
					String defaultColour = child.getNode("default-colour").getString();
					boolean unlockable = child.getNode("unlockable").getBoolean(false);
					boolean retired = child.getNode("retired").getBoolean(false);

					if (prefix == null) {
						logger.warn("Ignoring prefix " + id + " as it has no defined prefix");
						return;
					}

					if(defaultColour == null) {
						logger.warn("Prefix " + id + " has no default colour");
					} else if (!colours.containsKey(defaultColour)) {
						logger.warn("Default colour " + defaultColour + " for prefix " + id + " does not exist");
					}

					PrefixColour colour = colours.getOrDefault(defaultColour, fallbackColour);

					prefixes.put(id.toString(),
								 new Prefix(id.toString(), prefix, permission, description, colour, unlockable, retired));
				});
			}

			this.prefixes = prefixes;
			this.colours = colours;
		} catch (IOException e) {
			logger.error("Error loading config.yml");
			e.printStackTrace();
			return false;
		}

		//Message config
		ConfigurationNode messagesConfiguration;

		try {
			messagesConfiguration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "messages.yml")).build().load();
			Messages.set(messagesConfiguration);
		} catch (IOException e) {
			logger.error("Error loading messages.yml");
		}

		return true;
	}

	private void loadResource(String resource) {
		File folder = dataDirectory.toFile();

		if (!folder.exists()) {
			folder.mkdir();
		}

		File resourceFile = new File(dataDirectory.toFile(), resource);

		try {
			if (!resourceFile.exists()) {
				resourceFile.createNewFile();

				try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
					 OutputStream out = new FileOutputStream(resourceFile)) {
					ByteStreams.copy(in, out);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Prefixes getInstance() {
		return instance;
	}

	/**
	 * Applies the given prefix to the given player, updating their luckperms meta and prefix as necessary
	 *
	 * @param prefix - The prefix to apply
	 * @param colour - The prefix colour to apply
	 * @param player - The player to apply the prefix to
	 * @return - Completable future indicating whether applying was successful
	 */
	public CompletableFuture<Boolean> applyPrefix(Player player, Prefix prefix, PrefixColour colour) {
		if (prefix.hasPermission() && !player.hasPermission(prefix.getPermission())) {
			return CompletableFuture.completedFuture(false);
		}

		User user = userManager.getUser(player.getUniqueId());

		if (user == null) {
			return CompletableFuture.completedFuture(false);
		}

		return applyPrefix(user, prefix, colour);
	}

	/**
	 * Applies the given prefix to the given luckperms user, updating their meta and prefix
	 *
	 * @param prefix - The prefix to apply
	 * @param colour - The prefix colour to apply
	 * @param user - The user to apply the prefix to
	 * @return - Completable future indicating whether applying was successful
	 */
	public CompletableFuture<Boolean> applyPrefix(User user, Prefix prefix, PrefixColour colour) {
		return clearPrefix(user, false).thenCompose(cleared -> {
			user.data().add(MetaNode.builder("prefix", prefix.getId()).build());
			user.data().add(MetaNode.builder("prefix-colour", colour.getId()).build());
			user.data().add(PrefixNode.builder(prefix.getPrefix(colour), 1001).build());

			return saveUser(user);
		}).thenApply((result) -> {
			if(result) {
				currentPrefixes.put(user.getUniqueId(), prefix);
				currentColours.put(user.getUniqueId(), colour);
			}
			return true;
		});
	}

	/**
	 * Removes any prefixes from the given player
	 *
	 * @param player - The player to clear
	 * @return - Completable future indicating whether clearing was successful
	 */
	public CompletableFuture<Boolean> clearPrefix(Player player) {
		User user = userManager.getUser(player.getUniqueId());

		if (user == null) {
			return CompletableFuture.completedFuture(false);
		}

		return clearPrefix(user, true);
	}

	/**
	 * Removes any prefixes from the given luckperms user, optionally saving any changes
	 *
	 * @param user - The user to clear
	 * @param save - Whether to save any changes
	 * @return - Completable future indicating whether clearing was successful
	 */
	public CompletableFuture<Boolean> clearPrefix(User user, boolean save) {
		Collection<Node> nodes = user.getNodes(NodeType.META_OR_CHAT_META);
		NodeMap data = user.data();

		for (Node node : nodes) {
			if (node instanceof MetaNode metaNode) {
				if(metaNode.getMetaKey().equals("prefix") || metaNode.getMetaKey().equals("prefix-colour")) {
					data.remove(node);
				}
			}

			if(node instanceof PrefixNode) {
				data.remove(node);
			}
		}

		if(save) {
			return saveUser(user).thenApply((result) -> {
				if(result) {
					currentPrefixes.remove(user.getUniqueId());
				}

				return result;
			});
		} else {
			return CompletableFuture.completedFuture(true);
		}
	}

	private CompletableFuture<Boolean> saveUser(User user) {
		return userManager.saveUser(user)
					.thenApply((ignored) -> {
						luckperms.getMessagingService()
								.ifPresent((service) -> service.pushUserUpdate(user));
						return true;
					}).exceptionally(e -> {
						logger.warn("Failed to save and propagate prefix for " + user.getUsername(), e);
						return false;
					});
	}

	/**
	 * Checks the luckperms meta and prefixes of the given player and removes/applies prefixes as necessary
	 * Prefixes that don't match the user's set prefix are removed and the correct prefix is added if missing
	 *
	 * @param player - The player to check
	 */
	private void checkPrefix(Player player) {
		User user = userManager.getUser(player.getUniqueId());

		if (user == null) {
			logger.warn("Failed to update prefix for " + player.getUsername());
			return;
		}

		checkPrefix(user).thenAccept(result -> {
			if (result == PrefixCheckResult.NO_CHANGE) {
				return;
			}

			Prefix prefix = currentPrefixes.compute(player.getUniqueId(), (key, value) -> value);
			PrefixColour colour = currentColours.compute(player.getUniqueId(), (key, value) -> value);

			switch (result) {
				case PREFIX_REMOVED -> Messages.sendComponent(player, "notifications.prefix-removed");
				case COLOUR_REMOVED -> Messages.sendComponent(
						player, "notifications.colour-removed",
						Collections.emptyMap(),
						Collections.singletonMap("preview",
												 Messages.miniMessage.deserialize(prefix.getPrefix(colour))));
				case PREFIX_UPDATED -> Messages.sendComponent(
						player, "notifications.prefix-updated",
						Collections.emptyMap(),
						Collections.singletonMap("preview",
												 Messages.miniMessage.deserialize(prefix.getPrefix(colour))));
			}
		});
	}

	/**
	 * Checks the meta and prefixes of the given luckperms user and removes/applies prefixes as necessary
	 * Prefixes that don't match the user's set prefix are removed and the correct prefix is added if missing
	 *
	 * @param user - The user to check
	 * @fixme - Remove prefix when user loses permission
	 */
	private CompletableFuture<PrefixCheckResult> checkPrefix(User user) {
		Collection<Node> nodes = user.getNodes(NodeType.META_OR_CHAT_META);
		Prefix prefix = null;
		PrefixColour colour = null;

		var ref = new Object() {
			PrefixCheckResult result = PrefixCheckResult.NO_CHANGE;
		};

		boolean prefixFound = false;
		boolean colourFound = false;

		// Get selected prefix/colour
		for (Node node : nodes) {
			if (node instanceof MetaNode metaNode) {
				if(metaNode.getMetaKey().equals("prefix")) {
					prefix = prefixes.get(metaNode.getMetaValue());
					prefixFound = true;
				}

				if(metaNode.getMetaKey().equals("prefix-colour")) {
					colour = colours.get(metaNode.getMetaValue());
					colourFound = true;
				}
			}
		}

		currentPrefixes.remove(user.getUniqueId());
		currentColours.remove(user.getUniqueId());

		if (colour == null && prefix != null) { // User has invalid colour, or has never selected a colour
			ref.result = colourFound ? PrefixCheckResult.COLOUR_REMOVED : PrefixCheckResult.PREFIX_UPDATED;
			colour = prefix.getDefaultColour();
		}

		if (prefix == null && prefixFound) { // User has invalid prefix
			ref.result = PrefixCheckResult.PREFIX_REMOVED;
		} else if(ref.result != PrefixCheckResult.COLOUR_REMOVED) { // Check if any existing prefix needs updating
			for (Node node : nodes) {
				if (node instanceof PrefixNode) {
					// Existing prefix that needs to be updated
					if(prefix == null) {
						ref.result = PrefixCheckResult.PREFIX_REMOVED;
					} else if (!prefix.getPrefix(colour).equals(((PrefixNode) node).getMetaValue())) {
						ref.result = PrefixCheckResult.PREFIX_UPDATED;
					}
				}
			}
		}

		logger.info("Prefix check result for " + user.getUsername() + ": " + ref.result);

		if(ref.result == PrefixCheckResult.NO_CHANGE) {
			if(prefix != null) {
				currentPrefixes.put(user.getUniqueId(), prefix);
			}

			if(colour != null) {
				currentColours.put(user.getUniqueId(), colour);
			}

			return CompletableFuture.completedFuture(ref.result);
		}

		// Update prefix if required
		if (ref.result == PrefixCheckResult.PREFIX_REMOVED) {
			return clearPrefix(user, true).thenApply(result -> ref.result);
		} else {
			return applyPrefix(user, prefix, colour).thenApply((success) -> ref.result);
		}
	}

	/**
	 * Sends the book-based prefix list to the given player if possible
	 *
	 * @param player - The player to send the list to
	 */
	void sendPrefixList(Player player, int page) {
		Prefix currentPrefix = currentPrefixes.compute(player.getUniqueId(), (key, value) -> value);
		PrefixColour currentColour = currentColours.compute(player.getUniqueId(), (key, value) -> value);

		List<Prefix> prefixes = getAllowedPrefixes(player, true).stream()
				.filter(c -> !c.equals(currentPrefix))
				.collect(Collectors.toList());

		//Add player's currently selected prefix to top of list
		if(currentPrefix != null) {
			prefixes.add(0, currentPrefix);
		}

		boolean bedrock = platformDetectionEnabled && platformDetection.getPlatform(player).isBedrock();
		int start = (page - 1) * ITEMS_PER_PAGE;
		int index = 0;
		int pages = (int) Math.ceil((float) prefixes.size() / ITEMS_PER_PAGE);

		if (page > pages) {
			if (page == 1) {
				Messages.sendComponent(player, "errors.no-prefixes");
			} else {
				Messages.sendComponent(player, "errors.no-page",
									   Collections.singletonMap("page", String.valueOf(page)),
									   Collections.emptyMap());
			}

			return;
		}

		Component list = Messages.getComponent("prefix-list.header", Map.of(
						"page", String.valueOf(page),
						"pages", String.valueOf(pages)
				), Collections.emptyMap())
				.append(Component.newline());
		Component pagination = Component.empty();

		if (page > 1 && !bedrock) {
			pagination = pagination.append(
					Messages.getComponent("prefix-list.prev",
										  Collections.singletonMap("page", String.valueOf(page - 1)),
										  Collections.emptyMap()));
		}

		if (pages > page) {
			pagination = pagination.append(Component.space())
					.append(Messages.getComponent(bedrock ? "prefix-list-bedrock.next" : "prefix-list.next",
												  Collections.singletonMap("page", String.valueOf(page + 1)),
												  Collections.emptyMap()));
		}

		for (Prefix prefix : prefixes) {
			if (index < start) {
				index++;
				continue;
			}

			if (index >= start + ITEMS_PER_PAGE) {
				break;
			}

			if (prefix.hasPermission() && !player.hasPermission(prefix.getPermission())) {
				list = list.append(prefix.getLockedListItem(player.getUsername(), bedrock)).append(Component.newline());
			} else if (prefix.equals(currentPrefix)) {
				PrefixColour colour = currentColour != null ? currentColour : prefix.getDefaultColour();
				list = list.append(prefix.getSelectedListItem(player.getUsername(), colour, bedrock))
						.append(Component.newline());
			} else {
				list = list.append(prefix.getListItem(player.getUsername(), bedrock)).append(Component.newline());
			}

			index++;
		}

		player.sendMessage(list.append(pagination));
	}

	/**
	 * Sends the book-based colour list to the given player if possible
	 *
	 * @param player - The player to send the list to
	 * @param prefix - The prefix to use in colour previews
	 * @param page - The page of the list to send
	 */
	void sendColourList(Player player, @NotNull Prefix prefix, int page) {
		PrefixColour currentColour = currentColours.compute(player.getUniqueId(), (key, value) -> value);
		List<PrefixColour> colours = getAllowedColours(player, true).stream()
				.filter(c -> !c.equals(prefix.getDefaultColour()) && !c.equals(currentColour))
				.collect(Collectors.toList());

		//Add prefix's default colour to top of list
		if(!prefix.getDefaultColour().equals(fallbackColour)) {
			colours.add(0, prefix.getDefaultColour());
		}

		//Add player's currently selected colour to top of list
		if(currentColour != null && !currentColour.equals(prefix.getDefaultColour())) {
			colours.add(0, currentColour);
		}

		boolean bedrock = platformDetectionEnabled && platformDetection.getPlatform(player).isBedrock();
		int start = (page - 1) * ITEMS_PER_PAGE;
		int index = 0;
		int pages = (int) Math.ceil((float) colours.size() / ITEMS_PER_PAGE);

		if (page > pages) {
			if (page == 1) {
				Messages.sendComponent(player, "errors.no-colours");
			} else {
				Messages.sendComponent(player, "errors.no-page",
									   Collections.singletonMap("page", String.valueOf(page)),
									   Collections.emptyMap());
			}

			return;
		}

		Component list = Messages.getComponent("colour-list.header", Map.of(
						"page", String.valueOf(page),
						"pages", String.valueOf(pages)
				), Collections.emptyMap())
				.append(Component.newline());
		Component pagination = Component.empty();

		if (page > 1 && !bedrock) {
			pagination = pagination.append(
					Messages.getComponent("colour-list.prev",
										   Map.of(
														  "page", String.valueOf(page - 1),
														  "prefix", prefix.getId()),
										  Collections.emptyMap()));
		}

		if (pages > page) {
			pagination = pagination.append(Component.space())
					.append(Messages.getComponent(bedrock ? "colour-list-bedrock.next" : "colour-list.next",
												  Map.of(
														  "page", String.valueOf(page + 1),
														  "prefix", prefix.getId()),
												  Collections.emptyMap()));
		}

		for (PrefixColour colour : colours) {
			if (index < start) {
				index++;
				continue;
			}

			if (index >= start + ITEMS_PER_PAGE) {
				break;
			}

			if (colour.hasPermission() && !player.hasPermission(colour.getPermission())) {
				list = list.append(colour.getLockedListItem(prefix, bedrock)).append(Component.newline());
			} else if (colour.equals(currentColour)) {
				list = list.append(colour.getSelectedListItem(prefix, bedrock)).append(Component.newline());
			} else {
				list = list.append(colour.getListItem(prefix, bedrock)).append(Component.newline());
			}

			index++;
		}

		player.sendMessage(list.append(pagination));
	}

	/**
	 * Gets a prefix by its id
	 *
	 * @param id - The prefix id
	 * @return - The prefix, if one exists
	 */
	public Prefix getPrefix(String id) {
		return prefixes.get(id);
	}

	/**
	 * Gets a player's current prefix
	 *
	 * @param player - The player
	 * @return - The player's prefix, if one is set
	 */
	public Prefix getCurrentPrefix(Player player) {
		return currentPrefixes.compute(player.getUniqueId(), (key, value) -> value);
	}

	/**
	 * Gets a colour by its id
	 *
	 * @param id - The colour id
	 * @return - The colour, if one exists
	 */
	public PrefixColour getColour(String id) {
		return colours.get(id);
	}

	/**
	 * Returns a list of prefixs the given player is allowed to use, respecting prefix and player permissions
	 *
	 * @param player - The player
	 * @return - List of allowed prefixes
	 */
	public List<Prefix> getAllowedPrefixes(Player player, boolean includeLocked) {
		return prefixes.values().stream()
				.filter(p -> (!p.isRetired() || player.hasPermission("prefixes.use-retired"))
						&& (!p.hasPermission() || (includeLocked && p.isUnlockable()) || player.hasPermission(
						p.getPermission())))
				.collect(Collectors.toList());
	}

	/**
	 * Returns a list of colours the given player is allowed to use, respecting colour and player permissions
	 *
	 * @param player - The player
	 * @return - List of allowed colours
	 */
	public List<PrefixColour> getAllowedColours(Player player, boolean includeLocked) {
		return colours.values().stream()
				.filter(c -> (!c.isRetired() || player.hasPermission("prefixes.use-retired"))
						&& (!c.hasPermission() || (includeLocked && c.isUnlockable()) || player.hasPermission(
						c.getPermission())))
				.collect(Collectors.toList());
	}

	/**
	 * Reloads the configuration and reapplies prefixes to all online players
	 */
	public void reload() {
		loadConfig();

		for (Player player : proxy.getAllPlayers()) {
			checkPrefix(player);
		}
	}

	public ProxyServer getProxy() {
		return proxy;
	}
}
