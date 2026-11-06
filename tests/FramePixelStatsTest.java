package com.winlator.xenvironment.components;

public final class FramePixelStatsTest {
    public static void main(String[] args) {
        check(FramePixelStats.classify(new int[]{0xff000000, 0xff020303})
                .equals("BLACK"), "black scene");
        check(FramePixelStats.classify(new int[]{0xff000000, 0xff0000ff})
                .equals("CONTENT"), "colored content");
        check(FramePixelStats.classify(new int[]{0, 0})
                .equals("TRANSPARENT"), "transparent frame");
        check(FramePixelStats.classify(new int[0]).equals("UNREADABLE"), "no pixels");
        System.out.println("PASS: stopped-producer GPU pixel classification distinguishes black, content and unreadable");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
