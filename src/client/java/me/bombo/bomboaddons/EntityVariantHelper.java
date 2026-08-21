package me.bombo.bomboaddons;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
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

      // 0. ItemDisplay
      if (simpleName.equalsIgnoreCase("ItemDisplay") || simpleName.toLowerCase(Locale.ROOT).contains("itemdisplay")) {
         try {
            ItemStack stack = null;
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == ItemStack.class) {
                  Object res = m.invoke(entity);
                  if (res instanceof ItemStack is && !is.isEmpty()) {
                     stack = is;
                     break;
                  }
               }
            }
            if (stack != null && !stack.isEmpty()) {
               String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               String hoverName = stack.getHoverName().getString();
               String sbId = SkyblockUtils.getSkyblockId(stack);
               String transform = "";
               try {
                  for (Method m : entity.getClass().getMethods()) {
                     if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("transform")) {
                        Object res = m.invoke(entity);
                        if (res != null) {
                           transform = res.toString().toLowerCase(Locale.ROOT);
                           break;
                        }
                     }
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

      // BlockDisplay
      if (simpleName.equalsIgnoreCase("BlockDisplay") || simpleName.toLowerCase(Locale.ROOT).contains("blockdisplay")) {
         try {
            BlockState state = null;
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == BlockState.class) {
                  Object res = m.invoke(entity);
                  if (res instanceof BlockState bs) {
                     state = bs;
                     break;
                  }
               }
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

      // TextDisplay
      if (simpleName.equalsIgnoreCase("TextDisplay") || simpleName.toLowerCase(Locale.ROOT).contains("textdisplay")) {
         try {
            String text = "";
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == net.minecraft.network.chat.Component.class) {
                  Object res = m.invoke(entity);
                  if (res instanceof net.minecraft.network.chat.Component c) {
                     text = c.getString();
                     break;
                  }
               }
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
      if (simpleName.toLowerCase(Locale.ROOT).contains("itemframe")) {
         try {
            ItemStack stack = null;
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == ItemStack.class && m.getName().toLowerCase(Locale.ROOT).contains("item")) {
                  Object res = m.invoke(entity);
                  if (res instanceof ItemStack is && !is.isEmpty()) {
                     stack = is;
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("ItemEntity") || simpleName.toLowerCase(Locale.ROOT).contains("itementity")) {
         try {
            ItemStack stack = null;
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == ItemStack.class && m.getName().toLowerCase(Locale.ROOT).contains("item")) {
                  Object res = m.invoke(entity);
                  if (res instanceof ItemStack is && !is.isEmpty()) {
                     stack = is;
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Axolotl") || simpleName.toLowerCase(Locale.ROOT).contains("axolotl")) {
         String varName = "LUCY";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     varName = res.toString().toUpperCase(Locale.ROOT);
                     if (varName.contains(".")) varName = varName.substring(varName.lastIndexOf('.') + 1);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Parrot") || simpleName.toLowerCase(Locale.ROOT).contains("parrot")) {
         String varName = "RED_BLUE";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     varName = res.toString().toUpperCase(Locale.ROOT);
                     if (varName.contains(".")) varName = varName.substring(varName.lastIndexOf('.') + 1);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Cat") || simpleName.toLowerCase(Locale.ROOT).contains("cat")) {
         String catVar = "TABBY";
         String collar = "";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res instanceof Holder<?> h) {
                     var vKey = h.unwrapKey();
                     if (vKey.isPresent()) catVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
                  } else if (res != null) {
                     catVar = res.toString().toUpperCase(Locale.ROOT);
                     if (catVar.contains(".")) catVar = catVar.substring(catVar.lastIndexOf('.') + 1);
                  }
                  break;
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
      if (simpleName.equalsIgnoreCase("Wolf") || simpleName.toLowerCase(Locale.ROOT).contains("wolf")) {
         String wolfVar = "PALE";
         String collar = "";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res instanceof Holder<?> h) {
                     var vKey = h.unwrapKey();
                     if (vKey.isPresent()) wolfVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
                  } else if (res != null) {
                     wolfVar = res.toString().toUpperCase(Locale.ROOT);
                     if (wolfVar.contains(".")) wolfVar = wolfVar.substring(wolfVar.lastIndexOf('.') + 1);
                  }
                  break;
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
      if (simpleName.equalsIgnoreCase("Frog") || simpleName.toLowerCase(Locale.ROOT).contains("frog")) {
         String frogVar = "TEMPERATE";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res instanceof Holder<?> h) {
                     var vKey = h.unwrapKey();
                     if (vKey.isPresent()) frogVar = vKey.get().identifier().getPath().toUpperCase(Locale.ROOT);
                  } else if (res != null) {
                     frogVar = res.toString().toUpperCase(Locale.ROOT);
                     if (frogVar.contains(".")) frogVar = frogVar.substring(frogVar.lastIndexOf('.') + 1);
                  }
                  break;
               }
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
      if (simpleName.equalsIgnoreCase("Panda") || simpleName.toLowerCase(Locale.ROOT).contains("panda")) {
         String mainGene = "NORMAL";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("gene")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     mainGene = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Panda Gene: §e" + mainGene,
            "§a[+ Highlight " + mainGene + " Panda]",
            "/b highlight add panda:" + mainGene.toLowerCase(Locale.ROOT) + " GOLD",
            (mainGene + " panda").toLowerCase(Locale.ROOT)
         );
      }

      // 7. Horse
      if (simpleName.equalsIgnoreCase("Horse") || simpleName.toLowerCase(Locale.ROOT).contains("horse")) {
         String color = "WHITE";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     color = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Horse Variant: §e" + color,
            "§a[+ Highlight " + color + " Horse]",
            "/b highlight add horse:" + color.toLowerCase(Locale.ROOT) + " GOLD",
            (color + " horse").toLowerCase(Locale.ROOT)
         );
      }

      // 8. Llama
      if (simpleName.toLowerCase(Locale.ROOT).contains("llama")) {
         String llamaVar = "CREAMY";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     llamaVar = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Rabbit") || simpleName.toLowerCase(Locale.ROOT).contains("rabbit")) {
         String rabbitVar = "BROWN";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     rabbitVar = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Sheep") || simpleName.toLowerCase(Locale.ROOT).contains("sheep")) {
         String sheepCol = "WHITE";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("color")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     sheepCol = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("Fox") || simpleName.toLowerCase(Locale.ROOT).contains("fox")) {
         String foxVar = "RED";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     foxVar = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
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
      if (simpleName.equalsIgnoreCase("MushroomCow") || simpleName.equalsIgnoreCase("Mooshroom") || simpleName.toLowerCase(Locale.ROOT).contains("mushroomcow")) {
         String cowVar = "RED";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("variant")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     cowVar = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Mooshroom Variant: §e" + cowVar,
            "§a[+ Highlight " + cowVar + " Mooshroom]",
            "/b highlight add mooshroom:" + cowVar.toLowerCase(Locale.ROOT) + " GOLD",
            (cowVar + " mooshroom").toLowerCase(Locale.ROOT)
         );
      }

      // 13. Shulker
      if (simpleName.equalsIgnoreCase("Shulker") || simpleName.toLowerCase(Locale.ROOT).contains("shulker")) {
         String colName = "DEFAULT";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("color")) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     colName = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Shulker Color: §e" + colName,
            "§a[+ Highlight " + colName + " Shulker]",
            "/b highlight add shulker:" + colName.toLowerCase(Locale.ROOT) + " GOLD",
            (colName + " shulker").toLowerCase(Locale.ROOT)
         );
      }

      // 14. Tropical Fish
      if (simpleName.equalsIgnoreCase("TropicalFish") || simpleName.toLowerCase(Locale.ROOT).contains("tropicalfish")) {
         String baseColor = "WHITE";
         try {
            for (Method m : entity.getClass().getMethods()) {
               if (m.getParameterCount() == 0 && (m.getName().toLowerCase(Locale.ROOT).contains("basecolor") || m.getName().toLowerCase(Locale.ROOT).contains("color"))) {
                  Object res = m.invoke(entity);
                  if (res != null) {
                     baseColor = res.toString().toUpperCase(Locale.ROOT);
                     break;
                  }
               }
            }
         } catch (Throwable ignored) {}
         return new VariantResult(
            " §7Tropical Fish: Color: §b" + baseColor,
            "§a[+ Highlight " + baseColor + " Fish]",
            "/b highlight add tropical_fish:" + baseColor.toLowerCase(Locale.ROOT) + " GOLD",
            (baseColor + " tropical_fish").toLowerCase(Locale.ROOT)
         );
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
