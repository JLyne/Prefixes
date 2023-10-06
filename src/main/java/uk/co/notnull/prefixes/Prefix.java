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

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class Prefix {
	private final String id;
	private final String prefix;
	private final String permission;
	private final String description;
	private final @NotNull PrefixColour defaultColour;
	private final boolean unlockable;
	private final boolean retired;

	private Component listItem;
	private Component bedrockListItem;
	private Component lockedItem;
	private Component bedrockLockedItem;
	private Component selectedItem;
	private Component bedrockSelectedItem;

	public Prefix(
			String id, String prefix, String permission, String description, @NotNull PrefixColour defaultColour, boolean unlockable, boolean retired) {
		this.id = id;
		this.prefix = prefix;
		this.permission = permission;
		this.description = description;
		this.defaultColour = defaultColour;
		this.unlockable = unlockable;
		this.retired = retired;
	}

	public String getId() {
		return id;
	}

	public String getRawPrefix() {
		return prefix;
	}

	public String getPrefix() {
		return prefix.replace("<colourstart>", defaultColour.getColourStart())
				.replace("<colourend>", defaultColour.getColourEnd());
	}

	public String getPrefix(@NotNull PrefixColour colour) {
		return prefix.replace("<colourstart>", colour.getColourStart())
				.replace("<colourend>", colour.getColourEnd());
	}

	public boolean hasPermission() {
		return permission != null;
	}

	public String getPermission() {
		return permission;
	}

	public String getDescription() {
		return description;
	}
	public @NotNull PrefixColour getDefaultColour() {
		return defaultColour;
	}

	public boolean isRetired() {
		return retired;
	}

	public boolean isUnlockable() {
		return unlockable;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Prefix prefix1 = (Prefix) o;
		return isUnlockable() == prefix1.isUnlockable() && isRetired() == prefix1.isRetired() && getId().equals(
				prefix1.getId()) && getRawPrefix().equals(prefix1.getRawPrefix()) && Objects.equals(getPermission(),
																							  prefix1.getPermission()) && Objects.equals(
				getDescription(), prefix1.getDescription());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getRawPrefix(), getPermission(), getDescription(), isUnlockable(), isRetired());
	}

	@Override
	public String toString() {
		return "Prefix{" +
				"id='" + id + '\'' +
				", prefix='" + prefix + '\'' +
				", permission='" + permission + '\'' +
				", description='" + description + '\'' +
				", defaultColour='" + defaultColour.toString() + '\'' +
				", unlockable=" + unlockable +
				", retired=" + retired +
				'}';
	}

	public Component getListItem(boolean bedrock) {
		if(bedrock) {
			if(bedrockListItem == null) {
				bedrockListItem = createComponent("prefix-list-bedrock.item");
			}

			return bedrockListItem;
		} else {
			if(listItem == null) {
				listItem = createComponent("prefix-list.item");
			}

			return listItem;
		}
	}

	public Component getLockedListItem(boolean bedrock) {
		if(bedrock) {
			if(bedrockLockedItem == null) {
				bedrockLockedItem = createComponent("prefix-list-bedrock.item-locked");
			}

			return bedrockLockedItem;
		} else {
			if(lockedItem == null) {
				lockedItem = createComponent("prefix-list.item-locked");
			}

			return lockedItem;
		}
	}

	public Component getSelectedListItem(boolean bedrock) {
		if(bedrock) {
			if(bedrockSelectedItem == null) {
				bedrockSelectedItem = createComponent("prefix-list-bedrock.item-selected");
			}

			return bedrockSelectedItem;
		} else {
			if(selectedItem == null) {
				selectedItem = createComponent("prefix-list.item-selected");
			}

			return selectedItem;
		}
	}

	private Component createComponent(String key) {
		return Messages.getComponent(key, Map.of(
					"id", id,
					"description", description != null ? description : ""
			), Collections.singletonMap("preview", Messages.miniMessage.deserialize(getPrefix())));
	}
}
