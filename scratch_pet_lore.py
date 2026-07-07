import re
lines = open(r'e:\Users\frand\Documents\bomboaddons-26.1.2\src\client\java\me\bombo\bomboaddons\SkyblockItemManager.java', 'r', encoding='utf-8').read()

target = """                            String tier = obj.has("tier") ? obj.get("tier").getAsString() : extractedTier;"""

replacement = """                            String tier = obj.has("tier") ? obj.get("tier").getAsString() : extractedTier;
                            
                            // Replace stats for pets using petnums.json
                            if (finalPetNums != null && id.contains(";")) {
                                String baseId = id.substring(0, id.indexOf(";"));
                                if (finalPetNums.has(baseId)) {
                                    try {
                                        com.google.gson.JsonObject pObj = finalPetNums.getAsJsonObject(baseId).getAsJsonObject(tier);
                                        if (pObj != null && pObj.has("100")) {
                                            com.google.gson.JsonObject p100 = pObj.getAsJsonObject("100");
                                            com.google.gson.JsonObject statNums = p100.has("statNums") ? p100.getAsJsonObject("statNums") : new com.google.gson.JsonObject();
                                            com.google.gson.JsonArray otherNums = p100.has("otherNums") ? p100.getAsJsonArray("otherNums") : new com.google.gson.JsonArray();
                                            
                                            for (int i = 0; i < lore.size(); i++) {
                                                String line = lore.get(i).getString();
                                                // Replace {STAT}
                                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\\{([A-Z_]+)\\\\}").matcher(line);
                                                while (m.find()) {
                                                    String stat = m.group(1);
                                                    if (statNums.has(stat)) {
                                                        String val = statNums.get(stat).getAsString();
                                                        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                                                        line = line.replace("{" + stat + "}", val);
                                                    }
                                                }
                                                // Replace {0}, {1} etc
                                                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\\\\{([0-9]+)\\\\}").matcher(line);
                                                while (m2.find()) {
                                                    int idx = Integer.parseInt(m2.group(1));
                                                    if (idx >= 0 && idx < otherNums.size()) {
                                                        String val = otherNums.get(idx).getAsString();
                                                        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                                                        line = line.replace("{" + idx + "}", val);
                                                    }
                                                }
                                                lore.set(i, net.minecraft.network.chat.Component.literal(line));
                                            }
                                        }
                                    } catch (Exception e) {}
                                }
                            }"""

new_lines = lines.replace(target, replacement)

target2 = """                Map<String, SkyblockItemInfo> tempMap = new ConcurrentHashMap<>();
                Map<String, List<SkyblockItemInfo>> tempUsages = new ConcurrentHashMap<>();"""

replacement2 = """                Map<String, SkyblockItemInfo> tempMap = new ConcurrentHashMap<>();
                Map<String, List<SkyblockItemInfo>> tempUsages = new ConcurrentHashMap<>();

                com.google.gson.JsonObject petNums = null;
                try {
                    Path pnPath = NEUDownloader.REPO_DIR.resolve("constants").resolve("petnums.json");
                    if (Files.exists(pnPath)) {
                        try (FileReader reader = new FileReader(pnPath.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                            petNums = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        }
                    }
                } catch(Exception e) {}
                final com.google.gson.JsonObject finalPetNums = petNums;"""

new_lines = new_lines.replace(target2, replacement2)

target3 = """                            String displayname = obj.has("displayname") ? obj.get("displayname").getAsString() : id;"""

replacement3 = """                            String displayname = obj.has("displayname") ? obj.get("displayname").getAsString() : id;
                            if (displayname.contains("{LVL}") && id.contains(";")) {
                                displayname = displayname.replace("{LVL}", "100");
                            }"""

new_lines = new_lines.replace(target3, replacement3)

open(r'e:\Users\frand\Documents\bomboaddons-26.1.2\src\client\java\me\bombo\bomboaddons\SkyblockItemManager.java', 'w', encoding='utf-8').write(new_lines)
