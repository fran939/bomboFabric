package me.bombo.bomboaddons;

import java.util.Map;

public class RomanNumber {
   private static final Map<String, Integer> ROMAN_TO_DECIMAL = Map.of("I", 1, "II", 2, "III", 3, "IV", 4, "V", 5, "VI", 6, "VII", 7, "VIII", 8, "IX", 9, "X", 10);

   public static int romanToDecimal(String roman) {
      if (roman == null) {
         return 0;
      } else {
         String upper = roman.toUpperCase().trim();
         if (ROMAN_TO_DECIMAL.containsKey(upper)) {
            return (Integer)ROMAN_TO_DECIMAL.get(upper);
         } else {
            int res = 0;

            for(int i = 0; i < upper.length(); ++i) {
               int s1 = value(upper.charAt(i));
               if (i + 1 < upper.length()) {
                  int s2 = value(upper.charAt(i + 1));
                  if (s1 >= s2) {
                     res += s1;
                  } else {
                     res = res + s2 - s1;
                     ++i;
                  }
               } else {
                  res += s1;
               }
            }

            return res;
         }
      }
   }

   private static int value(char r) {
      if (r == 'I') {
         return 1;
      } else if (r == 'V') {
         return 5;
      } else if (r == 'X') {
         return 10;
      } else if (r == 'L') {
         return 50;
      } else if (r == 'C') {
         return 100;
      } else if (r == 'D') {
         return 500;
      } else {
         return r == 'M' ? 1000 : 0;
      }
   }
}
