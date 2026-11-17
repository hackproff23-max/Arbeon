# Arbeon

Navegador simples para Android, feito com WebView.

- Tela inicial com busca do Google
- Guias no topo (abrir, trocar, fechar)
- Barra de endereço (aceita URL ou texto para pesquisar no Google)
- Favoritos (★), listados na tela inicial e no menu
- Configurações: JavaScript, modo desktop e limpar dados

## Compilar pelo GitHub

Todo `push` na branch `main` roda o workflow **Build APK**.
O APK fica em **Releases → Arbeon (último build)** ou em **Actions → Artifacts**.

## Enviar pelo Termux

```bash
pkg install git unzip
mkdir Arbeon && cd Arbeon
unzip ../arbeon-browser.zip
bash gitpush --install     # só na primeira vez
gitpush "primeiro commit"
```

Ao pedir senha, use um **Personal Access Token** do GitHub (permissão `repo`).
