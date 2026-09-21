package me.bombo.bomboaddons.features.dungeons.map;

public class DungeonDoor {
    public enum Type {
        NONE(0),
        NORMAL(0xFF8D5B28),
        WITHER(0xFF111827),
        BLOOD(0xFFC62828),
        ENTRANCE(0xFF2E7D32),
        OPENED(0xFF4B5563);

        public final int color;
        Type(int color) {
            this.color = color;
        }
    }

    public final int gridX;
    public final int gridZ;
    public final boolean isHorizontal; // true = between (gx, gz) and (gx+1, gz), false = between (gx, gz) and (gx, gz+1)
    public Type type = Type.NONE;
    public boolean opened = false;

    public DungeonDoor(int gridX, int gridZ, boolean isHorizontal, Type type) {
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.isHorizontal = isHorizontal;
        this.type = type;
    }
}
