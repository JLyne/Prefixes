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

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Command implements SimpleCommand {

	@Override
	public void execute(final Invocation invocation) {
		int args = invocation.arguments().length;

		if (args == 0) {
			if (!(invocation.source() instanceof Player)) {
				Messages.sendComponent(invocation.source(), "errors.not-a-player");
				return;
			}

			Prefixes.getInstance().sendPrefixList((Player) invocation.source(), 1);
			return;
		}

		String arg1 = invocation.arguments()[0];

		switch (arg1) {
			case "reload" -> {
				Prefixes.getInstance().reload();
				Messages.sendComponent(invocation.source(), "reload-success");
			}
			case "list" -> handleListCommand(invocation);
			case "set" -> handleSetCommand(invocation);
			case "setfor" -> handleSetForCommand(invocation);
			case "clear" -> handleClearCommand(invocation);
			case "colours" -> handleColourCommand(invocation);
		}
	}

	private void handleListCommand(final Invocation invocation) {
		int args = invocation.arguments().length;

		if (!(invocation.source() instanceof Player)) {
			Messages.sendComponent(invocation.source(), "errors.not-a-player");
			return;
		}

		if (args == 1) {
			Prefixes.getInstance().sendPrefixList((Player) invocation.source(), 1);
		} else {
			try {
				int page = Integer.parseInt(invocation.arguments()[1]);

				if(page < 1) {
					Messages.sendComponent(invocation.source(), "errors.invalid-page");
				} else {
					Prefixes.getInstance().sendPrefixList((Player) invocation.source(), page);
				}
			} catch (NumberFormatException e) {
				Messages.sendComponent(invocation.source(), "errors.invalid-page");
			}
		}
	}

	private void handleSetCommand(final Invocation invocation) {
		int args = invocation.arguments().length;
		Player target = (Player) invocation.source();

		if(args == 1) {
			Messages.sendComponent(invocation.source(), "errors.no-prefix");
			return;
		}

		Prefix prefix = Prefixes.getInstance().getPrefix(invocation.arguments()[1]);

		if (!(invocation.source() instanceof Player)) {
			Messages.sendComponent(invocation.source(), "errors.not-a-player");
			return;
		}

		if (prefix == null) {
			Messages.sendComponent(invocation.source(), "errors.invalid-prefix",
								   Collections.singletonMap("prefix", invocation.arguments()[1]),
								   Collections.emptyMap());
			return;
		} else if (prefix.isRetired() && !target.hasPermission("prefixes.use-retired")) {
			Messages.sendComponent(invocation.source(), "errors.prefix-retired",
								   Collections.singletonMap("prefix", invocation.arguments()[1]),
								   Collections.emptyMap());
			return;
		}

		if (prefix.hasPermission() && !target.hasPermission(prefix.getPermission())) {
			Messages.sendComponent(invocation.source(), "errors.no-prefix-permission",
								   Collections.singletonMap("prefix", invocation.arguments()[1]),
								   Collections.emptyMap());
			return;
		}

		if(args == 2) {
			Prefixes.getInstance().sendColourList((Player) invocation.source(), prefix, 1);
		} else {
			String colourKey = invocation.arguments()[2];
			PrefixColour colour = prefix.getDefaultColour();

			if(!colourKey.equals("default")) {
				colour = Prefixes.getInstance().getColour(invocation.arguments()[2]);
			}

			if (colour == null) {
				Messages.sendComponent(invocation.source(), "errors.invalid-colour");
				return;
			} else if (prefix.isRetired() && !target.hasPermission("prefixes.use-retired")) {
				Messages.sendComponent(invocation.source(), "errors.colour-retired",
									   Collections.singletonMap("prefix", invocation.arguments()[1]),
									   Collections.emptyMap());
				return;
			}

			if (prefix.hasPermission() && !target.hasPermission(prefix.getPermission())) {
				Messages.sendComponent(invocation.source(), "errors.no-colour-permission");
			}

			Component preview = Messages.miniMessage.deserialize(prefix.getPrefix(colour));

			Prefixes.getInstance().applyPrefix(target, prefix, colour).thenAccept(success -> {
				if (success) {
					Messages.sendComponent(invocation.source(), "set-success",
										   Collections.emptyMap(),
										   Collections.singletonMap("preview", preview));
				} else {
					Messages.sendComponent(invocation.source(), "set-failed");
				}
			});
		}
	}

	private void handleSetForCommand(final Invocation invocation) {
		int args = invocation.arguments().length;

		if(args == 1) {
			Messages.sendComponent(invocation.source(), "errors.unknown-player");
		} else if (args == 2) {
			Messages.sendComponent(invocation.source(), "errors.no-prefix");
			return;
		} else if (args == 3) {
			Messages.sendComponent(invocation.source(), "errors.no-colour");
			return;
		}

		Player target = Prefixes.getInstance().getProxy().getPlayer(invocation.arguments()[1]).orElse(null);
		Prefix prefix = Prefixes.getInstance().getPrefix(invocation.arguments()[2]);

		if (prefix == null) {
			Messages.sendComponent(invocation.source(), "errors.invalid-prefix",
								   Collections.singletonMap("prefix", invocation.arguments()[2]),
								   Collections.emptyMap());
			return;
		}

		if (target == null) {
			Messages.sendComponent(invocation.source(), "errors.unknown-player");
		} else if (prefix.hasPermission() && !target.hasPermission(prefix.getPermission())) {
			Messages.sendComponent(invocation.source(), "errors.other-no-prefix-permission",
								   Map.of(
										   "player", target.getUsername(),
										   "prefix", invocation.arguments()[2]),
								   Collections.emptyMap());
		} else {
			String colourKey = invocation.arguments()[3];
			PrefixColour colour = prefix.getDefaultColour();

			if(!colourKey.equals("default")) {
				colour = Prefixes.getInstance().getColour(invocation.arguments()[3]);
			}

			if (colour == null) {
				Messages.sendComponent(invocation.source(), "errors.invalid-colour");
				return;
			} else if (colour.isRetired() && !target.hasPermission("prefixes.use-retired")) {
				Messages.sendComponent(invocation.source(), "errors.colour-retired",
									   Collections.singletonMap("prefix", invocation.arguments()[2]),
									   Collections.emptyMap());
				return;
			}

			if (colour.hasPermission() && !target.hasPermission(colour.getPermission())) {
				Messages.sendComponent(invocation.source(), "errors.no-colour-permission");
			}

			Component preview = Messages.miniMessage.deserialize(prefix.getPrefix(colour));

			Prefixes.getInstance().applyPrefix(target, prefix, colour).thenAccept(success -> {
				if (success) {
					Messages.sendComponent(invocation.source(), "other-set-success",
										   Collections.singletonMap("player", target.getUsername()),
										   Collections.singletonMap("preview", preview));
				} else {
					Messages.sendComponent(invocation.source(), "other-set-failed",
										   Collections.singletonMap("player", target.getUsername()),
										   Collections.emptyMap());
				}
			});
		}
	}

	private void handleClearCommand(final Invocation invocation) {
		int args = invocation.arguments().length;
		Player target = (Player) invocation.source();

		if (args == 1) {
			if(!(invocation.source() instanceof Player)) {
				Messages.sendComponent(invocation.source(), "errors.not-a-player");
				return;
			}

			Prefixes.getInstance().clearPrefix(target).thenAccept(success -> {
				if(success) {
					Messages.sendComponent(invocation.source(), "clear-success");
				} else {
					Messages.sendComponent(invocation.source(), "clear-failed");
				}
			});
		} else {
			target = Prefixes.getInstance().getProxy().getPlayer(invocation.arguments()[1]).orElse(null);

			if (target == null) {
				Messages.sendComponent(invocation.source(), "errors.unknown-player");
			} else {
				Player finalTarget1 = target;
				Prefixes.getInstance().clearPrefix(target).thenAccept(success -> {
					if (success) {
						Messages.sendComponent(invocation.source(), "other-clear-success",
											   Collections.singletonMap("player", finalTarget1.getUsername()),
											   Collections.emptyMap());
					} else {
						Messages.sendComponent(invocation.source(), "other-clear-failed",
											   Collections.singletonMap("player", finalTarget1.getUsername()),
											   Collections.emptyMap());
					}
				});
			}
		}
	}

	private void handleColourCommand(final Invocation invocation) {
		int args = invocation.arguments().length;

		if (!(invocation.source() instanceof Player player)) {
			Messages.sendComponent(invocation.source(), "errors.not-a-player");
			return;
		}

		// Default to player's current prefix
		Prefix prefix = Prefixes.getInstance().getCurrentPrefix(player);

		if(args > 1) { // Player specified prefix as argument
			prefix = Prefixes.getInstance().getPrefix(invocation.arguments()[1]);

			if (prefix == null) {
				Messages.sendComponent(invocation.source(), "errors.invalid-prefix",
									   Collections.singletonMap("prefix", invocation.arguments()[1]),
									   Collections.emptyMap());
				return;
			}

			if (prefix.hasPermission() && !player.hasPermission(prefix.getPermission())) {
				Messages.sendComponent(invocation.source(), "errors.no-prefix-permission",
									   Collections.singletonMap("prefix", prefix.getId()),
									   Collections.emptyMap());
				return;
			}
		} else if (prefix == null) { // Player hasn't set a prefix
			Messages.sendComponent(invocation.source(), "errors.no-prefix-set");
			return;
		}

		if (args < 3) {
			Prefixes.getInstance().sendColourList((Player) invocation.source(), prefix, 1);
		} else {
			try {
				int page = Integer.parseInt(invocation.arguments()[2]);

				if(page < 1) {
					Messages.sendComponent(invocation.source(), "errors.invalid-page");
				} else {
					Prefixes.getInstance().sendColourList((Player) invocation.source(), prefix, page);
				}
			} catch (NumberFormatException e) {
				Messages.sendComponent(invocation.source(), "errors.invalid-page");
			}
		}
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
		if (invocation.arguments().length > 0 && invocation.arguments()[0].equals("reload")) {
			return invocation.source().hasPermission("prefixes.reload");
		} else if (invocation.arguments().length > 0 && invocation.arguments()[0].equals("setfor")) {
			return invocation.source().hasPermission("prefixes.change-others");
		} else if (invocation.arguments().length >= 2 && invocation.arguments()[0].equals("clear")) {
			return invocation.source().hasPermission("prefixes.change-others");
		}

		return true;
	}

	@Override
	public List<String> suggest(final Invocation invocation) {
		if (!(invocation.source() instanceof Player)) {
			return invocation.arguments().length <= 1 ? Collections.singletonList("reload") : Collections.emptyList();
		}

		int args = invocation.arguments().length;

		if (args <= 1) {
			List<String> options = new ArrayList<>();

			if (invocation.source().hasPermission("prefixes.reload")) {
				options.add("reload");
			}

			if (invocation.source().hasPermission("prefixes.change-others")) {
				options.add("setfor");
			}

			options.add("set");
			options.add("clear");
			options.add("list");
			options.add("colours");

			return options.stream()
					.filter(o -> args == 0 || o.startsWith(invocation.arguments()[0].toLowerCase()))
					.collect(Collectors.toList());
		}

		if (invocation.arguments()[0].equals("set")) {
			if (args == 2) {
				return Prefixes.getInstance()
						.getAllowedPrefixes((Player) invocation.source(), false)
						.stream().map(Prefix::getId)
						.filter(id -> id.toLowerCase().startsWith(invocation.arguments()[1].toLowerCase()))
						.collect(Collectors.toList());
			} else if (args == 3) {
				return Prefixes.getInstance()
						.getAllowedColours((Player) invocation.source(), false)
						.stream().map(PrefixColour::getId)
						.filter(id -> id.toLowerCase().startsWith(invocation.arguments()[2].toLowerCase()))
						.collect(Collectors.toList());
			}
		}

		if (invocation.arguments()[0].equals("setfor") && invocation.source().hasPermission("prefixes.change-others")) {
			if(args == 2) {
				return Prefixes.getInstance().getProxy().getAllPlayers()
						.stream().map(Player::getUsername)
						.filter(name -> name.toLowerCase().startsWith(invocation.arguments()[1].toLowerCase()))
						.collect(Collectors.toList());
			} else if (args == 3) {
				return Prefixes.getInstance()
						.getAllowedPrefixes((Player) invocation.source(), false)
						.stream().map(Prefix::getId)
						.filter(id -> id.toLowerCase().startsWith(invocation.arguments()[2].toLowerCase()))
						.collect(Collectors.toList());
			} else if (args == 4) {
				return Prefixes.getInstance()
						.getAllowedColours((Player) invocation.source(), false)
						.stream().map(PrefixColour::getId)
						.filter(id -> id.toLowerCase().startsWith(invocation.arguments()[3].toLowerCase()))
						.collect(Collectors.toList());
			}
		}

		if(invocation.arguments()[0].equals("clear") && args == 2
			&& invocation.source().hasPermission("prefixes.change-others")) {
			return Prefixes.getInstance().getProxy().getAllPlayers()
					.stream().map(Player::getUsername)
					.filter(name -> name.toLowerCase().startsWith(invocation.arguments()[1].toLowerCase()))
					.collect(Collectors.toList());
		}

		if (invocation.arguments()[0].equals("colours")) {
			if (args == 2) {
				return Prefixes.getInstance()
						.getAllowedPrefixes((Player) invocation.source(), false)
						.stream().map(Prefix::getId)
						.filter(id -> id.toLowerCase().startsWith(invocation.arguments()[1].toLowerCase()))
						.collect(Collectors.toList());
			}
		}

		return Collections.emptyList();
	}
}
