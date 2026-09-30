# Viktus Telas · app do stick

Fork de [nktnet1/webview-kiosk](https://github.com/nktnet1/webview-kiosk) (AGPL-3.0) com o perfil do
[Viktus Telas](https://telas.viktus.com.br) embutido. Instalar o APK é configurar: nada para importar na TV.

O que muda em relação ao original (branch `viktus`, a partir da tag `v0.26.19`):

- `applicationId` `br.com.viktus.telas.kiosk`, nome "Viktus Telas" (pacote próprio, sem conflito de assinatura com o da F-Droid).
- Defaults em `UserSettings.kt` = perfil do Telas (home `/player`, lock e reset on launch, sem histórico, refresh on network, tema escuro, tela cheia sem insets, toolbar e barra escondidas, autoplay sem gesto, tela sempre ligada).
- Manifesto com `LEANBACK_LAUNCHER`, banner 320×180 e `touchscreen`/`leanback` não obrigatórios: aparece na lista de apps da Android TV.
- Modo loja com PIN (viktus.9, 30/09/2026): destravar sempre pede senha (`unlockAuthRequirement = REQUIRE`) e a senha de fábrica é **0000** (`customAuthPassword`). Na loja: segurar Voltar → painel do quiosque → Destravar → 0000 → **Apps** → Configurações → Wi-Fi (viktus.10: o painel mostra só Atualizar, Apps, Travar e Destravar; o Home não sai do app porque ele é o launcher); o travamento (lock task) exige o app como device owner, que o `provisionar-stick.ps1` faz. A versão completa (Trocar Wi-Fi direto, vigia, PIN pelo painel) está parada na branch `viktus-manutencao`.
- Botão Wi-Fi sem PIN (viktus.11, 30/09/2026): o painel do quiosque ganha **Wi-Fi**, que abre só a tela de rede do Android mesmo travado. Como device owner, o app põe o pacote de Configurações na lista do lock task só enquanto essa tela está aberta e o tira no `onResume` (`utils/wifiUtils.kt`); o resto das Configurações (reset, desinstalar) continua atrás de Destravar + 0000.
- `release.yaml` gera `ViktusTelas_v<versão>.apk` no Release a cada tag `v*`. Keystore e senhas nos secrets do repo; cópia no cofre do parque (`Claude/secrets/viktus-telas-kiosk/`). **Perder a keystore = não atualizar o app nos sticks instalados.**

Atualizar a partir do original: `git fetch upstream && git rebase <tag-nova>` na branch `viktus`; os conflitos ficam nos ~25 linhas acima.
O que consome este APK: repo `viktus-telas`, `scripts/stick/` (kit do PC) e `/stick` (o próprio stick baixa).
