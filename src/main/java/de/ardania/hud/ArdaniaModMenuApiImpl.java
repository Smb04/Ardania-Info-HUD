package de.ardania.hud;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu-Integration: Stellt den Config-Button im ModMenu bereit.
 */
public final class ArdaniaModMenuApiImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ArdaniaConfigScreen::new;
    }
}
