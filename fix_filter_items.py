import re

with open(r'e:/Users/frand/Documents/bomboaddons-26.1.2/src/client/java/me/bombo/bomboaddons/ItemListOverlay.java', 'r', encoding='utf-8') as f:
    text = f.read()

start_idx = text.find('private static void filterItems() {')
end_idx = text.find('private static boolean isCosmetic(SkyblockItemManager.SkyblockItemInfo info)')

replacement = """private static void filterItems() {
        filteredItems.clear();
        variantMap.clear();
        Map<String, SkyblockItemManager.SkyblockItemInfo> allItems = SkyblockItemManager.getAllItems();
        if (allItems == null) return;
        
        String lowerQuery = query.toLowerCase().trim();
        Map<String, List<SkyblockItemManager.SkyblockItemInfo>> groups = new HashMap<>();

        boolean hideSkins = BomboConfig.get().itemListHideSkins;
        boolean hideNPCs = BomboConfig.get().itemListHideNPCs;
        boolean hideMobs = BomboConfig.get().itemListHideMobs;
        boolean hideVanilla = BomboConfig.get().itemListHideVanilla;

        for (SkyblockItemManager.SkyblockItemInfo info : allItems.values()) {
            if (hideVanilla && info.vanilla) continue;
            
            if (info.id != null) {
                if (hideSkins) {
                    if (info.id.contains("_SKIN") || info.id.contains("DYE") || info.id.endsWith("_SHIMMER") || info.id.endsWith("_PERSONALITY")) continue;
                    if (info.name != null && (info.name.toLowerCase().contains(" skin") || info.name.toLowerCase().contains(" dye"))) continue;
                }
                if (hideNPCs && info.id.contains("_NPC")) continue;
                if (hideMobs && (info.id.endsWith("_MONSTER") || info.id.endsWith("_BOSS") || info.id.endsWith("_MINIBOSS"))) continue;
            }

            if (lowerQuery.isEmpty() || 
                (info.name != null && info.name.toLowerCase().contains(lowerQuery)) || 
                (info.id != null && info.id.toLowerCase().contains(lowerQuery))) {
                
                String baseId = info.id;
                if (baseId != null) {
                    if (baseId.contains(";")) {
                        baseId = baseId.substring(0, baseId.indexOf(";"));
                    }
                    if (baseId.matches("(HOT|BURNING|FIERY|INFERNAL)_(AURORA|CRIMSON|TERROR|FERVOR|HOLLOW)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")) {
                        baseId = baseId.replaceFirst("^(HOT|BURNING|FIERY|INFERNAL)_", "");
                    } else if (baseId.matches("PERFECT_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches(".*_GENERATOR_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("(SOUL_)?CAMPFIRE_TALISMAN_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("ROMEO_AND_JULIET_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("POTION_AFFINITY_TALISMAN_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    }
                }

                if (baseId != null) {
                    groups.computeIfAbsent(baseId, k -> new ArrayList<>()).add(info);
                }
            }
        }
        
        for (List<SkyblockItemManager.SkyblockItemInfo> group : groups.values()) {
            if (group.size() == 1) {
                filteredItems.add(group.get(0));
            } else {
                group.sort((a, b) -> {
                    int tvCmp = Integer.compare(SkyblockItemManager.getTierValue(a.tier), SkyblockItemManager.getTierValue(b.tier));
                    if (tvCmp != 0) return tvCmp;
                    return customNameCompare(a.name != null ? a.name : "", b.name != null ? b.name : "");
                });
                SkyblockItemManager.SkyblockItemInfo rep = group.get(group.size() - 1);
                filteredItems.add(rep);
                variantMap.put(rep.id, group);
            }
        }
        
        // Sort
        int sortType = BomboConfig.get().itemListSortType;
        boolean reverse = BomboConfig.get().itemListSortReverse;
        filteredItems.sort((a, b) -> {
            int result = 0;
            if (sortType == 0) { // Rarity
                int tvA = SkyblockItemManager.getTierValue(a.tier);
                int tvB = SkyblockItemManager.getTierValue(b.tier);
                if (tvA != tvB) result = Integer.compare(tvB, tvA); // Higher rarity first
                else {
                    String nameA = a.name != null ? a.name : "";
                    String nameB = b.name != null ? b.name : "";
                    result = customNameCompare(nameA, nameB);
                }
            } else if (sortType == 1) { // Name
                String nameA = a.name != null ? a.name : "";
                String nameB = b.name != null ? b.name : "";
                result = customNameCompare(nameA, nameB);
            } else { // Price fallback to Name
                String nameA = a.name != null ? a.name : "";
                String nameB = b.name != null ? b.name : "";
                result = customNameCompare(nameA, nameB);
            }
            return reverse ? -result : result;
        });
    }

    """

text = text[:start_idx] + replacement + text[end_idx:]
with open(r'e:/Users/frand/Documents/bomboaddons-26.1.2/src/client/java/me/bombo/bomboaddons/ItemListOverlay.java', 'w', encoding='utf-8') as f:
    f.write(text)
