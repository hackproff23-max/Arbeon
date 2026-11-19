#!/data/data/com.termux/files/usr/bin/bash
# Quando o download já existe, a janela mostra "Atualizar link" e "Baixar novamente". Roda uma vez, na pasta do projeto.
F=app/src/main/java/com/arbeon/browser/DownloadsUi.java
[ -f "$F" ] || { echo "Rode dentro da pasta do projeto (onde fica a pasta app/)."; exit 1; }
[ -f app/src/main/java/com/arbeon/browser/DlConfirm.java ] || { echo "Falta o DlConfirm.java (extraia o zip na pasta do projeto)."; exit 1; }
if grep -q "DlConfirm.show(a, name, len, true, add" "$F"; then echo "Já está aplicado."; rm -f existe.snippet; exit 0; fi
[ -f existe.snippet ] || { echo "Falta o existe.snippet (extraia o zip na pasta do projeto)."; exit 1; }

cp "$F" "$HOME/DownloadsUi.java.bak2"
# troca o método onDownloadRequested inteiro (funciona com ou sem o patch anterior)
awk -v f=existe.snippet '
/^    static void onDownloadRequested\(/ { while ((getline line < f) > 0) print line; skip = 1; next }
skip && /^    }$/ { skip = 0; next }
skip { next }
{ print }' "$F" > "$F.new" && mv "$F.new" "$F"

if grep -q "DlConfirm.show(a, name, len, true, add" "$F" && grep -q "private static void askPermissions" "$F"; then
  rm -f existe.snippet baixar_confirma.patch
  echo "OK: janela de download existente aplicada."
else
  cp "$HOME/DownloadsUi.java.bak2" "$F"
  echo "ERRO: não achei o trecho esperado. DownloadsUi restaurado."
  exit 1
fi
