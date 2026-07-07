text = open(r'e:/Users/frand/Documents/bomboaddons-26.1.2/src/client/java/me/bombo/bomboaddons/ItemListOverlay.java', encoding='utf-8').read()

customNameCompare = """    private static int customNameCompare(String nameA, String nameB) {
        // Kuudra armor prefixes
        java.util.List<String> kuudra = java.util.List.of("", "Hot ", "Burning ", "Fiery ", "Infernal ");
        for (String suffix : java.util.List.of("Aurora Helmet", "Aurora Chestplate", "Aurora Leggings", "Aurora Boots", 
                                                "Crimson Helmet", "Crimson Chestplate", "Crimson Leggings", "Crimson Boots",
                                                "Terror Helmet", "Terror Chestplate", "Terror Leggings", "Terror Boots",
                                                "Fervor Helmet", "Fervor Chestplate", "Fervor Leggings", "Fervor Boots",
                                                "Hollow Helmet", "Hollow Chestplate", "Hollow Leggings", "Hollow Boots")) {
            boolean aIsKuudra = false;
            boolean bIsKuudra = false;
            int aRank = -1;
            int bRank = -1;
            for (int i = 0; i < kuudra.size(); i++) {
                if (nameA.equalsIgnoreCase(kuudra.get(i) + suffix)) { aIsKuudra = true; aRank = i; }
                if (nameB.equalsIgnoreCase(kuudra.get(i) + suffix)) { bIsKuudra = true; bRank = i; }
            }
            if (aIsKuudra && bIsKuudra) {
                return Integer.compare(aRank, bRank);
            }
        }
        
        // Roman Numeral Suffixes (Enchants, Minions, Perfect Armor)
        int lastSpaceA = nameA.lastIndexOf(' ');
        int lastSpaceB = nameB.lastIndexOf(' ');
        if (lastSpaceA != -1 && lastSpaceB != -1) {
            String prefixA = nameA.substring(0, lastSpaceA);
            String prefixB = nameB.substring(0, lastSpaceB);
            if (prefixA.equalsIgnoreCase(prefixB)) {
                String suffixA = nameA.substring(lastSpaceA + 1);
                String suffixB = nameB.substring(lastSpaceB + 1);
                int romanA = me.bombo.bomboaddons.RomanNumber.romanToDecimal(suffixA);
                int romanB = me.bombo.bomboaddons.RomanNumber.romanToDecimal(suffixB);
                if (romanA > 0 && romanB > 0) {
                    return Integer.compare(romanA, romanB);
                }
            }
        }
        
        return nameA.compareToIgnoreCase(nameB);
    }

"""

start_idx = text.find('private static void filterItems() {')
text = text[:start_idx] + customNameCompare + text[start_idx:]
open(r'e:/Users/frand/Documents/bomboaddons-26.1.2/src/client/java/me/bombo/bomboaddons/ItemListOverlay.java', 'w', encoding='utf-8').write(text)
