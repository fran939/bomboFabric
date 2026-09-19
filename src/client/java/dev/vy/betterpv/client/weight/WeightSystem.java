package dev.vy.betterpv.client.weight;

public enum WeightSystem {
	SENITHER("Senither"),
	LILY("Lily");

	private final String display;

	WeightSystem(String display) {
		this.display = display;
	}

	public String display() {
		return this.display;
	}

	public WeightSystem other() {
		return this == SENITHER ? LILY : SENITHER;
	}
}
