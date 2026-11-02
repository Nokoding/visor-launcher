package com.nokoding.deck;

/** Writes the app's own built-in 3D controller models as JSON for the website (site/models.json). */
public final class DumpModel {
    public static void main(String[] a) {
        StringBuilder b = new StringBuilder("{");
        int[] types = {PadTest.PLAYSTATION, PadTest.XBOX};
        String[] names = {"dualsense", "xbox"};
        for (int t = 0; t < 2; t++) {
            ControllerModel m = new ControllerModel(types[t]);
            if (t > 0) b.append(',');
            b.append('"').append(names[t]).append("\":{\"v\":[");
            for (int i = 0; i < m.verts.length; i++) b.append(i > 0 ? "," : "").append(String.format(java.util.Locale.ROOT, "%.2f", m.verts[i]));
            b.append("],\"f\":[");
            for (int i = 0; i < m.faces.length; i++) {
                if (i > 0) b.append(',');
                b.append('[');
                for (int k = 0; k < m.faces[i].length; k++) b.append(k > 0 ? "," : "").append(m.faces[i][k]);
                b.append(']');
            }
            b.append("],\"g\":[");
            for (int i = 0; i < m.group.length; i++) b.append(i > 0 ? "," : "").append(m.group[i]);
            b.append("],\"b\":[");
            for (int i = 0; i < m.button.length; i++) b.append(i > 0 ? "," : "").append(m.button[i]);
            b.append("]}");
        }
        System.out.println(b.append('}'));
    }
}
