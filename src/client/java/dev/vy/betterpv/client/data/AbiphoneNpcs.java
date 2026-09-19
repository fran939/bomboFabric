package dev.vy.betterpv.client.data;

import java.util.Locale;
import java.util.Map;

/** Maps Abiphone contact ids → NEU NPC skull item ids. */
public final class AbiphoneNpcs {
	private static final Map<String, String> ALIASES = Map.ofEntries(
		Map.entry("pet_sitter", "KAT_NPC"),
		Map.entry("community_shop", "ELIZABETH_NPC"),
		Map.entry("thaumaturgist", "HEX_NPC"),
		Map.entry("arrow_forger", "JAX_NPC"),
		Map.entry("drill_fuel_mechanic", "JOAN_NPC"),
		Map.entry("forge_foreman", "FORGE_FOREMAN_NPC"),
		Map.entry("plumber", "PLUMBER_JOE_NPC"),
		Map.entry("fairy", "TIA_THE_FAIRY_NPC"),
		Map.entry("slayer", "MADDOX_THE_SLAYER_NPC"),
		Map.entry("gemstone", "X_NPC"),
		Map.entry("telekinesis_applier", "HOVER_EGG_NPC"),
		Map.entry("trevor_the_trapper", "TRAPPER_NPC"),
		Map.entry("shady_bartender", "SHADY_BARTENDER_NPC"),
		Map.entry("bartender", "BARTENDER_NPC"),
		Map.entry("queen_mismyla", "QUEEN_MISMYLA_NPC"),
		Map.entry("queen", "QUEEN_NYX_NPC"),
		Map.entry("st_jerry", "ST_JERRY_NPC"),
		Map.entry("wool_weaver", "WOOL_WEAVER_NPC"),
		Map.entry("lumber_merchant", "LUMBER_MERCHANT_NPC"),
		Map.entry("captain_ahone", "CAPTAIN_AHONE_NPC"),
		Map.entry("pet_collector", "PET_COLLECTOR_NPC"),
		Map.entry("pet_trainer", "PET_TRAINER_NPC"),
		Map.entry("blacksmith", "BLACKSMITH_NPC"),
		Map.entry("gatekeeper", "GATEKEEPER_NPC"),
		Map.entry("kuudra_gatekeeper", "KUUDRA_GATEKEEPER_NPC"),
		Map.entry("clerk_seraphine", "CLERK_SERAPHINE_NPC"),
		Map.entry("mad_redstone_engineer", "MAD_REDSTONE_ENGINEER_NPC"),
		Map.entry("pesthunter_phillip", "PESTHUNTER_PHILLIP_NPC"),
		Map.entry("spider_tamer", "SPIDER_TAMER_NPC"),
		Map.entry("feast_baker_scott", "BAKER_SCOTT_NPC"),
		Map.entry("feast_chef_ted", "CHEF_TED_NPC"),
		Map.entry("frozen_alex", "FROZEN_ALEX_NPC"),
		Map.entry("junker_joel", "JUNKER_JOEL_NPC"),
		Map.entry("jake_lab", "JAKE_NPC"),
		Map.entry("spooky", "FEAR_MONGERER_NPC"),
		Map.entry("trinity", "SISTER_TRINITY_NPC"),
		Map.entry("tony", "TONY_NPC"),
		Map.entry("dalir", "DALIR_NPC"),
		Map.entry("anita", "ANITA_NPC"),
		Map.entry("shaggy", "SHAGGY_NPC"),
		Map.entry("jacob", "JACOB_NPC")
	);

	private AbiphoneNpcs() {
	}

	public static String neuId(String contactId) {
		if (contactId == null || contactId.isBlank()) {
			return "";
		}
		String key = contactId.toLowerCase(Locale.ROOT);
		String alias = ALIASES.get(key);
		if (alias != null) {
			return alias;
		}
		return key.toUpperCase(Locale.ROOT) + "_NPC";
	}
}
