package io.interrupt21h.ch347studio.protocol;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class Hex {
    private Hex() {}

    public static byte[] parse(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return new byte[0];
        String[] tokens = trimmed.replace(',', ' ').replace(':',' ').split("\\s+");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String token : tokens) {
            if (token.startsWith("0x") || token.startsWith("0X")) token = token.substring(2);
            if ((token.length() & 1) != 0 || token.isEmpty())
                throw new IllegalArgumentException("Hex bytes require pairs of digits (e.g. 00 FF 2A)");
            for (int i = 0; i < token.length(); i += 2) {
                int high = Character.digit(token.charAt(i), 16);
                int low = Character.digit(token.charAt(i + 1), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hex byte: " + token);
                out.write((high << 4) | low);
            }
        }
        return out.toByteArray();
    }

    public static String format(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) {
            if (s.length() != 0) s.append(' ');
            s.append(String.format(Locale.US, "%02X", b & 0xff));
        }
        return s.toString();
    }
}
