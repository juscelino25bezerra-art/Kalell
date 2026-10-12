# Kalendrium — Android nativo (Kotlin + Jetpack Compose)

Migração completa do PWA **Minha Escala v4.11** (agora **Kalendrium**) para um app Android nativo, mantendo layout, regras e formato de dados.

## Abrir e gerar o APK

1. Abra a pasta `Kalendrium` no **Android Studio** (Narwhal 2025.1.3 ou mais novo, por causa do AGP 8.13).
2. Aguarde o *Gradle Sync* (baixa Gradle 8.13, AGP 8.13.0, Kotlin 2.1.0, Compose BOM 2024.12.01). Precisa do JDK 17 (o do Android Studio serve).
3. Rode no aparelho/emulador (▶) ou gere o APK em *Build → Build App Bundle(s) / APK(s)*.
4. Para testar rápido use `./gradlew assembleDebug`. O release (`assembleRelease`) passa pelo R8 e é bem mais demorado, principalmente em máquinas com pouca memória.
5. Testes da lógica e do controlador: `./gradlew test` (86 testes, incluindo os do `tests/run.js` do PWA e os de endurecimento da auditoria 5.3).
6. Testes no aparelho/emulador: `./gradlew connectedAndroidTest` (gravação atômica dos arquivos e interface).

`applicationId` = `com.kalendrium`, versão 5.3 (`versionCode 6`), `minSdk 24`, `compileSdk`/`targetSdk 36` (exigido pela Play Store para apps novos e atualizações desde 31/08/2026).
Para instalar por cima do APK antigo, assine com a **mesma keystore**; com outra chave o Android pede para desinstalar antes
(exporte um backup no app antigo e importe no novo).

## Assinatura do release

1. Crie a chave uma vez (guarde o arquivo e as senhas fora do projeto e com backup: sem ela não dá para atualizar o app):
   `keytool -genkeypair -v -keystore kalendrium-upload.jks -alias kalendrium -keyalg RSA -keysize 4096 -validity 10000`
2. Copie `keystore.properties.example` para `keystore.properties` e preencha (o arquivo e os `.jks` estão no `.gitignore`).
   Também dá para usar as variáveis `KALENDRIUM_STORE_FILE`, `KALENDRIUM_STORE_PASSWORD`, `KALENDRIUM_KEY_ALIAS` e `KALENDRIUM_KEY_PASSWORD`.
3. `./gradlew assembleRelease` (APK) ou `bundleRelease` (AAB para a Play Store) já saem assinados. Confira com
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`.
4. Na Play Store, ative o *Play App Signing*: a chave acima vira a chave de upload.

Sem `keystore.properties`, o release continua saindo sem assinatura, como antes.

## Play Console

- **Alarme exato:** o app declara `USE_EXACT_ALARM` para os lembretes tocarem no horário. Na Play Console, em
  *Conteúdo do app › Permissões de alarme exato*, informe que é um app de calendário com lembretes definidos pelo usuário.
- **Segurança dos dados:** nenhum dado é coletado nem compartilhado (sem internet, sem analytics). O backup automático do
  Android só envia dados com criptografia de ponta a ponta.

## Visual (redesign)

- Fonte **Manrope** embutida em `res/font` (licença SIL OFL em `docs/MANROPE-OFL.txt`), aplicada em todo o app pelo `AppTheme`.
- Card de hoje na cor primária: situação do dia em destaque, resumo abaixo e a barrinha com o dia atual dentro do bloco (some em férias/licenças e em blocos com mais de 14 dias).
- Topo só com o título, visualização e ajustes; Pessoal | Work na largura toda e o card de hoje rolam junto com o calendário.
- Tela levemente tingida com cartões claros por cima (no escuro, o contrário), como no Material 3.
- Chips de escala em pílula (a selecionada ganha ✓), botão "Nova escala" tracejado, calendário com mês à esquerda e setas à direita.
- Dias do mês só com o número e os pontos das outras escalas: trabalho/folga pela cor, hoje com o dia inteiro na cor primária. O bloco e a situação completa aparecem ao tocar no dia (visualização mensal + diária) e no leitor de tela.
- Barra inferior com indicador em pílula.
- **Liquid Glass**: vidro fosco simulado (`glassSurface` em `Components.kt`): preenchimento translúcido, brilho no topo e borda clara, sobre manchas de cor em gradiente radial. Não usa desfoque real, então fica igual do Android 7 em diante.
- Só a camada visual mudou: lógica, dados, textos e ações continuam os mesmos, e as 10 paletas e o modo escuro continuam valendo.

## Estrutura

```
app/src/main/java/com/kalendrium/
  domain/   Kotlin puro (sem Android): motor do ciclo, validação, backup
    Engine.kt     status do dia, posições, mudanças, eras preservadas, remapChanges
    Sanitize.kt   cleanX/validX/sanitize portados 1:1 do app.js
    Codec.kt      JSON no formato do PWA (localStorage e backup version 3)
    I18n.kt       idiomas (pt, en, es): L("pt", "en", "es"), datas e rótulos por idioma
  ui/       Compose: telas, folhas, tema e desenhos
    AppController.kt  estado + handlers do app.js (salvar, confirmar, importar…)
    MainScreen.kt     topo com resumo de hoje, chips, calendário, mudanças, períodos, barra inferior
    Views.kt          botão de grade e visualizações diária, semanal, mensal + diária, anual e lista de eventos
    Calendar.kt       grade do mês (TRAB./FOLGA, bloco, notas, feriados, períodos, outras escalas)
    Sheets.kt         editar escala, mudança, nota, período, compartilhar, mês, mensagens
    SettingsSheet.kt  Temas / Dados / Idiomas / Sobre
    Render.kt         imagem "Compartilhar período" e "PDF do ano"
    theme/            10 paletas × claro/escuro (copiadas do style.css), Material 3 e Liquid Glass
  app/      só Android: MainActivity, ViewModel, arquivos (SAF), PDF (PdfDocument), FileProvider
