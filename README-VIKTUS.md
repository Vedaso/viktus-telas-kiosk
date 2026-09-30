# Viktus Telas · app do stick

Fork de [nktnet1/webview-kiosk](https://github.com/nktnet1/webview-kiosk) (AGPL-3.0) com o perfil do
[Viktus Telas](https://telas.viktus.com.br) embutido. Instalar o APK é configurar: nada para importar na TV.

O que muda em relação ao original (branch `viktus`, a partir da tag `v0.26.19`):

- `applicationId` `br.com.viktus.telas.kiosk`, nome "Viktus Telas" (pacote próprio, sem conflito de assinatura com o da F-Droid).
- Defaults em `UserSettings.kt` = perfil do Telas (home `/player`, lock e reset on launch, sem histórico, refresh on network, tema escuro, tela cheia sem insets, toolbar e barra escondidas, autoplay sem gesto, tela sempre ligada).
- Manifesto com `LEANBACK_LAUNCHER`, banner 320×180 e `touchscreen`/`leanback` não obrigatórios: aparece na lista de apps da Android TV.
- Modo loja com PIN (viktus.9, 30/09/2026): destravar sempre pede senha (`unlockAuthRequirement = REQUIRE`) e a senha de fábrica é **0000** (`customAuthPassword`). Na loja: segurar Voltar → painel do quiosque → Destravar → 0000; o travamento (lock task) exige o app como device owner, que o `provisionar-stick.ps1` faz. A versão completa (Trocar Wi-Fi direto, vigia, PIN pelo painel) está parada na branch `viktus-manutencao`.
- `release.yaml` gera `ViktusTelas_v<versão>.apk` no Release a cada tag `v*`. Keystore e senhas nos secrets do repo; cópia no cofre do parque (`Claude/secrets/viktus-telas-kiosk/`). **Perder a keystore = não atualizar o app nos sticks instalados.**

Atualizar a partir do original: `git fetch upstream && git rebase <tag-nova>` na branch `viktus`; os conflitos ficam nos ~25 linhas acima.
O que consome este APK: repo `viktus-telas`, `scripts/stick/` (kit do PC) e `/stick` (o próprio stick baixa).
