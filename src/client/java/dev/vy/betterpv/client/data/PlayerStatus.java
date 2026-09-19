package dev.vy.betterpv.client.data;

import java.util.Locale;
import java.util.Map;

/** Hypixel player online status for the Home Status control. */
public final class PlayerStatus {
	private static final Map<String, String> LOCATION_NAMES = Map.ofEntries(
		Map.entry("dynamic", "Private Island"),
		Map.entry("hub", "Hub"),
		Map.entry("farming_1", "The Farming Islands"),
		Map.entry("combat_1", "Spider's Den"),
		Map.entry("combat_2", "Blazing Fortress"),
		Map.entry("combat_3", "The End"),
		Map.entry("foraging_1", "The Park"),
		Map.entry("foraging_2", "Galatea"),
		Map.entry("mining_1", "Gold Mine"),
		Map.entry("mining_2", "Deep Caverns"),
		Map.entry("mining_3", "Dwarven Mines"),
		Map.entry("crystal_hollows", "Crystal Hollows"),
		Map.entry("mineshaft", "Glacite Mineshafts"),
		Map.entry("crimson_isle", "Crimson Isle"),
		Map.entry("fishing_1", "Backwater Bayou"),
		Map.entry("dungeon", "Dungeon"),
		Map.entry("dungeon_hub", "Dungeon Hub"),
		Map.entry("dark_auction", "Dark Auction"),
		Map.entry("winter", "Jerry's Workshop"),
		Map.entry("rift", "The Rift"),
		Map.entry("garden", "Garden"),
		Map.entry("kuudra", "Kuudra"),
		Map.entry("instanced", "Kuudra"),
		Map.entry("skyblock", "SkyBlock"),
		Map.entry("bedwars", "Bed Wars"),
		Map.entry("skywars", "SkyWars"),
		Map.entry("murder_mystery", "Murder Mystery"),
		Map.entry("build_battle", "Build Battle"),
		Map.entry("housing", "Housing"),
		Map.entry("arcade", "Arcade"),
		Map.entry("survival_games", "Blitz SG"),
		Map.entry("tntgames", "TNT Games"),
		Map.entry("uhc", "UHC"),
		Map.entry("speed_uhc", "Speed UHC"),
		Map.entry("duels", "Duels"),
		Map.entry("pit", "The Pit"),
		Map.entry("replay", "Replay"),
		Map.entry("wool_games", "Wool Wars"),
		Map.entry("mcgo", "Cops and Crims"),
		Map.entry("battleground", "Warlords"),
		Map.entry("super_smash", "Smash Heroes"),
		Map.entry("gingerbread", "Turbo Kart Racers"),
		Map.entry("legacy", "Classic Games"),
		Map.entry("prototype", "Prototype"),
		Map.entry("walls3", "Mega Walls"),
		Map.entry("smp", "SMP")
	);
	public enum State {
		IDLE,
		LOADING,
		ONLINE,
		OFFLINE,
		ERROR
	}

	private final State state;
	private final String gameType;
	private final String mode;
	private final String map;
	private final String error;

	private PlayerStatus(State state, String gameType, String mode, String map, String error) {
		this.state = state == null ? State.IDLE : state;
		this.gameType = gameType == null ? "" : gameType;
		this.mode = mode == null ? "" : mode;
		this.map = map == null ? "" : map;
		this.error = error == null ? "" : error;
	}

	public static PlayerStatus idle() {
		return new PlayerStatus(State.IDLE, "", "", "", "");
	}

	public static PlayerStatus loading() {
		return new PlayerStatus(State.LOADING, "", "", "", "");
	}

	public static PlayerStatus online(String gameType, String mode, String map) {
		return new PlayerStatus(State.ONLINE, gameType, mode, map, "");
	}

	public static PlayerStatus offline() {
		return new PlayerStatus(State.OFFLINE, "", "", "", "");
	}

	public static PlayerStatus error(String message) {
		return new PlayerStatus(State.ERROR, "", "", "", message);
	}

	public State state() {
		return this.state;
	}

	public String gameType() {
		return this.gameType;
	}

	public String mode() {
		return this.mode;
	}

	public String map() {
		return this.map;
	}

	public String error() {
		return this.error;
	}

	public String buttonLabel() {
		return switch (this.state) {
			case IDLE -> "Status";
			case LOADING -> "…";
			case ONLINE -> {
				String loc = prettyLocation(this.map);
				if (loc.isBlank()) {
					loc = prettyLocation(this.mode);
				}
				if (loc.isBlank()) {
					loc = prettyLocation(this.gameType);
				}
				yield loc.isBlank() ? "Online" : "Online - " + loc;
			}
			case OFFLINE -> "Offline";
			case ERROR -> "Status";
		};
	}

	public int buttonColor(int accent, int online, int offline, int muted) {
		return switch (this.state) {
			case ONLINE -> online;
			case OFFLINE -> offline;
			case LOADING -> muted;
			case ERROR -> muted;
			case IDLE -> accent;
		};
	}

	/** Hypixel {@code combat_3} / {@code dungeon_hub} → display name. */
	public static String prettyLocation(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		String mapped = LOCATION_NAMES.get(key);
		if (mapped != null) {
			return mapped;
		}
		String[] parts = key.split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}
}
