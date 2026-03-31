package com.beautyinblocks.snarkyserver;

import java.util.List;

public record SnarkExternalOutputManifest(
        String plugin,
        int version,
        List<SnarkExternalOutput> outputs
) {
    public static final int SUPPORTED_VERSION = 1;

    public SnarkExternalOutputManifest {
        plugin = plugin == null ? "" : plugin.trim();
        outputs = List.copyOf(outputs);
    }
}
