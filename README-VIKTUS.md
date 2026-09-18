# Viktus Telas · app do stick

Fork de [nktnet1/webview-kiosk](https://github.com/nktnet1/webview-kiosk) (AGPL-3.0) com o perfil do
[Viktus Telas](https://telas.viktus.com.br) embutido. Instalar o APK é configurar: nada para importar na TV.

O que muda em relação ao original (branch `viktus`, a partir da tag `v0.26.19`):

- `applicationId` `br.com.viktus.telas.kiosk`, nome "Viktus Telas" (pacote próprio, sem conflito de assinatura com o da F-Droid).
- Defaults em `UserSettings.kt` = perfil do Telas (home `/player`, lock e reset on launch, sem histórico, refresh on network, tema escuro, tela cheia sem insets, toolbar e barra escondidas, autoplay sem gesto, tela sempre ligada).
- Manifesto com `LEANBACK_LAUNCHER`, banner 320×180 e `touchscreen`/`leanback` não obrigatórios: aparece na lista de apps da Android TV.
- `release.yaml` gera `ViktusTelas_v<versão>.apk` no Release a cada tag `v*`. Keystore e senhas nos secrets do repo; cópia no cofre do parque (`Claude/secrets/viktus-telas-kiosk/`). **Perder a keystore = não atualizar o app nos sticks instalados.**

Atualizar a partir do original: `git fetch upstream && git rebase <tag-nova>` na branch `viktus`; os conflitos ficam nos ~25 linhas acima.
O que consome este APK: repo `viktus-telas`, `scripts/stick/` (kit do PC) e `/stick` (o próprio stick baixa).