```

## Visualizações

O botão de grade ao lado do seletor Pessoal | Work troca a visualização (vale para os dois modos e fica gravada):
**Diária**, **Semanal**, **Mensal** (padrão), **Mensal + diária**, **Anual** e **Lista de eventos**.
Na mensal, tocar num dia abre a diária (Pessoal) ou a nota (Work); na mensal + diária, o primeiro toque seleciona e
o segundo cria um evento/abre a nota. Na semanal, tocar num dia abre a diária; na anual, tocar num mês abre a mensal.
Na diária do Pessoal, tocar numa hora vazia cria um evento já naquele horário. Prévias em `docs/previews/visualizacoes/`.

## O que foi mantido

- Ciclo contínuo por blocos trabalho×folga (1–99 cada, ciclo ≤ 1000 dias), várias escalas (até 20) com nome e cor,
  pontinhos das outras escalas no dia.
- Mudanças a partir de uma data (só recalculam dali em diante), "Manter os dias anteriores como estão" (eras preservadas),
  remapeamento das mudanças quando os blocos são editados, avisos e confirmações com os mesmos textos.
- Notas (500 caracteres, cor, alinhamento), feriados, períodos (férias/licenças) por escala.
- Temas: Sistema/Claro/Escuro, estilos Material 3 e Liquid Glass, paletas Material You, Gruvbox, Nord, Dracula,
  Catppuccin, Tokyo Night, Solarized, Everforest, One Dark e Rosé Pine.
- Backup: exporta/importa **o mesmo JSON do PWA** (`app: "minha-escala"`, `version: 3`), com a mesma validação estrita,
  cópia `.bak`/`.bak2` antes de importar e "Último backup exportado".
- Compartilhar período como imagem (até 12 meses, opção de ocultar notas) e PDF anual A4 paisagem nas cores do tema.
- Restaurar padrão, primeira configuração "Criar minha escala", tela Sobre.
- Dados só no aparelho: o app **não pede permissão de internet**.

## Diferenças intencionais

- Símbolos de texto (⚙ ✎ ‹ › ＋ × ◐ ☀ ●) trocados por **Material Icons** (pacote core + 6 ícones copiados em `ui/AppIcons.kt`, sem a biblioteca extended, que pesa muito no build release).
- Seletores nativos do Android: data (DatePicker do Material 3), arquivos (Salvar/Abrir do sistema) e folha de compartilhamento.
- O Liquid Glass simula o vidro com gradientes e transparência (como o modo "Lite" do PWA), sem desfoque do fundo.
- Coisas só de navegador saíram: service worker, sincronização entre abas, aviso "Nova versão instalada".
- Os dados ficam em arquivos privados do app, gravados com `AtomicFile` em segundo plano. Se um arquivo não puder ser
  lido, o app avisa e não grava por cima; dados inválidos são corrigidos e o original fica numa cópia `.bak`, que pode ser
  restaurada em *Ajustes › Dados* junto com os dados anteriores a uma importação.
- O backup automático do Android só vai para a nuvem com criptografia de ponta a ponta (`disableIfNoEncryptionCapabilities`).
- Em telas de 320 dp as células do calendário ficam com ~34 dp de largura: é o limite físico de 7 colunas, por isso o dia
  inteiro é lido pelo TalkBack com data, situação e notas.

## Prévias

As imagens em `docs/previews/` foram renderizadas a partir deste mesmo código de UI (fora do Android, com a fonte padrão do
Linux; no celular a fonte é a Roboto).

## Idiomas

Ajustes › **Idiomas** tem quatro opções: **Sistema** (padrão), **Português**, **English** e **Español**.

- Em "Sistema", o app segue o idioma do aparelho: português e espanhol (de qualquer país) usam esse idioma; qualquer outro usa inglês.
  Ao voltar para o app depois de trocar o idioma do aparelho, a tela já aparece no idioma novo.
- No Android 13+, o Kalendrium também aparece em Configurações › Apps › Kalendrium › Idioma (`res/xml/locales_config.xml`).
- A escolha fica no arquivo `kalendrium.lang` do aparelho (fora do backup, para cada celular seguir o próprio idioma).
- Tudo é traduzido: telas, mensagens, datas (em inglês, mm/dd/aaaa e "October 6"), rótulos do ciclo, imagem compartilhada,
  PDF do ano, seletores de data e hora e notificações dos lembretes. Nomes que você digitou (escalas, eventos, notas) não mudam,
  e os dados gravados são os mesmos em qualquer idioma.
- Textos ficam no próprio código com `L("Hoje", "Today", "Hoy")` (`domain/I18n.kt`), assim a lógica continua testável fora do Android.

## Modos Pessoal e Work

O seletor **Pessoal | Work** ao lado do nome troca o contexto na mesma tela (estado `CalendarMode.PERSONAL` / `CalendarMode.WORK`),
mantendo o mês exibido. O cabeçalho, o card "Hoje", o calendário mensal, a navegação e os cards são os mesmos componentes;
só mudam os dados, as ações e o conteúdo de cada dia (`PersonalDayCell` / `WorkDayCell`).

- **Pessoal**: eventos, compromissos e lembretes (dia inteiro ou com horário, cor, descrição), nota por dia,
  lista do dia selecionado e lembretes com notificação do Android (AlarmManager, reagendados ao reiniciar o aparelho).
- **Work**: a escala de trabalho de sempre (ciclos, combinação de escalas, mudanças, períodos, notas e feriados).
- Os dados ficam em arquivos separados: `kalendrium.personal.v1` (pessoal) e `minhaEscala.material.v4` (escalas).
  O backup leva os dois; backups do PWA continuam importando e mantêm o calendário pessoal intacto.

## Ícone

O ícone de referência é o calendário azul com três pontos enviado pelo Juscelino (`docs/icone/kalendrium-icone-original.png`,
192×192). Como a referência é pequena e sem suavização, o desenho foi medido nela e é redesenhado pelo gerador com a mesma
geometria e a mesma cor (#8EB4D9), para ficar nítido em todos os tamanhos.
- Android 8+: ícone adaptativo; calendário (54 dp, dentro da zona segura de 66 dp) sobre fundo branco, então nenhuma máscara
  (círculo, squircle, quadrado) corta o desenho. Android 13+: versão monocromática temática (`drawable/ic_launcher_monochrome.xml`).
- Android 7: `ic_launcher` (o próprio calendário, faixa e pontos em branco) e `ic_launcher_round` (calendário num círculo
  branco), de mdpi a xxxhdpi em `res/mipmap-*`.
- Play Store: `docs/icone/ic_launcher-playstore.png` (512×512). Para regenerar tudo: `python3 docs/icone/gerador/gerar_icones.py`
  `app/src/main docs/icone` (precisa de Pillow).
