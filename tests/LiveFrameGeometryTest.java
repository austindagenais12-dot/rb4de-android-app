package com.winlator.xenvironment.components;

public final class LiveFrameGeometryTest {
    public static void main(String[] args) {
        portraitPhoneDoesNotBecomeGameBuffer();
        screenshotSizedPreviewDisplaysWholeGameFrame();
        widePreviewCentersGameFrame();
        System.out.println("PASS: RB4 live frame dimensions and aspect-fit viewport");
    }

    private static void portraitPhoneDoesNotBecomeGameBuffer() {
        LiveFrameGeometry.Layout layout = LiveFrameGeometry.fit(1440, 3120);
        require(layout.sourceWidth == 1280 && layout.sourceHeight == 720,
                "the phone screen must not set the game-image buffer size");
        require(layout.targetWidth == 1440 && layout.targetHeight == 810,
                "portrait viewport letterboxes a complete 16:9 image");
        require(layout.offsetX == 0 && layout.offsetY == 1155,
                "game image centers vertically within the full viewport");
    }

    private static void screenshotSizedPreviewDisplaysWholeGameFrame() {
        LiveFrameGeometry.Layout layout = LiveFrameGeometry.fit(620, 388);
        require(layout.sourceWidth == 1280 && layout.sourceHeight == 720,
                "rendering source is the guest 720p frame");
        require(layout.targetWidth == 620 && layout.targetHeight == 349,
                "the game occupies the entire preview width at its original ratio");
        require(layout.offsetX == 0 && layout.offsetY == 19,
                "the letterboxed game is centered, not confined to the top strip");
    }

    private static void widePreviewCentersGameFrame() {
        LiveFrameGeometry.Layout layout = LiveFrameGeometry.fit(2400, 1080);
        require(layout.targetWidth == 1920 && layout.targetHeight == 1080,
                "wide viewport fits the full height");
        require(layout.offsetX == 240 && layout.offsetY == 0,
                "wide viewport centers the frame horizontally");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
