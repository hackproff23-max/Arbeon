#!/data/data/com.termux/files/usr/bin/bash
# Liga o novo menu (estilo Chrome) ao MainActivity. Roda uma vez, na pasta do projeto.
F=app/src/main/java/com/arbeon/browser/MainActivity.java
[ -f "$F" ] || { echo "Rode dentro da pasta do projeto (onde fica a pasta app/)."; exit 1; }

if grep -q "showMenuV2" "$F"; then echo "Menu novo já está aplicado."; rm -f menu2.snippet; exit 0; fi
grep -q "DIALOG_THEME" "$F" || { echo "Aplique antes o Arbeon-visual.zip."; exit 1; }
[ -f menu2.snippet ] || { echo "Falta o arquivo menu2.snippet (extraia o zip na pasta do projeto)."; exit 1; }

cp "$F" "$HOME/MainActivity.java.bak3"

# 1) a chamada antiga do AppMenu vira showMenuV2()
sed -i '/AppMenu.show(this, new Runnable\[\]/,/DownloadsUi.show(this)});$/c\        showMenuV2();' "$F"
# 2) os métodos novos entram antes de sharePage()
awk '/^    private void sharePage\(\) \{$/ { while ((getline line < "menu2.snippet") > 0) print line; print "" } { print }' "$F" > "$F.new" && mv "$F.new" "$F"

if grep -q "showMenuV2();" "$F" && grep -q "private void showMenuV2" "$F" && grep -q "private void confirmClear" "$F"; then
  rm -f menu2.snippet
  echo "OK: menu novo aplicado."
else
  cp "$HOME/MainActivity.java.bak3" "$F"
  echo "ERRO: não achei os trechos esperados. MainActivity restaurado."
  exit 1
fi
