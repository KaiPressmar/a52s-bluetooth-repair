package de.kaipressmar.a52srepair.ui.common;

import de.kaipressmar.a52srepair.update.UpdateRelease;

/** Actions the screens delegate to the hosting activity (dialogs and activity results). */
public interface SetupActions {
    void requestBluetoothPermission();

    void startCarLink();

    void showAdbInstructions();

    UpdateRelease availableUpdate();

    void installUpdate();

    void checkForUpdates();

    void refreshStatus();
}
