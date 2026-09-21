package me.bombo.bomboaddons.features.critters;

public record CritterEvent(
    Type type,
    Critter critter,
    String catcher,
    int shards,
    boolean sparkling
) {
    public enum Type {
        OWN_CATCH,
        SHARED_CATCH,
        ATTEMPT,
        FAILED,
        ENTERED_SAFARI
    }

    public boolean isCatch() {
        return type == Type.OWN_CATCH || type == Type.SHARED_CATCH;
    }
}
