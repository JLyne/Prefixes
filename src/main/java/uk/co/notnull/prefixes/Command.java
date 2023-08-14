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
			case "reload":
				Prefixes.getInstance().reload();
				Messages.sendComponent(invocation.source(), "reload-success");

				return;
			case "list":
				if (!(invocation.source() instanceof Player)) {
					Messages.sendComponent(invocation.source(), "errors.not-a-player");
					return;
				}

				if (args == 1) {
					Prefixes.getInstance().sendPrefixList((Player) invocation.source(), 1);
				} else {
					try {
						int page = Integer.parseInt(invocation.arguments()[1]);
						Prefixes.getInstance().sendPrefixList((Player) invocation.source(), page);

						if(page < 1) {
							Messages.sendComponent(invocation.source(), "errors.invalid-page");
						}
					} catch (NumberFormatException e) {
						Messages.sendComponent(invocation.source(), "errors.invalid-page");
					}
				}

				break;
			case "set":
				Player target = (Player) invocation.source();

				if(args == 1) {
					Messages.sendComponent(invocation.source(), "errors.no-prefix");
					return;
				}

				Prefix prefix = Prefixes.getInstance().getPrefix(invocation.arguments()[1]);

				if (args == 2 && !(invocation.source() instanceof Player)) {
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

				if (args == 2) {
					if (prefix.hasPermission() && !target.hasPermission(prefix.getPermission())) {
						Messages.sendComponent(invocation.source(), "errors.no-permission",
											   Collections.singletonMap("prefix", invocation.arguments()[1]),
											   Collections.emptyMap());
					} else {
						Prefixes.getInstance().applyPrefix(prefix, target).thenAccept(success -> {
							if (success) {
								Messages.sendComponent(invocation.source(), "set-success",
													   Collections.emptyMap(),
													   Collections.singletonMap("prefix",
																				Messages.miniMessage.deserialize(
																						prefix.getPrefix())));
							} else {
								Messages.sendComponent(invocation.source(), "set-failed");
							}
						});
					}
				} else {
					target = Prefixes.getInstance().getProxy().getPlayer(invocation.arguments()[2]).orElse(null);

					if (target == null) {
						Messages.sendComponent(invocation.source(), "errors.unknown-player");
					} else if (prefix.hasPermission() && !target.hasPermission(prefix.getPermission())) {
						Messages.sendComponent(invocation.source(), "errors.other-no-permission",
											   Map.of(
													   "player", target.getUsername(),
													   "prefix", invocation.arguments()[0]),
											   Collections.emptyMap());
					} else {
						Player finalTarget = target;

						Prefixes.getInstance().applyPrefix(prefix, target).thenAccept(success -> {
							if (success) {
								Messages.sendComponent(invocation.source(), "other-set-success",
													   Collections.singletonMap("player", finalTarget.getUsername()),
													   Collections.singletonMap("prefix",
																				Messages.miniMessage.deserialize(
																						prefix.getPrefix())));
							} else {
								Messages.sendComponent(invocation.source(), "other-set-failed",
													   Collections.singletonMap("player", finalTarget.getUsername()),
													   Collections.emptyMap());
							}
						});
					}
				}

				break;
			case "clear":
				target = (Player) invocation.source();

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
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
		if (invocation.arguments().length > 0 && invocation.arguments()[0].equals("reload")) {
			return invocation.source().hasPermission("prefixes.reload");
		} else if (invocation.arguments().length >= 3 && invocation.arguments()[0].equals("set")) {
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

		if (invocation.arguments().length <= 1) {
			List<String> options = new ArrayList<>();

			if (invocation.source().hasPermission("prefixes.reload")) {
				options.add("reload");
			}

			options.add("set");
			options.add("clear");
			options.add("list");

			return options.stream()
					.filter(o -> invocation.arguments().length == 0 || o.startsWith(invocation.arguments()[0]))
					.collect(Collectors.toList());
		}

		if (invocation.arguments()[0].equals("set")) {
			if (invocation.arguments().length == 2) {
				return Prefixes.getInstance()
						.getAllowedPrefixes((Player) invocation.source(), false)
						.stream().map(Prefix::getId)
						.filter(id -> invocation.arguments().length == 1 || id.startsWith(invocation.arguments()[1]))
						.collect(Collectors.toList());
			} else if (invocation.arguments().length == 3
					&& invocation.source().hasPermission("prefixes.change-others")) {
				return Prefixes.getInstance().getProxy().getAllPlayers()
						.stream().map(Player::getUsername).filter(name -> name.startsWith(invocation.arguments()[2]))
						.collect(Collectors.toList());
			}
		}

		if(invocation.arguments()[0].equals("clear") && invocation.arguments().length == 2
			&& invocation.source().hasPermission("prefixes.change-others")) {
			return Prefixes.getInstance().getProxy().getAllPlayers()
						.stream().map(Player::getUsername).filter(name -> name.startsWith(invocation.arguments()[1]))
						.collect(Collectors.toList());
		}

		return Collections.emptyList();
	}
}
