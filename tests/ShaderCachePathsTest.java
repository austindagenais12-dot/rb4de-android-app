package dev.rb4dx.standalone;

import java.nio.file.Path;

public final class ShaderCachePathsTest {
    public static void main(String[] args) {
        Path root = Path.of("/data/user/0/rb4dx/files/shadps4/user");
        Path cache = ShaderCachePaths.forTitle(root, "CUSA02084");
        require(cache.equals(root.resolve("shader-cache/mesa/CUSA02084")),
                "unexpected persistent per-game cache directory: " + cache);
        require(ShaderCachePaths.forTitle(root, "../../bad") == null,
                "title traversal must not escape app storage");
        require(ShaderCachePaths.forTitle(root, "") == null,
                "empty title must not allocate a shared cache");
        System.out.println("PASS: per-title Mesa shader cache is persistent and traversal-safe");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
