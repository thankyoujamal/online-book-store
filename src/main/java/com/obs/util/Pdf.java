package com.obs.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal PDF writer using only the JDK: A4 pages with text in the built-in Helvetica
 * fonts, lines and filled boxes. Enough for an invoice without adding a library.
 * Positions are in points (1/72 inch) and y is measured from the top of the page.
 * Text is Windows-1252 encoded, so characters outside it print as '?'.
 */
public final class Pdf {

    public static final float WIDTH = 595.28f;
    public static final float HEIGHT = 841.89f;

    private static final Charset ENCODING = Charset.forName("windows-1252");

    /** Glyph widths (per 1000 units of font size) for codes 32–255 in WinAnsiEncoding. */
    private static final int[] REGULAR = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 350,
            556, 350, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 350, 611, 350,
            350, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 350, 500, 667,
            278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611,
            667, 667, 667, 667, 667, 667, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
            722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
            556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500,
    };
    private static final int[] BOLD = {
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
            975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
            333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
            611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 350,
            556, 350, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 350, 611, 350,
            350, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 350, 500, 667,
            278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611,
            722, 722, 722, 722, 722, 722, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
            722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
            556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278,
            611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556,
    };

    private final String title;
    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;

    public Pdf(String title) {
        this.title = title;
        newPage();
    }

    public void newPage() {
        page = new StringBuilder();
        pages.add(page);
    }

    public int pageCount() {
        return pages.size();
    }

    public void text(float x, float y, String s, float size, boolean bold, int rgb) {
        page.append("BT /").append(bold ? "F2 " : "F1 ").append(num(size)).append(" Tf ")
                .append(color(rgb)).append(" rg ")
                .append(num(x)).append(' ').append(num(HEIGHT - y)).append(" Td (")
                .append(escape(s)).append(") Tj ET\n");
    }

    public void textRight(float right, float y, String s, float size, boolean bold, int rgb) {
        text(right - width(s, size, bold), y, s, size, bold, rgb);
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int rgb) {
        page.append(color(rgb)).append(" RG ").append(num(thickness)).append(" w ")
                .append(num(x1)).append(' ').append(num(HEIGHT - y1)).append(" m ")
                .append(num(x2)).append(' ').append(num(HEIGHT - y2)).append(" l S\n");
    }

    /** Filled rectangle whose top-left corner is (x, y). */
    public void box(float x, float y, float w, float h, int rgb) {
        page.append(color(rgb)).append(" rg ")
                .append(num(x)).append(' ').append(num(HEIGHT - y - h)).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    public static float width(String s, float size, boolean bold) {
        int[] widths = bold ? BOLD : REGULAR;
        int units = 0;
        for (byte b : clean(s).getBytes(ENCODING)) {
            int code = b & 0xff;
            units += code >= 32 ? widths[code - 32] : 0;
        }
        return units * size / 1000f;
    }

    /** Shortens text with "..." so it fits in maxWidth. */
    public static String fit(String s, float maxWidth, float size, boolean bold) {
        if (width(s, size, bold) <= maxWidth) {
            return s;
        }
        String cut = s;
        while (!cut.isEmpty() && width(cut + "...", size, bold) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "...";
    }

    /** Breaks text into lines no wider than maxWidth, splitting at spaces. */
    public static List<String> wrap(String s, float maxWidth, float size, boolean bold) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : clean(s).split(" +")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && width(candidate, size, bold) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(fit(word, maxWidth, size, bold));
            } else {
                current = new StringBuilder(width(candidate, size, bold) > maxWidth
                        ? fit(candidate, maxWidth, size, bold) : candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public byte[] toBytes() {
        // Objects: 1 catalog, 2 page tree, 3–4 fonts, 5 info, then a page and its content per page.
        List<byte[]> objects = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(6 + i * 2).append(" 0 R ");
        }
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(ascii("<< /Type /Pages /Count " + pages.size() + " /Kids [" + kids + "] >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        objects.add(("<< /Title (" + escape(title) + ") /Producer (Online Book Store) >>").getBytes(ENCODING));
        for (int i = 0; i < pages.size(); i++) {
            byte[] content = pages.get(i).toString().getBytes(ENCODING);
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(WIDTH) + " " + num(HEIGHT) + "]"
                    + " /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (7 + i * 2) + " 0 R >>"));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.writeBytes(ascii("<< /Length " + content.length + " >>\nstream\n"));
            stream.writeBytes(content);
            stream.writeBytes(ascii("\nendstream"));
            objects.add(stream.toByteArray());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("%PDF-1.4\n"));
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            out.writeBytes(ascii((i + 1) + " 0 obj\n"));
            out.writeBytes(objects.get(i));
            out.writeBytes(ascii("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R /Info 5 0 R >>\n")
                .append("startxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(ascii(table.toString()));
        return out.toByteArray();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ");
    }

    private static String escape(String s) {
        return clean(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String color(int rgb) {
        return num(((rgb >> 16) & 0xff) / 255f) + " " + num(((rgb >> 8) & 0xff) / 255f) + " " + num((rgb & 0xff) / 255f);
    }

    private static String num(float f) {
        return String.format(Locale.ROOT, "%.2f", f);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
