package dev.vy.betterpv.client.data;

import java.util.List;

/** Public Mojang username history for the Home name tooltip. */
public final class UsernameHistory {
	public record Entry(String username, String changedAt) {
		public Entry {
			username = username == null ? "" : username;
			changedAt = changedAt == null ? "" : changedAt;
		}
	}

	public enum State {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	private final State state;
	private final List<Entry> entries;
	private final String error;

	private UsernameHistory(State state, List<Entry> entries, String error) {
		this.state = state == null ? State.IDLE : state;
		this.entries = entries == null ? List.of() : List.copyOf(entries);
		this.error = error == null ? "" : error;
	}

	public static UsernameHistory idle() {
		return new UsernameHistory(State.IDLE, List.of(), "");
	}

	public static UsernameHistory loading() {
		return new UsernameHistory(State.LOADING, List.of(), "");
	}

	public static UsernameHistory ready(List<Entry> entries) {
		return new UsernameHistory(State.READY, entries, "");
	}

	public static UsernameHistory error(String message) {
		return new UsernameHistory(State.ERROR, List.of(), message);
	}

	public State state() {
		return this.state;
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public String error() {
		return this.error;
	}

	public boolean loaded() {
		return this.state == State.READY || this.state == State.ERROR;
	}
}
