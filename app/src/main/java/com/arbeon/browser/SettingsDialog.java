package com.arbeon.browser;

import android.app.Activity;

/**
 * Entrada das Configurações. Mantém a mesma assinatura de antes, então o MainActivity
 * não precisa mudar: a tela agora é cheia (veja SettingsUi) e lê/grava as opções
 * direto nas preferências "arbeon" (js, desktop).
 */
final class SettingsDialog {

    interface OnSave {
        void run(boolean js, boolean desktop);
    }

    private SettingsDialog() {
    }

    static void show(Activity a, boolean js, boolean desktop, OnSave save, Runnable clear) {
        SettingsUi.show(a, save, clear);
    }
}
