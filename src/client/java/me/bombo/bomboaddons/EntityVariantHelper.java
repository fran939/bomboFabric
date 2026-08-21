package me.bombo.bomboaddons;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class EntityVariantHelper {

   public static class VariantResult {
      public final String lineText;
      public final String buttonText;
      public final String command;
      public final String matchKeyword;

      public VariantResult(String lineText, String buttonText, String command, String matchKeyword) {
         this.lineText = lineText;
         this.buttonText = buttonText;
         this.command = command;
         this.matchKeyword = matchKeyword;
      }
   }

   public static VariantResult inspect(Entity entity) {
      if (entity == null) return null;
      String simpleName = entity.getClass().getSimpleName();

      // 0. Display Entities (ItemDisplay, BlockDisplay, TextDisplay)
      if (entity instanceof Display.ItemDisplay || simpleName.equalsIgnoreCase("ItemDisplay")) {
         try {
            ItemStack stack = null;
            if (entity instanceof Display.ItemDisplay id) {
               stack = id.getItemItemStack();
            } else {
               Method m = entity.getClass().getMethod("getItemItemStack");
               Object res = m.invoke(entity);
               if (res instanceof ItemStack is) stack = is;
            }
            if (stack != null && !stack.isEmpty()) {
               String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               String hoverName = stack.getHoverName().getString();
               String sbId = SkyblockUtils.getSkyblockId(stack);
               String transform = "";
               try {
                  if (entity instanceof Display.ItemDisplay id) {
                     transform = id.getItemTransform().getSerializedName();
                  }
               } catch (Throwable ignored) {}
               
               String idInfo = sbId != null ? " | §b" + sbId : " | §8" + itemPath;
               String transInfo = !transform.isEmpty() && !transform.equalsIgnoreCase("none") ? " §7[§d" + transform + "§7]" : "";
               String key = sbId != null ? sbId : itemPath;
               return new VariantResult(
                  " §7Item Display: §e" + hoverName + " §8(" + itemPath + idInfo + "§8)" + transInfo,
                  "§a[+ Highlight " + key + " Display]",
                  "/b highlight add display:" + key.toLowerCase(Locale.ROOT) + " GOLD",
                  (itemPath + " " + (sbId != null ? sbId : "") + " " + hoverName + " " + transform + " item_display display").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      if (entity instanceof Display.BlockDisplay || simpleName.equalsIgnoreCase("BlockDisplay")) {
         try {
            BlockState state = null;
            if (entity instanceof Display.BlockDisplay bd) {
               state = bd.getBlockState();
            } else {
               Method m = entity.getClass().getMethod("getBlockState");
               Object res = m.invoke(entity);
               if (res instanceof BlockState bs) state = bs;
            }
            if (state != null) {
               String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
               return new VariantResult(
                  " §7Block Display: §e" + blockPath,
                  "§a[+ Highlight " + blockPath + " Display]",
                  "/b highlight add display:" + blockPath.toLowerCase(Locale.ROOT) + " GOLD",
                  (blockPath + " block_display display").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      if (entity instanceof Display.TextDisplay || simpleName.equalsIgnoreCase("TextDisplay")) {
         try {
            String text = "";
            if (entity instanceof Display.TextDisplay td) {
               text = td.getText().getString();
            } else {
               Method m = entity.getClass().getMethod("getText");
               Object res = m.invoke(entity);
               if (res instanceof net.minecraft.network.chat.Component c) text = c.getString();
            }
            return new VariantResult(
               " §7Text Display: §f\"" + text + "§f\"",
               "§a[+ Highlight Text Display]",
               "/b highlight add text_display GOLD",
               (text + " text_display display").toLowerCase(Locale.ROOT)
            );
         } catch (Throwable ignored) {}
      }

      // ItemFrame
      if (entity instanceof ItemFrame || simpleName.toLowerCase(Locale.ROOT).contains("itemframe")) {
         try {
            ItemStack stack = null;
            if (entity instanceof ItemFrame f) {
               stack = f.getItem();
            } else {
               Method m = entity.getClass().getMethod("getItem");
               Object res = m.invoke(entity);
               if (res instanceof ItemStack is) stack = is;
            }
            if (stack != null && !stack.isEmpty()) {
               String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               String hoverName = stack.getHoverName().getString();
               String sbId = SkyblockUtils.getSkyblockId(stack);
               String idInfo = sbId != null ? " | §b" + sbId : " | §8" + itemPath;
               String key = sbId != null ? sbId : itemPath;
               return new VariantResult(
                  " §7Item Frame Item: §e" + hoverName + " §8(" + itemPath + idInfo + "§8)",
                  "§a[+ Highlight " + key + " Frame]",
                  "/b highlight add item_frame:" + key.toLowerCase(Locale.ROOT) + " GOLD",
                  (itemPath + " " + (sbId != null ? sbId : "") + " " + hoverName + " item_frame frame").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      // ItemEntity (Ground Drops)
      if (entity instanceof ItemEntity || simpleName.equalsIgnoreCase("ItemEntity")) {
         try {
            ItemStack stack = null;
            if (entity instanceof ItemEntity ie) {
               stack = ie.getItem();
            } else {
               Method m = entity.getClass().getMethod("getItem");
               Object res = m.invoke(entity);
               if (res instanceof ItemStack is) stack = is;
            }
            if (stack != null && !stack.isEmpty()) {
               String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               String hoverName = stack.getHoverName().getString();
               String sbId = SkyblockUtils.getSkyblockId(stack);
               String idInfo = sbId != null ? " | §b" + sbId : " | §8" + itemPath;
               String key = sbId != null ? sbId : itemPath;
               return new VariantResult(
                  " §7Item Drop: §e" + hoverName + " x" + stack.getCount() + " §8(" + itemPath + idInfo + "§8)",
                  "§a[+ Highlight " + key + " Drop]",
                  "/b highlight add item:" + key.toLowerCase(Locale.ROOT) + " GOLD",
                  (itemPath + " " + (sbId != null ? sbId : "") + " " + hoverName + " item drop").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      // 1. Axolotl
      if (simpleName.equalsIgnoreCase("Axolotl") || entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl) {
         String varName = "LUCY";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl) {
               varName = axolotl.getVariant().name().toUpperCase(Locale.ROOT);
            } else {
               Method m = entity.getClass().getMethod("getVariant");
               Object res = m.invoke(entity);
               if (res instanceof Enum<?> e) varName = e.name().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         String friendly = switch (varName) {
            case "LUCY" -> "Pink";
            case "WILD" -> "Brown";
            case "GOLD" -> "Yellow";
            case "CYAN" -> "Cyan";
            case "BLUE" -> "Rare Blue";
            default -> varName;
         };
         return new VariantResult(
            " §7Axolotl Variant: §e" + varName + " §7(§b" + friendly + "§7)",
            "§a[+ Highlight " + varName + " Axolotl]",
            "/b highlight add axolotl:" + varName.toLowerCase(Locale.ROOT) + " GOLD",
            (varName + " " + friendly + " axolotl").toLowerCase(Locale.ROOT)
         );
      }

      // 2. Parrot
      if (simpleName.equalsIgnoreCase("Parrot") || entity instanceof net.minecraft.world.entity.animal.Parrot) {
         String varName = "RED_BLUE";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Parrot parrot) {
               varName = parrot.getVariant().name().toUpperCase(Locale.ROOT);
            } else {
               Method m = entity.getClass().getMethod("getVariant");
               Object res = m.invoke(entity);
               if (res instanceof Enum<?> e) varName = e.name().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         String friendly = switch (varName) {
            case "RED_BLUE" -> "Red";
            case "BLUE" -> "Blue";
            case "GREEN" -> "Green";
            case "YELLOW_BLUE" -> "Cyan/Yellow";
            case "GRAY" -> "Gray";
            default -> varName;
         };
         return new VariantResult(
            " §7Parrot Variant: §e" + varName + " §7(§b" + friendly + "§7)",
            "§a[+ Highlight " + varName + " Parrot]",
            "/b highlight add parrot:" + varName.toLowerCase(Locale.ROOT) + " GOLD",
            (varName + " " + friendly + " parrot").toLowerCase(Locale.ROOT)
         );
      }

      // 3. Cat
      if (simpleName.equalsIgnoreCase("Cat") || entity instanceof net.minecraft.world.entity.animal.Cat) {
         String catVar = "TABBY";
         String collar = "";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Cat cat) {
               var vKey = cat.getVariant().unwrapKey();
               if (vKey.isPresent()) catVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
               if (cat.isTame()) collar = " §7| Collar: §d" + cat.getCollarColor().getName();
            } else {
               Method m = entity.getClass().getMethod("getVariant");
               Object res = m.invoke(entity);
               if (res instanceof Holder<?> h) {
                  var vKey = h.unwrapKey();
                  if (vKey.isPresent()) catVar = vKey.get().toString().toUpperCase(Locale.ROOT);
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Cat Variant: §e" + catVar + collar,
            "§a[+ Highlight " + catVar + " Cat]",
            "/b highlight add cat:" + catVar.toLowerCase(Locale.ROOT) + " GOLD",
            (catVar + " cat").toLowerCase(Locale.ROOT)
         );
      }

      // 4. Wolf
      if (simpleName.equalsIgnoreCase("Wolf") || entity instanceof net.minecraft.world.entity.animal.Wolf) {
         String wolfVar = "PALE";
         String collar = "";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Wolf wolf) {
               var vKey = wolf.getVariant().unwrapKey();
               if (vKey.isPresent()) wolfVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
               if (wolf.isTame()) collar = " §7| Collar: §d" + wolf.getCollarColor().getName();
            } else {
               Method m = entity.getClass().getMethod("getVariant");
               Object res = m.invoke(entity);
               if (res instanceof Holder<?> h) {
                  var vKey = h.unwrapKey();
                  if (vKey.isPresent()) wolfVar = vKey.get().toString().toUpperCase(Locale.ROOT);
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Wolf Variant: §e" + wolfVar + collar,
            "§a[+ Highlight " + wolfVar + " Wolf]",
            "/b highlight add wolf:" + wolfVar.toLowerCase(Locale.ROOT) + " GOLD",
            (wolfVar + " wolf").toLowerCase(Locale.ROOT)
         );
      }

      // 5. Frog
      if (simpleName.equalsIgnoreCase("Frog") || entity instanceof net.minecraft.world.entity.animal.frog.Frog) {
         String frogVar = "TEMPERATE";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.frog.Frog frog) {
               var vKey = frog.getVariant().unwrapKey();
               if (vKey.isPresent()) frogVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Frog Variant: §e" + frogVar,
            "§a[+ Highlight " + frogVar + " Frog]",
            "/b highlight add frog:" + frogVar.toLowerCase(Locale.ROOT) + " GOLD",
            (frogVar + " frog").toLowerCase(Locale.ROOT)
         );
      }

      // 6. Panda
      if (simpleName.equalsIgnoreCase("Panda") || entity instanceof net.minecraft.world.entity.animal.panda.Panda) {
         String mainGene = "NORMAL";
         String hiddenGene = "NORMAL";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.panda.Panda panda) {
               mainGene = panda.getMainGene().getSerializedName().toUpperCase(Locale.ROOT);
               hiddenGene = panda.getHiddenGene().getSerializedName().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Panda Gene: §e" + mainGene + " §7(Hidden: §8" + hiddenGene + "§7)",
            "§a[+ Highlight " + mainGene + " Panda]",
            "/b highlight add panda:" + mainGene.toLowerCase(Locale.ROOT) + " GOLD",
            (mainGene + " panda").toLowerCase(Locale.ROOT)
         );
      }

      // 7. Horse
      if (simpleName.equalsIgnoreCase("Horse") || entity instanceof net.minecraft.world.entity.animal.horse.Horse) {
         String color = "WHITE";
         String markings = "NONE";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.horse.Horse horse) {
               color = horse.getVariant().name();
               markings = horse.getMarkings().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Horse: Color: §e" + color + " §7| Markings: §f" + markings,
            "§a[+ Highlight " + color + " Horse]",
            "/b highlight add horse:" + color.toLowerCase(Locale.ROOT) + " GOLD",
            (color + " horse").toLowerCase(Locale.ROOT)
         );
      }

      // 8. Llama
      if (simpleName.toLowerCase(Locale.ROOT).contains("llama") || entity instanceof net.minecraft.world.entity.animal.horse.Llama) {
         String llamaVar = "CREAMY";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.horse.Llama llama) {
               llamaVar = llama.getVariant().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Llama Variant: §e" + llamaVar,
            "§a[+ Highlight " + llamaVar + " Llama]",
            "/b highlight add llama:" + llamaVar.toLowerCase(Locale.ROOT) + " GOLD",
            (llamaVar + " llama").toLowerCase(Locale.ROOT)
         );
      }

      // 9. Rabbit
      if (simpleName.equalsIgnoreCase("Rabbit") || entity instanceof net.minecraft.world.entity.animal.Rabbit) {
         String rabbitVar = "BROWN";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Rabbit rabbit) {
               rabbitVar = rabbit.getVariant().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Rabbit Variant: §e" + rabbitVar,
            "§a[+ Highlight " + rabbitVar + " Rabbit]",
            "/b highlight add rabbit:" + rabbitVar.toLowerCase(Locale.ROOT) + " GOLD",
            (rabbitVar + " rabbit").toLowerCase(Locale.ROOT)
         );
      }

      // 10. Sheep
      if (simpleName.equalsIgnoreCase("Sheep") || entity instanceof net.minecraft.world.entity.animal.Sheep) {
         String sheepCol = "WHITE";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Sheep sheep) {
               sheepCol = sheep.getColor().getName().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Sheep Color: §e" + sheepCol,
            "§a[+ Highlight " + sheepCol + " Sheep]",
            "/b highlight add sheep:" + sheepCol.toLowerCase(Locale.ROOT) + " GOLD",
            (sheepCol + " sheep").toLowerCase(Locale.ROOT)
         );
      }

      // 11. Fox
      if (simpleName.equalsIgnoreCase("Fox") || entity instanceof net.minecraft.world.entity.animal.Fox) {
         String foxVar = "RED";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.Fox fox) {
               foxVar = fox.getVariant().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Fox Variant: §e" + foxVar,
            "§a[+ Highlight " + foxVar + " Fox]",
            "/b highlight add fox:" + foxVar.toLowerCase(Locale.ROOT) + " GOLD",
            (foxVar + " fox").toLowerCase(Locale.ROOT)
         );
      }

      // 12. Mooshroom
      if (simpleName.equalsIgnoreCase("MushroomCow") || simpleName.equalsIgnoreCase("Mooshroom") || entity instanceof net.minecraft.world.entity.animal.MushroomCow) {
         String cowVar = "RED";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.MushroomCow cow) {
               cowVar = cow.getVariant().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Mooshroom Variant: §e" + cowVar,
            "§a[+ Highlight " + cowVar + " Mooshroom]",
            "/b highlight add mooshroom:" + cowVar.toLowerCase(Locale.ROOT) + " GOLD",
            (cowVar + " mooshroom").toLowerCase(Locale.ROOT)
         );
      }

      // 13. Goat
      if (simpleName.equalsIgnoreCase("Goat") || entity instanceof net.minecraft.world.entity.animal.goat.Goat) {
         boolean screaming = false;
         try {
            if (entity instanceof net.minecraft.world.entity.animal.goat.Goat goat) {
               screaming = goat.isScreamingGoat();
            }
         } catch (Throwable ignored) {}
         String typeStr = screaming ? "SCREAMING" : "NORMAL";
         return new VariantResult(
            " §7Goat Type: §e" + typeStr,
            screaming ? "§a[+ Highlight Screaming Goat]" : null,
            screaming ? "/b highlight add goat:screaming GOLD" : null,
            (typeStr + " goat").toLowerCase(Locale.ROOT)
         );
      }

      // 14. Shulker
      if (simpleName.equalsIgnoreCase("Shulker") || entity instanceof net.minecraft.world.entity.monster.Shulker) {
         String colName = "DEFAULT";
         try {
            if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker) {
               var dye = shulker.getColor();
               if (dye != null) colName = dye.getName().toUpperCase(Locale.ROOT);
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Shulker Color: §e" + colName,
            "§a[+ Highlight " + colName + " Shulker]",
            "/b highlight add shulker:" + colName.toLowerCase(Locale.ROOT) + " GOLD",
            (colName + " shulker").toLowerCase(Locale.ROOT)
         );
      }

      // 15. Tropical Fish
      if (simpleName.equalsIgnoreCase("TropicalFish") || entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish) {
         String baseColor = "WHITE";
         String patColor = "WHITE";
         String pattern = "KOB";
         try {
            if (entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish fish) {
               baseColor = fish.getBaseColor().getName();
               patColor = fish.getPatternColor().getName();
               pattern = fish.getPattern().name();
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Tropical Fish: Base: §b" + baseColor + " §7| Pattern: §e" + patColor + " (" + pattern + ")",
            "§a[+ Highlight " + baseColor + " Fish]",
            "/b highlight add tropical_fish:" + baseColor.toLowerCase(Locale.ROOT) + " GOLD",
            (baseColor + " " + patColor + " " + pattern + " tropical_fish").toLowerCase(Locale.ROOT)
         );
      }

      // 16. Villager
      if (simpleName.equalsIgnoreCase("Villager") || entity instanceof net.minecraft.world.entity.npc.Villager) {
         try {
            if (entity instanceof net.minecraft.world.entity.npc.Villager villager) {
               var vData = villager.getVillagerData();
               String vType = vData.getType().toString();
               String vProf = vData.getProfession().name();
               int vLevel = vData.getLevel();
               return new VariantResult(
                  " §7Villager: Type: §e" + vType + " §7| Profession: §a" + vProf + " §7(Level §b" + vLevel + "§7)",
                  null, null, (vProf + " " + vType + " villager").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      // 17. Zombie Villager
      if (simpleName.equalsIgnoreCase("ZombieVillager") || entity instanceof net.minecraft.world.entity.monster.ZombieVillager) {
         try {
            if (entity instanceof net.minecraft.world.entity.monster.ZombieVillager zv) {
               var vData = zv.getVillagerData();
               String vType = vData.getType().toString();
               String vProf = vData.getProfession().name();
               return new VariantResult(
                  " §7Zombie Villager: Type: §e" + vType + " §7| Profession: §a" + vProf,
                  null, null, (vProf + " " + vType + " zombie_villager").toLowerCase(Locale.ROOT)
               );
            }
         } catch (Throwable ignored) {}
      }

      return null;
   }

   public static boolean matchesVariant(Entity entity, String variantReq) {
      if (entity == null || variantReq == null) return false;
      VariantResult res = inspect(entity);
      if (res != null) {
         String req = variantReq.toLowerCase(Locale.ROOT).trim();
         if (res.matchKeyword != null && res.matchKeyword.contains(req)) {
            return true;
         }
         if (res.lineText != null && res.lineText.toLowerCase(Locale.ROOT).contains(req)) {
            return true;
         }
      }
      return false;
   }
}
