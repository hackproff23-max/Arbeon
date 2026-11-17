#!/data/data/com.termux/files/usr/bin/bash
# Liga o novo motor de downloads ao MainActivity (roda uma vez, na pasta do projeto).
F=app/src/main/java/com/arbeon/browser/MainActivity.java
[ -f "$F" ] || { echo "Rode dentro da pasta do projeto (onde fica a pasta app/)."; exit 1; }
if grep -q "DownloadsUi" "$F"; then echo "MainActivity já está com os downloads novos."; exit 0; fi

cp "$F" "$HOME/MainActivity.java.bak"

sed -i '/w.setDownloadListener/,/^        });$/c\        w.setDownloadListener((url, ua, cd, mime, len) -> DownloadsUi.onDownloadRequested(MainActivity.this, url, ua, cd, mime, len));' "$F"
sed -i '/m.getMenu().add(0, 4, 3, "Compartilhar página");/a\        m.getMenu().add(0, 5, 4, "Downloads");' "$F"
sed -i '0,/^                case 4:$/s//                case 5:\n                    DownloadsUi.show(MainActivity.this);\n                    break;\n                case 4:/' "$F"

if grep -q "DownloadsUi.onDownloadRequested" "$F" && grep -q "DownloadsUi.show" "$F" && grep -q 'add(0, 5, 4, "Downloads")' "$F"; then
  echo "OK: MainActivity ligado aos downloads novos."
else
  cp "$HOME/MainActivity.java.bak" "$F"
  echo "ERRO: não achei os trechos esperados. MainActivity restaurado."
  exit 1
fi
