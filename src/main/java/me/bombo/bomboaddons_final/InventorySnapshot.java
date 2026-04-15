package me.bombo.bomboaddons_final;

import java.util.List;

public class InventorySnapshot {
    public String guiName;
    public String timestamp;
    public List<String> itemData; // Base64 NBT strings

    public InventorySnapshot() {
    }

    public InventorySnapshot(String guiName, String timestamp, List<String> itemData) {
        this.guiName = guiName;
        this.timestamp = timestamp;
        this.itemData = itemData;
    }
}
