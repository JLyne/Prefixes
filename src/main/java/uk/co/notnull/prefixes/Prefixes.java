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
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
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
import java.util.stream.Collectors;

public class Prefixes {
	private static Prefixes instance;

	private final Map<String, Prefix> prefixes = new HashMap<>();

	private final Map<UUID, Prefix> currentPrefixes = new HashMap<>();

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
		User user = userManager.getUser(event.getPlayer().getUniqueId());

		if (user == null) {
			logger.warn("Failed to update prefix for " + event.getPlayer().getUsername());
			return;
		}

		checkPrefix(user);
	}

	@Subscribe
	public void onPlayerDisconnect(DisconnectEvent event) {
		currentPrefixes.remove(event.getPlayer().getUniqueId());
	}

	private boolean loadConfig() {
		// Setup config
		loadResource("config.yml");
		loadResource("messages.yml");

		prefixes.clear();

		try {
			ConfigurationNode configuration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "config.yml")).build().load();

			Map<Object, ? extends ConfigurationNode> prefixConfig = configuration.getNode("prefixes").getChildrenMap();

			if (!prefixConfig.isEmpty()) {
				prefixConfig.forEach((Object id, ConfigurationNode child) -> {
					String prefix = child.getNode("prefix").getString();
					String permission = child.getNode("permission").getString();
					String description = child.getNode("description").getString();
					boolean unlockable = child.getNode("unlockable").getBoolean(false);
					boolean retired = child.getNode("retired").getBoolean(false);

					if (prefix == null) {
						logger.warn("Ignoring " + id + " as it has no defined prefix");
						return;
					}

					prefixes.put(id.toString(),
								 new Prefix(id.toString(), prefix, permission, description, unlockable, retired));
				});
			}
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
	 * @param player - The player to apply the prefix to
	 * @return - Completable future indicating wshether applying was successful
	 */
	public CompletableFuture<Boolean> applyPrefix(Prefix prefix, Player player) {
		if (prefix.hasPermission() && !player.hasPermission(prefix.getPermission())) {
			return CompletableFuture.completedFuture(false);
		}

		User user = userManager.getUser(player.getUniqueId());

		if (user == null) {
			return CompletableFuture.completedFuture(false);
		}

		return clearPrefix(user, false).thenCompose(cleared -> {
			user.data().add(MetaNode.builder("prefix", prefix.getId()).build());
			user.data().add(PrefixNode.builder(prefix.getPrefix(), 1001).build());

			return saveUser(user);
		}).thenApply((result) -> {
			if(result) {
				currentPrefixes.put(user.getUniqueId(), prefix);
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
			if (node instanceof MetaNode && ((MetaNode) node).getMetaKey().equals("prefix")) {
				data.remove(node);
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
			return;
		}

		checkPrefix(user);
	}

	/**
	 * Checks the meta and prefixes of the given luckperms user and removes/applies prefixes as necessary
	 * Prefixes that don't match the user's set prefix are removed and the correct prefix is added if missing
	 *
	 * @param user - The user to check
	 */
	private void checkPrefix(User user) {
		Collection<Node> nodes = user.getNodes(NodeType.META_OR_CHAT_META);
		Prefix prefix = null;
		boolean prefixFound = false;
		boolean prefixChanged = false;

		for (Node node : nodes) {
			if (node instanceof MetaNode && ((MetaNode) node).getMetaKey().equals("prefix")) {
				prefix = prefixes.get(((MetaNode) node).getMetaValue());
			}
		}

		currentPrefixes.remove(user.getUniqueId());

		if (prefix != null) {
			currentPrefixes.put(user.getUniqueId(), prefix);
		}

		for (Node node : nodes) {
			if (node instanceof PrefixNode) {
				if (prefix == null) {
					logger.info(
							"Removing prefix " + ((PrefixNode) node).getMetaValue() + " from " + user.getUsername() + " ");
					user.getData(DataType.NORMAL).remove(node);
					prefixChanged = true;
				} else if (!prefix.getPrefix().equals(((PrefixNode) node).getMetaValue())) {
					logger.info("Updating prefix " + prefix.getId() + " for " + user.getUsername() + " ");
					user.getData(DataType.NORMAL).remove(node);
					prefixChanged = true;
				} else {
					prefixFound = true;
				}
			}
		}

		if (!prefixFound && prefix != null) {
			prefixChanged = true;
			user.getData(DataType.NORMAL).add(PrefixNode.builder(prefix.getPrefix(), 1001).build());
		}

		if (prefixChanged) {
			saveUser(user);
		}
	}

	/**
	 * Sends the book-based prefix list to the given player if possible
	 *
	 * @param player - The player to send the list to
	 */
	void sendPrefixList(Player player, int page) {
		Prefix currentPrefix = currentPrefixes.get(player.getUniqueId());
		List<Prefix> prefixes = getAllowedPrefixes(player, true);

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

		Component list = Messages.getComponent("list.header", Map.of(
						"page", String.valueOf(page),
						"pages", String.valueOf(pages)
				), Collections.emptyMap())
				.append(Component.newline());
		Component pagination = Component.empty();

		if (page > 1 && !bedrock) {
			pagination = pagination.append(
					Messages.getComponent("list.prev",
										  Collections.singletonMap("page", String.valueOf(page - 1)),
										  Collections.emptyMap()));
		}

		if (pages > page) {
			pagination = pagination.append(Component.space())
					.append(Messages.getComponent(bedrock ? "list-bedrock.next" : "list.next",
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
				list = list.append(prefix.getLockedListItem(bedrock)).append(Component.newline());
			} else if (prefix.equals(currentPrefix)) {
				list = list.append(prefix.getSelectedListItem(bedrock)).append(Component.newline());
			} else {
				list = list.append(prefix.getListItem(bedrock)).append(Component.newline());
			}

			index++;
		}

		player.sendMessage(list.append(pagination), MessageType.SYSTEM);
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
