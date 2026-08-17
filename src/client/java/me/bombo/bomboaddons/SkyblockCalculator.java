package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class SkyblockCalculator {
   public static EvaluationResult evaluate(String input) {
      try {
         if (input.contains("=")) {
            String[] parts = input.split("=", 2);
            String alias = parts[0].trim().toLowerCase();
            String expression = parts[1].trim();
            if (!alias.isEmpty() && !expression.isEmpty()) {
               expression = cleanSkyblockInput(expression);
               BomboConfig.get().calculatorAliases.put(alias, expression);
               BomboConfig.save();
               return new EvaluationResult((double)0.0F, Component.literal("§aSet alias §b" + alias + " §7to §f" + expression));
            } else {
               return new EvaluationResult("§cInvalid alias definition. Usage: /c <name> = <expression>");
            }
         } else {
            input = cleanSkyblockInput(input);
            LowestBinManager.ensureLoaded();
            List<String> tokens = tokenize(input);
            return parseAndEvaluate(tokens);
         }
      } catch (Exception e) {
         return new EvaluationResult("§cError: " + e.getMessage());
      }
   }

   private static String cleanSkyblockInput(String input) {
      if (input == null) {
         return "";
      } else {
         input = input.replaceAll("(?<=\\d),(?=\\d)", "");
         String[] noisePatterns = new String[]{"(?i)\\byou earn:?\\b", "(?i)\\bcoins?\\b", "(?i)\\bbuy price:?\\b", "(?i)\\bsell price:?\\b", "(?i)\\bprofit:?\\b", "(?i)\\best\\.? value:?\\b", "(?i)\\bworth:?\\b", "(?i)\\bprice:?\\b"};

         for(String pattern : noisePatterns) {
            input = input.replaceAll(pattern, "");
         }

         return input.trim();
      }
   }

   private static List<String> tokenize(String input) {
      List<String> tokens = new ArrayList();
      Pattern pattern = Pattern.compile("[0-9]*\\.?[0-9]+[kKmMbB]?|[a-zA-Z_0-9;]+|[\\+\\-\\*/\\(\\)]");
      Matcher matcher = pattern.matcher(input);

      while(matcher.find()) {
         tokens.add(matcher.group());
      }

      return tokens;
   }

   private static EvaluationResult parseAndEvaluate(List<String> tokens) {
      try {
         List<Object> resolved = new ArrayList();
         List<Component> breakdownParts = new ArrayList();

         for(String token : tokens) {
            if (isOperatorOrParen(token)) {
               resolved.add(token);
               breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.GRAY));
            } else if (isNumber(token)) {
               double val = parseNumber(token);
               resolved.add(val);
               breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.WHITE));
            } else {
               String aliasExpr = (String)BomboConfig.get().calculatorAliases.get(token.toLowerCase());
               if (aliasExpr != null) {
                  EvaluationResult aliasRes = evaluate(aliasExpr);
                  if (aliasRes.error != null) {
                     return aliasRes;
                  }

                  resolved.add(aliasRes.value);
                  breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.YELLOW).append(Component.literal("(" + LowestBinManager.formatPrice(aliasRes.value) + ")").withStyle(ChatFormatting.GRAY)));
               } else {
                  boolean isStrict = token.equals(token.toUpperCase()) || token.contains("_");
                  String id = LowestBinManager.findIdByName(token, isStrict);
                  if (id == null) {
                     return new EvaluationResult("§cUnknown token: " + token);
                  }

                  long price = LowestBinManager.getCachedPrice(id);
                  if (price == -1L) {
                     return new EvaluationResult("§cPrice not found for: " + id);
                  }

                  resolved.add((double)price);
                  breakdownParts.add(Component.literal(id).withStyle(ChatFormatting.AQUA).append(Component.literal("(" + LowestBinManager.formatPrice(price) + ")").withStyle(ChatFormatting.GRAY)));
               }
            }
         }

         double result = evaluateInfix(resolved);
         String formattedRes = result == (double)((long)result) ? String.valueOf((long)result) : String.format("%.3f", result);
         Component finalBreakdown = Component.literal("§6Result: §b" + formattedRes + " §7(§b" + LowestBinManager.formatPrice(result) + "§7) §7(§f");

         for(Component part : breakdownParts) {
            finalBreakdown = finalBreakdown.copy().append(part);
         }

         Component var16 = finalBreakdown.copy().append(Component.literal("§7)"));
         return new EvaluationResult(result, var16);
      } catch (Exception e) {
         return new EvaluationResult("§cEvaluation error: " + e.getMessage());
      }
   }

   private static boolean isOperatorOrParen(String s) {
      return s.length() == 1 && "+-*/()".contains(s);
   }

   private static boolean isNumber(String s) {
      try {
         if (s.matches("(?i)[0-9]*\\.?[0-9]+[kmb]")) {
            return true;
         } else {
            Double.parseDouble(s);
            return true;
         }
      } catch (NumberFormatException var2) {
         return false;
      }
   }

   private static double parseNumber(String s) {
      String lower = s.toLowerCase();
      double multiplier = (double)1.0F;
      if (lower.endsWith("k")) {
         multiplier = (double)1000.0F;
         s = s.substring(0, s.length() - 1);
      } else if (lower.endsWith("m")) {
         multiplier = (double)1000000.0F;
         s = s.substring(0, s.length() - 1);
      } else if (lower.endsWith("b")) {
         multiplier = (double)1.0E9F;
         s = s.substring(0, s.length() - 1);
      }

      return Double.parseDouble(s) * multiplier;
   }

   private static double evaluateInfix(List<Object> tokens) {
      Stack<Double> values = new Stack();
      Stack<Character> ops = new Stack();

      for(int i = 0; i < tokens.size(); ++i) {
         Object token = tokens.get(i);
         if (token instanceof Double) {
            values.push((Double)token);
         } else if (token.equals("(")) {
            ops.push('(');
         } else if (token.equals(")")) {
            while((Character)ops.peek() != '(') {
               values.push(applyOp((Character)ops.pop(), (Double)values.pop(), (Double)values.pop()));
            }

            ops.pop();
         } else if (token instanceof String && "+-*/".contains((String)token)) {
            char op = ((String)token).charAt(0);

            while(!ops.empty() && hasPrecedence(op, (Character)ops.peek())) {
               values.push(applyOp((Character)ops.pop(), (Double)values.pop(), (Double)values.pop()));
            }

            ops.push(op);
         }
      }

      while(!ops.empty()) {
         values.push(applyOp((Character)ops.pop(), (Double)values.pop(), (Double)values.pop()));
      }

      return (Double)values.pop();
   }

   private static boolean hasPrecedence(char op1, char op2) {
      if (op2 != '(' && op2 != ')') {
         return op1 != '*' && op1 != '/' || op2 != '+' && op2 != '-';
      } else {
         return false;
      }
   }

   private static double applyOp(char op, double b, double a) {
      switch (op) {
         case '*':
            return a * b;
         case '+':
            return a + b;
         case ',':
         case '.':
         default:
            return (double)0.0F;
         case '-':
            return a - b;
         case '/':
            if (b == (double)0.0F) {
               throw new UnsupportedOperationException("Cannot divide by zero");
            } else {
               return a / b;
            }
      }
   }

   public static class EvaluationResult {
      public double value;
      public Component breakdown;
      public String error;

      public EvaluationResult(double value, Component breakdown) {
         this.value = value;
         this.breakdown = breakdown;
      }

      public EvaluationResult(String error) {
         this.error = error;
      }
   }
}
