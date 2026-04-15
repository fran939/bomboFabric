package me.bombo.bomboaddons_final;

import java.util.Map;

public class RomanNumber {
    private static final Map<String, Integer> ROMAN_TO_DECIMAL = Map.of(
        "I", 1, "II", 2, "III", 3, "IV", 4, "V", 5,
        "VI", 6, "VII", 7, "VIII", 8, "IX", 9, "X", 10
    );

    public static int romanToDecimal(String roman) {
        if (roman == null) return 0;
        String upper = roman.toUpperCase().trim();
        
        // Handle I-X via fast lookup
        if (ROMAN_TO_DECIMAL.containsKey(upper)) {
            return ROMAN_TO_DECIMAL.get(upper);
        }

        // Fallback for larger numbers (though rare in enchants)
        int res = 0;
        for (int i = 0; i < upper.length(); i++) {
            int s1 = value(upper.charAt(i));
            if (i + 1 < upper.length()) {
                int s2 = value(upper.charAt(i + 1));
                if (s1 >= s2) {
                    res = res + s1;
                } else {
                    res = res + s2 - s1;
                    i++;
                }
            } else {
                res = res + s1;
            }
        }
        return res;
    }

    private static int value(char r) {
        if (r == 'I') return 1;
        if (r == 'V') return 5;
        if (r == 'X') return 10;
        if (r == 'L') return 50;
        if (r == 'C') return 100;
        if (r == 'D') return 500;
        if (r == 'M') return 1000;
        return 0;
    }
}
