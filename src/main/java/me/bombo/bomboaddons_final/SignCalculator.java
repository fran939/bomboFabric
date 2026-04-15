package me.bombo.bomboaddons_final;

public class SignCalculator {
   public static boolean isPotentialExpression(String text) {
      if (text != null && !text.isEmpty()) {
         return text.matches(".*[0-9].*") && text.toLowerCase().matches(".*[+\\-*/xkmbes].*");
      } else {
         return false;
      }
   }

   public static boolean isValidExpression(String text) {
      if (!isPotentialExpression(text)) {
         return false;
      } else {
         try {
            String expression = preprocess(text);
            eval(expression);
            return true;
         } catch (Exception var2) {
            return false;
         }
      }
   }

   private static String preprocess(String text) {
      String expression = text.replace(" ", "").toLowerCase().replace("x", "*");
      expression = expression.replaceAll("([0-9.]+)k", "($1*1000)");
      expression = expression.replaceAll("([0-9.]+)m", "($1*1000000)");
      expression = expression.replaceAll("([0-9.]+)b", "($1*1000000000)");
      expression = expression.replaceAll("([0-9.]+)e", "($1*160)");
      expression = expression.replaceAll("([0-9.]+)s", "($1*64)");
      return expression;
   }

   public static String calculate(String text) {
      if (!isValidExpression(text)) {
         return text;
      } else {
         try {
            String expression = preprocess(text);
            double result = eval(expression);
            return result == (double) ((long) result) ? String.valueOf((long) result) : String.valueOf(result);
         } catch (Exception var4) {
            return text;
         }
      }
   }

   public static String getPreviewText(String text) {
      if (!isValidExpression(text)) {
         return isPotentialExpression(text) ? text + " = ?" : text;
      } else {
         try {
            String expression = preprocess(text);
            double result = eval(expression);
            String formatted = formatResultOnly(result);
            if (text.contains("*")) {
               String var10000 = text.replace("*", "x");
               return var10000 + " = " + formatted;
            } else {
               return text.toLowerCase().matches(".*[kmbes].*") ? text + " = " + formatted : text + " = " + formatted;
            }
         } catch (Exception var5) {
            return text + " = ?";
         }
      }
   }

   public static String formatResultOnly(double result) {
      return result == (double) ((long) result) ? String.format("%,d", (long) result) : String.format("%,.2f", result);
   }

   public static double getResult(String text) {
      if (!isValidExpression(text)) {
         return 0.0D;
      } else {
         try {
            String expression = preprocess(text);
            return eval(expression);
         } catch (Exception var2) {
            return 0.0D;
         }
      }
   }

   public static double eval(String str) {
      return (new Object() {
         int pos = -1;
         int ch;

         void nextChar() {
            this.ch = ++this.pos < str.length() ? str.charAt(this.pos) : -1;
         }

         boolean eat(int charToEat) {
            while(this.ch == 32) {
               this.nextChar();
            }

            if (this.ch == charToEat) {
               this.nextChar();
               return true;
            } else {
               return false;
            }
         }

         double parse() {
            this.nextChar();
            double x = this.parseExpression();
            if (this.pos < str.length()) {
               throw new RuntimeException("Unexpected: " + (char)this.ch);
            } else {
               return x;
            }
         }

         double parseExpression() {
            double x = this.parseTerm();

            while(true) {
               while(!this.eat(43)) {
                  if (!this.eat(45)) {
                     return x;
                  }

                  x -= this.parseTerm();
               }

               x += this.parseTerm();
            }
         }

         double parseTerm() {
            double x = this.parseFactor();

            while(true) {
               while(!this.eat(42)) {
                  if (!this.eat(47)) {
                     return x;
                  }

                  x /= this.parseFactor();
               }

               x *= this.parseFactor();
            }
         }

         double parseFactor() {
            if (this.eat(43)) {
               return this.parseFactor();
            } else if (this.eat(45)) {
               return -this.parseFactor();
            } else {
               int startPos = this.pos;
               double x;
               if (this.eat(40)) {
                  x = this.parseExpression();
                  this.eat(41);
               } else {
                  if ((this.ch < 48 || this.ch > 57) && this.ch != 46) {
                     throw new RuntimeException("Unexpected: " + (char)this.ch);
                  }

                  while(this.ch >= 48 && this.ch <= 57 || this.ch == 46) {
                     this.nextChar();
                  }

                  x = Double.parseDouble(str.substring(startPos, this.pos));
               }

               return x;
            }
         }
      }).parse();
   }
}
