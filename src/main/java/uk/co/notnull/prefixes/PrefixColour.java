/*
 * Prefixes, a Velocity prefix plugin
 *
 * Copyright (c) 2023 James Lyne
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

import java.util.Map;
import java.util.Objects;

public class PrefixColour {
	private final String id;
	private final String colourStart;
	private final String colourEnd;
	private final String permission;
	private final String description;
	private final boolean unlockable;
	private final boolean retired;

	public PrefixColour(String id, String colour) {
		this(id, colour, "", null, null, false, false);
	}

	public PrefixColour(
			String id, String colourStart, String colourEnd, String permission, String description, boolean unlockable, boolean retired) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(colourStart);
		Objects.requireNonNull(colourEnd);

		this.id = id;
		this.colourStart = colourStart;
		this.colourEnd = colourEnd;
		this.permission = permission;
		this.description = description;
		this.unlockable = unlockable;
		this.retired = retired;
	}

	public @NotNull String getId() {
		return id;
	}

	public @NotNull String getColourStart() {
		return colourStart;
	}

	public @NotNull String getColourEnd() {
		return colourEnd;
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
		PrefixColour that = (PrefixColour) o;
		return isUnlockable() == that.isUnlockable() && isRetired() == that.isRetired() && getId().equals(
				that.getId()) && getColourStart().equals(that.getColourStart()) && Objects.equals(getColourEnd(),
																								  that.getColourEnd()) && Objects.equals(
				getPermission(), that.getPermission()) && Objects.equals(getDescription(), that.getDescription());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getColourStart(), getColourEnd(), getPermission(), getDescription(),
							isUnlockable(),
							isRetired());
	}

	@Override
	public String toString() {
		return "PrefixColour{" +
				"id='" + id + '\'' +
				", colourStart='" + colourStart + '\'' +
				", colourEnd='" + colourEnd + '\'' +
				", permission='" + permission + '\'' +
				", description='" + description + '\'' +
				", unlockable=" + unlockable +
				", retired=" + retired +
				'}';
	}

	public Component getListItem(Prefix prefix, boolean bedrock) {
		if(bedrock) {
			return createComponent("colour-list-bedrock.item", prefix);
		} else {
			return createComponent("colour-list.item", prefix);
		}
	}

	public Component getLockedListItem(Prefix prefix, boolean bedrock) {
		if(bedrock) {
			return createComponent("colour-list-bedrock.item-locked", prefix);
		} else {
			return createComponent("colour-list.item-locked", prefix);
		}
	}

	public Component getSelectedListItem(Prefix prefix, boolean bedrock) {
		if(bedrock) {
			return createComponent("colour-list-bedrock.item-selected", prefix);
		} else {
			return createComponent("colour-list.item-selected", prefix);
		}
	}

	private Component createComponent(String key, Prefix prefix) {
		return Messages.getComponent(key, Map.of(
					"id", id,
					"prefix", prefix.getId(),
					"description", description != null ? description : ""
			), Map.of("preview", Messages.miniMessage.deserialize(prefix.getPrefix(this))));
	}
}
