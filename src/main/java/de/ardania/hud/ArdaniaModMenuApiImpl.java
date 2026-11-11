package de.ardania.hud;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Integrations-Einstiegspunkt fuer ModMenu zur Bereitstellung des In-Game-Konfigurationsbildschirms.
 */
public final class ArdaniaModMenuApiImpl implements ModMenuApi {

    /**
     * Erstellt die ModMenu-API-Implementierung.
     */
    public ArdaniaModMenuApiImpl() {
    }

    /**
     * Liefert die Factory fuer den Konfigurationsbildschirm der Mod.
     *
     * @return Factory zur Erzeugung von {@link ArdaniaConfigScreen}
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ArdaniaConfigScreen::new;
    }
}
