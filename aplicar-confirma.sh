#!/data/data/com.termux/files/usr/bin/bash
# Coloca a janela "Baixar o arquivo?" (DlConfirm) antes de qualquer download. Roda uma vez, na pasta do projeto.
F=app/src/main/java/com/arbeon/browser/DownloadsUi.java
[ -f "$F" ] || { echo "Rode dentro da pasta do projeto (onde fica a pasta app/)."; exit 1; }
[ -f app/src/main/java/com/arbeon/browser/DlConfirm.java ] || { echo "Falta o DlConfirm.java (extraia o zip na pasta do projeto)."; exit 1; }
if grep -q "DlConfirm.show" "$F"; then echo "A confirmação já está aplicada."; exit 0; fi
[ -f baixar_confirma.patch ] || { echo "Falta o baixar_confirma.patch (extraia o zip na pasta do projeto)."; exit 1; }

cp "$F" "$HOME/DownloadsUi.java.bak"
if git apply --recount baixar_confirma.patch; then
  rm -f baixar_confirma.patch
  echo "OK: confirmação de download aplicada."
else
  cp "$HOME/DownloadsUi.java.bak" "$F"
  echo "ERRO: o patch não encaixou (o DownloadsUi mudou?). Arquivo restaurado."
  exit 1
fi
