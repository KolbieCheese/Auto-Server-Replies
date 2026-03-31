package com.beautyinblocks.snarkyserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class SnarkExternalOutputManifestLoader {
    static final String MANIFEST_PATH = "META-INF/snarky-outputs.json";

    private final Logger logger;

    public SnarkExternalOutputManifestLoader(Logger logger) {
        this.logger = logger;
    }

    public Optional<SnarkExternalOutputManifest> load(Plugin plugin) {
        try (InputStream inputStream = plugin.getResource(MANIFEST_PATH)) {
            if (inputStream == null) {
                return Optional.empty();
            }

            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (!rootElement.isJsonObject()) {
                    warn(plugin, "Manifest root must be a JSON object.");
                    return Optional.empty();
                }

                JsonObject rootObject = rootElement.getAsJsonObject();
                int version = intValue(rootObject.get("version"), -1);
                if (version != SnarkExternalOutputManifest.SUPPORTED_VERSION) {
                    warn(plugin, "Unsupported manifest version '" + version + "'.");
                    return Optional.empty();
                }

                String declaredPluginName = stringValue(rootObject.get("plugin"));
                if (!declaredPluginName.isBlank() && !plugin.getName().equals(declaredPluginName)) {
                    warn(plugin, "Manifest declared plugin '" + declaredPluginName
                            + "', but runtime plugin name is '" + plugin.getName() + "'. Using runtime name.");
                }

                JsonElement outputsElement = rootObject.get("outputs");
                if (!(outputsElement instanceof JsonArray outputsArray)) {
                    warn(plugin, "Manifest is missing an outputs array.");
                    return Optional.empty();
                }

                List<SnarkExternalOutput> outputs = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();
                for (int index = 0; index < outputsArray.size(); index++) {
                    JsonElement outputElement = outputsArray.get(index);
                    if (!outputElement.isJsonObject()) {
                        warn(plugin, "Skipping output entry " + index + " because it is not a JSON object.");
                        continue;
                    }

                    JsonObject outputObject = outputElement.getAsJsonObject();
                    String id = stringValue(outputObject.get("id"));
                    String displayName = stringValue(outputObject.get("displayName"));
                    String eventClass = stringValue(outputObject.get("eventClass"));
                    String kind = stringValue(outputObject.get("kind"));
                    String description = stringValue(outputObject.get("description"));

                    if (id.isBlank() || displayName.isBlank() || eventClass.isBlank() || kind.isBlank()) {
                        warn(plugin, "Skipping output entry " + index + " because required fields are missing.");
                        continue;
                    }

                    if (!seenIds.add(id)) {
                        warn(plugin, "Skipping duplicate manifest output id '" + id + "'.");
                        continue;
                    }

                    if (!"chat".equalsIgnoreCase(kind)) {
                        warn(plugin, "Skipping output '" + id + "' because kind '" + kind + "' is not supported.");
                        continue;
                    }

                    try {
                        outputs.add(new SnarkExternalOutput(
                                id,
                                displayName,
                                plugin.getName(),
                                eventClass,
                                kind,
                                description
                        ));
                    } catch (IllegalArgumentException exception) {
                        warn(plugin, "Skipping output entry " + index + " because " + exception.getMessage());
                    }
                }

                return Optional.of(new SnarkExternalOutputManifest(plugin.getName(), version, outputs));
            } catch (JsonParseException exception) {
                warn(plugin, "Failed to parse manifest JSON: " + exception.getMessage());
                return Optional.empty();
            }
        } catch (IOException exception) {
            warn(plugin, "Failed to read manifest: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private int intValue(JsonElement element, int fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String stringValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString().trim();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private void warn(Plugin plugin, String message) {
        logger.warning("Snarky external output manifest for plugin '" + plugin.getName() + "': " + message);
    }
}
