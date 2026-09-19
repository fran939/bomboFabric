package dev.vy.betterpv.client.data;

import java.util.List;
import java.util.OptionalDouble;

/** Computed overview combat/fortune stats from profile + equipped gear lore. */
public final class PlayerStatsSnapshot {
	public record Entry(String id, String label, OptionalDouble value) {
		public Entry {
			id = id == null ? "" : id;
			label = label == null ? "" : label;
			value = value == null ? OptionalDouble.empty() : value;
		}

		public boolean present() {
			return value.isPresent();
		}
	}

	private final List<Entry> entries;

	public PlayerStatsSnapshot(List<Entry> entries) {
		this.entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
	}

	public static PlayerStatsSnapshot empty() {
		return new PlayerStatsSnapshot(List.of());
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public boolean isEmpty() {
		return this.entries.isEmpty() || this.entries.stream().noneMatch(Entry::present);
	}
}
