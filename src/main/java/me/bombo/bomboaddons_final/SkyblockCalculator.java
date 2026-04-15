package me.bombo.bomboaddons_final;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyblockCalculator {

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

    public static EvaluationResult evaluate(String input) {
        try {
            // Check for alias definition: name = expression
            if (input.contains("=")) {
                String[] parts = input.split("=", 2);
                String alias = parts[0].trim().toLowerCase();
                String expression = parts[1].trim();
                if (alias.isEmpty() || expression.isEmpty()) {
                    return new EvaluationResult("§cInvalid alias definition. Usage: /c <name> = <expression>");
                }
                BomboConfig.get().calculatorAliases.put(alias, expression);
                BomboConfig.save();
                return new EvaluationResult(0, Component.literal("§aSet alias §b" + alias + " §7to §f" + expression));
            }

            // Ensure prices are loaded before starting evaluation
            LowestBinManager.ensureLoaded();

            List<String> tokens = tokenize(input);
            return parseAndEvaluate(tokens);
        } catch (Exception e) {
            return new EvaluationResult("§cError: " + e.getMessage());
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("[0-9]*\\.?[0-9]+[kKmMbB]?|[a-zA-Z_0-9;]+|[\\+\\-\\*/\\(\\)]");
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static EvaluationResult parseAndEvaluate(List<String> tokens) {
        try {
            List<Object> resolved = new ArrayList<>();
            List<Component> breakdownParts = new ArrayList<>();

            for (String token : tokens) {
                if (isOperatorOrParen(token)) {
                    resolved.add(token);
                    breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.GRAY));
                } else if (isNumber(token)) {
                    double val = parseNumber(token);
                    resolved.add(val);
                    breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.WHITE));
                } else {
                    // Try to resolve as alias
                    String aliasExpr = BomboConfig.get().calculatorAliases.get(token.toLowerCase());
                    if (aliasExpr != null) {
                        EvaluationResult aliasRes = evaluate(aliasExpr);
                        if (aliasRes.error != null) return aliasRes;
                        resolved.add(aliasRes.value);
                        breakdownParts.add(Component.literal(token).withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal("(" + LowestBinManager.formatPrice((long)aliasRes.value) + ")").withStyle(ChatFormatting.GRAY)));
                    } else {
                        // Try to resolve as Skyblock ID
                        boolean isStrict = token.equals(token.toUpperCase()) || token.contains("_");
                        String id = LowestBinManager.findIdByName(token, isStrict);
                        if (id != null) {
                            long price = LowestBinManager.getCachedPrice(id);
                            if (price != -1) {
                                resolved.add((double) price);
                                breakdownParts.add(Component.literal(id).withStyle(ChatFormatting.AQUA)
                                        .append(Component.literal("(" + LowestBinManager.formatPrice(price) + ")").withStyle(ChatFormatting.GRAY)));
                            } else {
                                return new EvaluationResult("§cPrice not found for: " + id);
                            }
                        } else {
                            // If it's something unrecognizable, maybe it's meant to be a number but tokenize failed?
                            // Or just return error
                            return new EvaluationResult("§cUnknown token: " + token);
                        }
                    }
                }
            }

            double result = evaluateInfix(resolved);
            long resLong = Math.round(result);
            Component finalBreakdown = Component.literal("§6Result: §b" + resLong + " §7(§b" + LowestBinManager.formatPrice(resLong) + "§7) §7(§f");
            for (Component part : breakdownParts) {
                finalBreakdown = finalBreakdown.copy().append(part);
            }
            finalBreakdown = finalBreakdown.copy().append(Component.literal("§7)"));

            return new EvaluationResult(result, finalBreakdown);
        } catch (Exception e) {
            return new EvaluationResult("§cEvaluation error: " + e.getMessage());
        }
    }

    private static boolean isOperatorOrParen(String s) {
        return s.length() == 1 && "+-*/()".contains(s);
    }

    private static boolean isNumber(String s) {
        try {
            if (s.matches("(?i)[0-9]*\\.?[0-9]+[kmb]")) return true;
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double parseNumber(String s) {
        String lower = s.toLowerCase();
        double multiplier = 1.0;
        if (lower.endsWith("k")) { multiplier = 1000.0; s = s.substring(0, s.length()-1); }
        else if (lower.endsWith("m")) { multiplier = 1000000.0; s = s.substring(0, s.length()-1); }
        else if (lower.endsWith("b")) { multiplier = 1000000000.0; s = s.substring(0, s.length()-1); }
        return Double.parseDouble(s) * multiplier;
    }

    private static double evaluateInfix(List<Object> tokens) {
        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();

        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);

            if (token instanceof Double) {
                values.push((Double) token);
            } else if (token.equals("(")) {
                ops.push('(');
            } else if (token.equals(")")) {
                while (ops.peek() != '(')
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                ops.pop();
            } else if (token instanceof String && "+-*/".contains((String)token)) {
                char op = ((String) token).charAt(0);
                while (!ops.empty() && hasPrecedence(op, ops.peek()))
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                ops.push(op);
            }
        }

        while (!ops.empty())
            values.push(applyOp(ops.pop(), values.pop(), values.pop()));

        return values.pop();
    }

    private static boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

    private static double applyOp(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': if (b == 0) throw new UnsupportedOperationException("Cannot divide by zero");
                      return a / b;
        }
        return 0;
    }
}
