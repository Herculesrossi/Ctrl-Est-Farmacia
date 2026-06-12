package farmacia.util;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class Masks {

    private Masks() {}

    public static void installDateMask(JTextField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DateMaskFilter(field));
    }

    public static void installMoneyMask(JTextField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new MoneyMaskFilter(field));
    }

    private static final class DateMaskFilter extends DocumentFilter {
        private final JTextField field;

        private DateMaskFilter(JTextField field) {
            this.field = field;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);

            int digitsBeforeCaret = countDigits(next.substring(0, Math.min(next.length(), offset + (text == null ? 0 : text.length()))));

            String digits = onlyDigits(next);
            if (digits.length() > 8) digits = digits.substring(0, 8);

            String formatted = formatDateDigits(digits);
            fb.replace(0, fb.getDocument().getLength(), formatted, attrs);

            int caret = caretPosForDateDigits(formatted, digitsBeforeCaret);
            SwingUtilities.invokeLater(() -> field.setCaretPosition(Math.min(caret, field.getText().length())));
        }

        private static int countDigits(String s) {
            int c = 0;
            for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) c++;
            return c;
        }

        private static String onlyDigits(String s) {
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (Character.isDigit(ch)) out.append(ch);
            }
            return out.toString();
        }

        private static String formatDateDigits(String digits) {
            StringBuilder out = new StringBuilder(10);
            for (int i = 0; i < digits.length(); i++) {
                if (i == 2 || i == 4) out.append('/');
                out.append(digits.charAt(i));
            }
            return out.toString();
        }

        private static int caretPosForDateDigits(String formatted, int digitsBeforeCaret) {
            int digitsSeen = 0;
            for (int i = 0; i < formatted.length(); i++) {
                if (Character.isDigit(formatted.charAt(i))) digitsSeen++;
                if (digitsSeen >= digitsBeforeCaret) return i + 1;
            }
            return formatted.length();
        }
    }

    private static final class MoneyMaskFilter extends DocumentFilter {
        private final JTextField field;

        private MoneyMaskFilter(JTextField field) {
            this.field = field;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);

            int digitsBeforeCaret = countDigits(next.substring(0, Math.min(next.length(), offset + (text == null ? 0 : text.length()))));

            String digits = onlyDigits(next);
            if (digits.length() > 14) digits = digits.substring(0, 14);

            String formatted = formatMoneyDigits(digits);
            fb.replace(0, fb.getDocument().getLength(), formatted, attrs);

            int caret = caretPosForMoneyDigits(formatted, digitsBeforeCaret);
            SwingUtilities.invokeLater(() -> field.setCaretPosition(Math.min(caret, field.getText().length())));
        }

        private static int countDigits(String s) {
            int c = 0;
            for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) c++;
            return c;
        }

        private static String onlyDigits(String s) {
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (Character.isDigit(ch)) out.append(ch);
            }
            return out.toString();
        }

        private static String formatMoneyDigits(String digits) {
            if (digits.isEmpty()) return "";
            if (digits.length() <= 2) return digits;
            int split = digits.length() - 2;
            String intPart = digits.substring(0, split);
            String fracPart = digits.substring(split);
            return groupThousands(intPart) + "," + fracPart;
        }

        private static String groupThousands(String intPart) {
            intPart = stripLeadingZeros(intPart);
            if (intPart.isEmpty()) return "0";
            if (intPart.length() <= 3) return intPart;

            StringBuilder out = new StringBuilder(intPart.length() + (intPart.length() / 3));
            int len = intPart.length();
            int firstGroupLen = len % 3;
            if (firstGroupLen == 0) firstGroupLen = 3;

            out.append(intPart, 0, firstGroupLen);
            for (int i = firstGroupLen; i < len; i += 3) {
                out.append('.').append(intPart, i, i + 3);
            }
            return out.toString();
        }

        private static String stripLeadingZeros(String s) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == '0') i++;
            return s.substring(i);
        }

        private static int caretPosForMoneyDigits(String formatted, int digitsBeforeCaret) {
            int digitsSeen = 0;
            for (int i = 0; i < formatted.length(); i++) {
                if (Character.isDigit(formatted.charAt(i))) digitsSeen++;
                if (digitsSeen >= digitsBeforeCaret) return i + 1;
            }
            return formatted.length();
        }
    }
}
