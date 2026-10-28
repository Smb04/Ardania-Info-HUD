package de.ardania.hud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class ArdaniaI18n {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<ArdaniaConfig.HudLanguage, Map<String, String>> TRANSLATIONS = new EnumMap<>(ArdaniaConfig.HudLanguage.class);

    static {
        loadLanguage(ArdaniaConfig.HudLanguage.DE_DE, "de_de");
        loadLanguage(ArdaniaConfig.HudLanguage.EN_US, "en_us");
        loadLanguage(ArdaniaConfig.HudLanguage.ES_ES, "es_es");
        loadLanguage(ArdaniaConfig.HudLanguage.FR_FR, "fr_fr");
    }

    private ArdaniaI18n() {
    }

    private static void loadLanguage(ArdaniaConfig.HudLanguage lang, String code) {
        String path = "/assets/ardania_hud/lang/" + code + ".json";
        try (InputStream stream = ArdaniaI18n.class.getResourceAsStream(path)) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                    if (map != null) {
                        TRANSLATIONS.put(lang, Collections.unmodifiableMap(map));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        TRANSLATIONS.put(lang, Collections.emptyMap());
    }

    public static ArdaniaConfig.HudLanguage resolveLanguage(ArdaniaConfig.HudLanguage configured, String clientLangCode) {
        if (configured != null && configured != ArdaniaConfig.HudLanguage.AUTO) {
            return configured;
        }
        if (clientLangCode != null) {
            String lower = clientLangCode.toLowerCase(Locale.ROOT);
            if (lower.startsWith("de")) {
                return ArdaniaConfig.HudLanguage.DE_DE;
            }
            if (lower.startsWith("es")) {
                return ArdaniaConfig.HudLanguage.ES_ES;
            }
            if (lower.startsWith("fr")) {
                return ArdaniaConfig.HudLanguage.FR_FR;
            }
            if (lower.startsWith("en")) {
                return ArdaniaConfig.HudLanguage.EN_US;
            }
        }
        return ArdaniaConfig.HudLanguage.EN_US;
    }

    public static String getString(ArdaniaConfig.HudLanguage lang, String key) {
        ArdaniaConfig.HudLanguage effective = (lang == null || lang == ArdaniaConfig.HudLanguage.AUTO)
                ? ArdaniaConfig.HudLanguage.EN_US
                : lang;
        Map<String, String> map = TRANSLATIONS.getOrDefault(effective, Collections.emptyMap());
        String value = map.get(key);
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = TRANSLATIONS.getOrDefault(ArdaniaConfig.HudLanguage.EN_US, Collections.emptyMap());
        return fallback.getOrDefault(key, key);
    }

    public static MutableComponent translate(String key) {
        ArdaniaConfig.HudLanguage configured = ArdaniaConfig.getInstance().language;
        String clientLangCode = null;
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.options != null) {
                clientLangCode = client.options.languageCode;
            }
        } catch (Throwable ignored) {
        }

        if (configured == ArdaniaConfig.HudLanguage.AUTO && clientLangCode == null) {
            return Component.translatable(key);
        }

        ArdaniaConfig.HudLanguage resolved = resolveLanguage(configured, clientLangCode);
        return Component.literal(getString(resolved, key));
    }

    public static Map<String, String> getLanguageMap(ArdaniaConfig.HudLanguage lang) {
        return TRANSLATIONS.getOrDefault(lang, Collections.emptyMap());
    }
}
