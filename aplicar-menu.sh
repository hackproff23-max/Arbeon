#!/data/data/com.termux/files/usr/bin/bash
# Liga o novo menu (estilo GBA Emu) e o ícone de download na barra. Roda uma vez, na pasta do projeto.
D=app/src/main/java/com/arbeon/browser
F=$D/MainActivity.java
[ -f "$F" ] || { echo "Rode dentro da pasta do projeto (onde fica a pasta app/)."; exit 1; }
[ -f "$D/DownloadsUi.java" ] || { echo "Faltam os downloads novos. Aplique antes o Arbeon-downloads.zip."; exit 1; }
if grep -q "AppMenu.show" "$F"; then echo "Menu novo já está aplicado."; exit 0; fi

cp "$F" "$HOME/MainActivity.java.bak2"

sed -i '/^    private void showMenu(View anchor) {$/a\        AppMenu.show(this, new Runnable[]{() -> newTab(null), this::showBookmarks, this::showSettings, this::sharePage, () -> DownloadsUi.show(this)});\n        if (true) return;' "$F"
sed -i '/bar.addView(btnStar, new LinearLayout.LayoutParams(dp(40), dp(44)));/a\        bar.addView(new DownloadIconView(this, v -> DownloadsUi.show(this)), new LinearLayout.LayoutParams(dp(36), dp(44)));' "$F"

if grep -q "AppMenu.show" "$F" && grep -q "new DownloadIconView" "$F"; then
  echo "OK: menu novo e ícone de download aplicados."
else
  cp "$HOME/MainActivity.java.bak2" "$F"
  echo "ERRO: não achei os trechos esperados. MainActivity restaurado."
  exit 1
fi
