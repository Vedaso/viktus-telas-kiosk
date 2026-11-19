# Viktus Telas · app do stick

Fork de [nktnet1/webview-kiosk](https://github.com/nktnet1/webview-kiosk) (AGPL-3.0) com o perfil do
[Viktus Telas](https://telas.viktus.com.br) embutido. Instalar o APK é configurar: nada para importar na TV.

O que muda em relação ao original (branch `viktus`, a partir da tag `v0.26.19`):

- `applicationId` `br.com.viktus.telas.kiosk`, nome "Viktus Telas" (pacote próprio, sem conflito de assinatura com o da F-Droid).
- Defaults em `UserSettings.kt` = perfil do Telas (home `/player`, lock e reset on launch, sem histórico, refresh on network, tema escuro, tela cheia sem insets, toolbar e barra escondidas, autoplay sem gesto, tela sempre ligada).
- Manifesto com `LEANBACK_LAUNCHER`, banner 320×180 e `touchscreen`/`leanback` não obrigatórios: aparece na lista de apps da Android TV.
- Modo loja com PIN (viktus.9, 30/09/2026): destravar sempre pede senha (`unlockAuthRequirement = REQUIRE`) e a senha de fábrica é **0000** (`customAuthPassword`). Na loja: segurar Voltar → painel do quiosque → Destravar → 0000 → **Apps** → Configurações → Wi-Fi (viktus.10: o painel mostra só Atualizar, Apps, Travar e Destravar; o Home não sai do app porque ele é o launcher); o travamento (lock task) exige o app como device owner, que o `provisionar-stick.ps1` faz. A versão completa (Trocar Wi-Fi direto, vigia, PIN pelo painel) está parada na branch `viktus-manutencao`.
- Botão Wi-Fi sem PIN (viktus.11, 30/09/2026): o painel do quiosque ganha **Wi-Fi**, que abre só a tela de rede do Android mesmo travado. Como device owner, o app põe o pacote de Configurações na lista do lock task só enquanto essa tela está aberta e o tira no `onResume` (`utils/wifiUtils.kt`); o resto das Configurações (reset, desinstalar) continua atrás de Destravar + 0000.
- `scripts/publicar.sh` gera `ViktusTelas_v<versão>.apk` assinado e abre o Release (rascunho) da tag `v<versão>`; substituiu o `release.yaml` em 19/11/2026 (ver "Sem GitHub Actions" abaixo). Keystore e senhas no cofre do parque (`Claude/secrets/viktus-telas-kiosk/`; os secrets do repo no GitHub ficam sem uso). **Perder a keystore = não atualizar o app nos sticks instalados.**

Atualizar a partir do original: `git fetch upstream && git rebase <tag-nova>` na branch `viktus`; os conflitos ficam nos ~25 linhas acima.
O que consome este APK: repo `viktus-telas`, `scripts/stick/` (kit do PC) e `/stick` (o próprio stick baixa).

## Sem GitHub Actions (desde 19/11/2026)

Decisão do Victor (19/11/2026): "remove isso do github testes locais e deploy pela maquina". A cobrança
do GitHub travou em 18/11 e todo job falhava em segundos. O Actions está **desativado** neste fork e os
dois workflows estão em `.github/workflows-desligados/` (reversível: mover de volta e reativar o Actions).

| Workflow antigo | Destino |
|---|---|
| `release.yaml` (APK assinado + Release rascunho a cada tag `v*`) | **Mantido, agora local:** `bash scripts/publicar.sh` com HEAD na tag `v<versionName>`. Usa `Claude/secrets/viktus-telas-kiosk/` (keystore.env e release.jks), empurra a tag, builda e abre o Release rascunho. Precisa de JDK, Android SDK (plataforma 37) e `gh` logado |
| `docs.yaml` (site de docs do upstream no GitHub Pages) | **Desligado, sem script:** herança do `nktnet1/webview-kiosk`. Só disparava em push na `main` (o fork trabalha na `viktus`, Pages nunca foi ligado) e nunca rodou neste fork. Para ler a doc do upstream: `docs/` ou o site dele |

Não há `verificar.sh`: nenhum dos dois workflows rodava teste ou lint do app.
