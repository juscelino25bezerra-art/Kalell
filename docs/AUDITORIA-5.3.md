# Auditoria técnica e refatoração — Kalendrium 5.3

Data: 09/10/2026 · Base: `Kalendrium-redesign Base.zip` (versionName 5.3, versionCode 6)

Legenda usada em todo o relatório:

- **[VERIFICADO]** lido no código e/ou confirmado por execução.
- **[COMPROVADO]** falha reproduzida por teste ou execução.
- **[RISCO]** problema plausível pelo código, sem reprodução.
- **[HIPÓTESE]** depende de condição não confirmada.
- **[NÃO INSPECIONADO]** fora do que foi lido ou executado.

---

## Limitações do ambiente (leia antes)

1. **Não foi possível compilar o app Android.** O ambiente bloqueia `dl.google.com` (403 da política de rede), que serve o
   Google Maven (AGP 8.13, AndroidX, Compose Android) e o Android SDK. O comando `gradle testDebugUnitTest` no projeto
   original falhou em 43 s com `Plugin [id: 'com.android.application', version: '8.13.0'] was not found`.
   Por isso **`./gradlew assembleDebug`, `assembleRelease`, R8, lint e `connectedAndroidTest` não foram executados.**
2. **Como os testes foram rodados mesmo assim (harness JVM, fora do projeto):** `domain/` e `ui/` compilados com Kotlin
   2.1.0 + plugin Compose contra Compose Multiplatform Desktop 1.7.3 (mesma geração de API do BOM 2024.12.01), e
   executados com o runtime Compose 1.5.12 (o último sem dependências do Google Maven). O único ajuste nas fontes é
   trocar a família de fontes `R.font.manrope_*` (API só do Android) por `FontFamily.Default` no `AppTheme.kt` copiado.
   Os testes não executam nenhum `@Composable`: exercitam domínio, `AppController` e `Storage`.
3. **Arquivos Android alterados** (`AndroidPlatform.kt`, `Reminders.kt`) foram **checados por tipo** contra o framework do
   Android 16 (`org.robolectric:android-all:16-robolectric-13921718`, Maven Central), com stubs de mesma assinatura para
   `FileProvider`, `ActivityResultLauncher`, `Canvas(android.graphics.Canvas)` e `asAndroidBitmap`. Isso prova que o código
   compila contra as APIs reais do Android, mas **não** substitui o build com AGP nem o teste no aparelho.
4. **Não verificado online:** checksum do `gradle-wrapper.jar` (SHA-256 `7d3a4ac4…6172`) e da distribuição Gradle
   (o redirecionamento de `services.gradle.org` também foi bloqueado). Também não foi feita busca de CVEs das dependências.

**Consequência:** este relatório **não concede aprovação de produção** (ver seção H).

---

## Etapa 0 — Reconhecimento

**Estrutura [VERIFICADO].** Um módulo (`:app`), 27 arquivos Kotlin de produção (≈ 6.300 linhas), 6 de teste JVM e 2 de
teste instrumentado. Pacotes: `domain/` (Kotlin puro: motor do ciclo, validação, JSON, i18n), `ui/` (Compose + `AppController`,
que não depende do Android), `app/` (Activity, ViewModel, `AndroidPlatform`, alarmes).

**Stack confirmada [VERIFICADO]:** Kotlin 2.1.0, AGP 8.13.0, Gradle 8.13 com `distributionSha256Sum`, Compose BOM 2024.12.01,
Material 3, kotlinx-serialization-json 1.7.3 (só a árvore `JsonElement`, sem `@Serializable` e sem o plugin de serialização),
coroutines (via lifecycle), desugaring de `java.time`, `compileSdk`/`targetSdk` 36, `minSdk` 24, R8 + shrinkResources no release.
`applicationId` `com.kalendrium` preservado.

Diferenças em relação ao prompt:

- A persistência usa **`android.util.AtomicFile`** (framework), não `androidx.core.util.AtomicFile`. Comportamento equivalente.
- **Não há `Mutex`**: a coordenação de escrita é uma fila serial (`Dispatchers.IO.limitedParallelism(1)`) em `ui/Storage.kt`.
- **Não há testes Compose UI** executáveis em JVM; os dois testes instrumentados (`MainActivityTest`, `AndroidPlatformTest`) exigem aparelho.

**Fluxos de dados [VERIFICADO].**

| Fluxo | Onde |
|---|---|
| Carga inicial (fora da Main) | `AppController.readAll` (`ui/AppController.kt:186`) → `Storage.readSafe` → `AndroidPlatform.read` (AtomicFile) |
| Gravação | `AppController.save/savePersonal` → `Storage.persist` (fila serial, só a versão mais recente por chave) |
| Exportação | `AppController.exportBackup` → `Codec.encodeBackup` em `Dispatchers.Default` → SAF `CreateDocument` |
| Importação | SAF `OpenDocument` → `openTextFile` (limite 10 MB) → `parseBackup` (validação estrita) → confirmação → `replaceAll` (cópias `.bak`/`.bak2`, gravação, desfaz a 1ª se a 2ª falhar) |
| Restaurar | `restorePrevious` lê `.bak` e troca com os dados atuais |
| Lembretes | `savePersonal` → `syncReminders` (fila própria) → `Reminders.sync` (AlarmManager, até 64) ; `ReminderReceiver` para disparo, boot, atualização, hora e fuso |
| Compartilhar/PDF | `Render.periodImage` (Default) → FileProvider `cache/share/` ; `Render.yearPdf` → `PdfDocument` (Default) → SAF |

**Componentes e permissões [VERIFICADO]** (`AndroidManifest.xml`): `MainActivity` exportada só com `MAIN/LAUNCHER` e sem
leitura de extras; `ReminderReceiver` `exported=false` (recebe broadcasts do sistema e os próprios alarmes);
`FileProvider` `exported=false` com `grantUriPermissions` e só `cache/share/`. Permissões: `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` (até API 32), `USE_EXACT_ALARM`. **Sem `INTERNET`.**
`allowBackup=true` com regras para `files/data/`.

**Linha de base [VERIFICADO]:** 77 testes JVM, 77 aprovados, 0 falhas (harness, item 2 acima). Os 4 testes instrumentados não rodaram.

**Inspeção:** lidos por completo `domain/*`, `app/*`, `ui/AppController.kt`, `ui/Storage.kt`, `ui/Platform.kt`, manifesto,
XMLs, Gradle, ProGuard, README e `docs/CORRECOES.md`. Nos demais arquivos de UI (`MainScreen`, `Views`, `Calendar`,
`PersonalScreen`, `Sheets`, `SettingsSheet`, `Components`, `Render`, `ColorPicker`, `AppIcons`, `theme/*`) a leitura foi
**dirigida** (efeitos, `remember`, corrotinas, cálculos em composição, folhas de edição); não foram revisados linha a linha
quanto a layout e acessibilidade **[NÃO INSPECIONADO em detalhe]**. O gerador de ícones em Python não foi auditado.

---

## A. Resumo executivo

**Estado geral.** O projeto já passou por uma auditoria anterior (5.2) e está acima da média: sem rede, sem segredos
versionados, assinatura fora do Git, gravação atômica, distinção entre "arquivo inexistente" e "erro de leitura",
validação estrita na importação, cópia `.bak` antes de importar e desfazer em caso de falha parcial. A arquitetura é enxuta
e o domínio é testável.

**Principais problemas encontrados (todos corrigidos nesta entrega, exceto onde indicado):**

1. **Crash comprovado na importação** (SEC-01): um JSON de ~20 KB com 10 mil `[` aninhados lança `StackOverflowError`
   no parser; como é `Error` e não `Exception`, passa pelo `catch` e fecha o app. Reproduzido no harness.
2. **Crash sem seletor de arquivos** (SEC-02): sem um app de documentos, `ActivityResultLauncher.launch` lança
   `ActivityNotFoundException`, que sobe até o `viewModelScope` e fecha o app (exportar backup, PDF, importar).
3. **Promessa de cópia de segurança não garantida** (REL-01): a falha ao gravar `.bak` era ignorada; o app dizia "uma cópia
   foi mantida" e seguia substituindo os dados.
4. **Trabalho pesado na thread principal do receiver** (REL-03): reagendar lembretes após boot/fuso lia e validava até 10 MB
   de JSON dentro de `onReceive`.
5. **Corrida entre reagendamentos** (REL-04): app e receiver podiam intercalar `Reminders.sync` e deixar alarmes órfãos de
   eventos excluídos.

**Riscos prioritários restantes:** nenhum build Android foi feito; R8/release e os testes instrumentados não rodaram;
o backup do Android 7–8 vai para a nuvem sem a exigência de criptografia de ponta a ponta (SEC-05, não corrigido).

**Prontidão para distribuição:** o código está em condição de seguir para build e testes no Android Studio; não há
evidência suficiente para liberar a produção daqui.

---

## B. Matriz de vulnerabilidades (segurança)

| ID | Severidade | Arquivo/Linha (base) | Evidência | Impacto | Correção |
|---|---|---|---|---|---|
| SEC-01 | **MÉDIO** · [COMPROVADO] · confiança alta | `domain/Codec.kt:105` `parseOrNull`; chamado por `AppController.parseBackup` (`ui/AppController.kt:549`) dentro de `scope.launch` | Harness, heap 256 MB: profundidade 10.000 (20 KB) → `StackOverflowError`; idem com 100 mil, 1 mi e 4 mi. `catch (_: Exception)` não captura `Error` | Negação de serviço local: o arquivo de backup é uma fronteira de confiança (recebido por e-mail, mensageiro, nuvem). Ao importar, o app fecha; nada é gravado, então **não há perda de dados** | Pré-verificação linear de aninhamento fora de strings (`Codec.MAX_DEPTH = 32`; o formato real mais fundo tem 7 níveis). Testes: `HardeningTest` (4) e `importar JSON muito aninhado recusa sem fechar o app` |
| SEC-02 | **BAIXO** · [RISCO] · confiança média | `app/AndroidPlatform.kt:68` `pick` | `ActivityResultRegistry` relança a exceção de `startActivityForResult`; `pick` não a tratava | Fecha o app em aparelhos sem DocumentsUI (perfis gerenciados, Android Go/TV com o app desativado). Disponibilidade, sem exposição de dados | `pick` captura `ActivityNotFoundException`, limpa o pedido pendente e devolve "falhou" (`saveFile` → `false`, `openTextFile` → `FileRead.Failed`) com mensagem ao usuário. Sem teste JVM possível (API Android); checado por tipo |
| SEC-03 | **BAIXO** · [VERIFICADO] · confiança alta | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`; `README.md:118` | As regras incluem `data/` inteiro, que contém `kalendrium.lang.json`; o README afirma que o idioma fica "fora do backup" | Divergência entre o comportamento e o que é documentado (o idioma de um aparelho viaja para outro). Sem dado pessoal sensível | `<exclude>` do arquivo de idioma nas três seções de backup |
| SEC-04 | **INFORMATIVO** · [VERIFICADO] | `app/AndroidPlatform.kt:171` `openTextFile` | Bytes eram convertidos com `toString(UTF_8)`, que troca bytes inválidos por "�" em silêncio | Um arquivo binário ou com outra codificação era lido como texto alterado (depois recusado pela validação, com a mensagem genérica "Backup inválido") | Decodificação UTF-8 estrita; tamanho acima do limite e erros de E/S passam a ter mensagens próprias (`FileRead.TooLarge`, `FileRead.Failed`). Testes: `arquivo grande demais ou ilegivel tem mensagem propria e nada muda` |
| SEC-05 | **BAIXO** · [VERIFICADO] · não corrigido | `AndroidManifest.xml:13`, `res/xml/backup_rules.xml` | `requireFlags="clientSideEncryption"` só existe a partir do Android 9 (o próprio comentário do arquivo diz isso); `minSdk` é 24 | No Android 7.0–8.1, escalas, notas e eventos pessoais vão para o backup do Google sem a exigência de criptografia de ponta a ponta (continuam cifrados em trânsito e no servidor pelo Google) | Decisão de produto: aceitar, ou desligar o backup em nuvem só nessas versões (ex.: `BackupAgent` que não faz nada abaixo da API 28). Não alterado para não mudar comportamento sem autorização |
| SEC-06 | **INFORMATIVO** · [VERIFICADO] | `domain/Model.kt:112` `Ids` | `java.util.Random` para ids | Ids não são segredos nem controlam acesso; sem impacto de segurança | Nenhuma |
| SEC-07 | **INFORMATIVO** · [VERIFICADO] | `app/build.gradle.kts`, `gradle/libs.versions.toml` | Dependências de dez/2024 (Kotlin 2.1.0, BOM 2024.12.01, core-ktx 1.15.0, activity 1.9.3, lifecycle 2.8.7). **Nenhuma CVE identificada** — a busca não foi feita (sem acesso aos índices) | Dívida de atualização, não vulnerabilidade demonstrada | Atualizar num ciclo próprio, com build e testes no Android Studio |

**Controles verificados sem achado:** sem `INTERNET`; sem logs (`Log.*`, `println`, `printStackTrace` ausentes); sem segredos
no repositório (`keystore.properties` e `*.jks` no `.gitignore`, senhas só em arquivo local ou variáveis de ambiente do build);
`PendingIntent` com `FLAG_IMMUTABLE` e intents explícitas; `FileProvider` não exportado, restrito a `cache/share/`, nomes de
arquivo gerados a partir de slug ASCII e datas já validadas (sem path traversal); dados em `filesDir` privado; repositórios
Gradle só `google()` e `mavenCentral()` com `FAIL_ON_PROJECT_REPOS`; `distributionSha256Sum` presente; regras R8 coerentes
com o uso só de `JsonElement`; `FLAG_SECURE` opcional disponível ("Ocultar na tela de recentes").

**STRIDE resumido:** *Spoofing* e *Elevation*: superfície mínima (uma Activity de launcher, receiver não exportado).
*Tampering*: só via arquivo importado (SEC-01, validação estrita existente). *Repudiation*: não se aplica (app local, um usuário).
*Information disclosure*: backup do Android (SEC-05), compartilhamento de imagem só por ação do usuário.
*DoS*: SEC-01 e SEC-02.

---

## C. Matriz de resiliência (confiabilidade, não segurança)

| ID | Categoria | Onde (base) | Evidência / cenário | Status |
|---|---|---|---|---|
| REL-01 | Recuperação / perda de dados | `ui/Storage.kt:61` `keepBak`; `ui/AppController.kt:579` `replaceAll` | Os retornos de `platform.write` em `keepBak` eram ignorados. Com armazenamento quase cheio, a cópia `.bak` podia falhar e a importação seguia, embora a confirmação prometa "uma cópia dos dados atuais fica guardada" | **Corrigido**: `keepBak` devolve `Boolean`; `replaceAll` não substitui nada se a cópia falhar. Teste: `sem conseguir guardar a copia dos dados atuais a importacao nao substitui nada` e `copia de seguranca informa falha de gravacao` |
| REL-02 | Recuperação / corrupção | `ui/AppController.kt:186` `readAll` | Ao carregar dados inválidos, o original ia para `.bak` sem checar a gravação; a mensagem dizia "uma cópia foi mantida" e a próxima edição gravava a versão corrigida por cima do original | **Corrigido**: se a cópia falha, a chave fica bloqueada para escrita (mesmo mecanismo do C3) e o aviso não promete cópia. Teste: `dados corrompidos sem copia possivel nao sao sobrescritos pelos padroes` |
| REL-03 | ANR / ciclo de vida | `app/Reminders.kt:129` `ReminderReceiver` | `syncFromStorage` (ler, analisar e validar até 10 MB, agendar 64 alarmes) rodava em `onReceive`, na thread principal, competindo com o início do app após boot | **Corrigido**: `goAsync()` + executor serial. [RISCO] não medido: sem aparelho |
| REL-04 | Concorrência | `app/Reminders.kt:39` `sync` ; chamado por `AndroidPlatform.kt:112` e pelo receiver | Duas threads sem coordenação lendo e gravando a lista `codes` em `SharedPreferences`: uma sincronização podia sobrescrever a lista da outra e deixar alarmes fora do controle (lembrete de evento já excluído) | **Corrigido**: `@Synchronized` e cancelamento da faixa fixa de requestCodes (1000–1063) sem depender de lista gravada |
| REL-05 | Falha silenciosa | `app/Reminders.kt:65` | No Android 12, revogar "Alarmes e lembretes" entre a checagem e o agendamento faz `setExactAndAllowWhileIdle` lançar `SecurityException`; o `runCatching` externo engolia e **nenhum** lembrete restante era agendado | **Corrigido**: cai para `setAndAllowWhileIdle` e continua |
| REL-06 | Vazamento de recurso | `app/AndroidPlatform.kt:156` `savePdf` | `PdfDocument.close()` não era chamado se o desenho lançasse exceção (memória nativa) | **Corrigido** com `try/finally` |
| REL-07 | Integridade da exportação | `app/AndroidPlatform.kt:146` `saveFile` | Se a escrita falha no meio, o documento recém-criado ficava com conteúdo parcial | **Corrigido**: o documento é apagado (`DocumentsContract.deleteDocument`) quando a escrita falha |
| REL-08 | Main thread | `ui/AppController.kt:580-581` | `replaceAll` codificava os dados atuais (até 5.000 eventos + 5.000 notas) na thread principal | **Corrigido**: codificação em `io` |
| REL-09 | Recuperação | `ui/AppController.kt:191` + `605` | Ao carregar dados corrompidos, o `.bak` válido (pré-importação) é empurrado para `.bak2`; "Restaurar" só lê `.bak` (que agora contém o JSON corrompido), então a cópia válida fica inacessível pela interface | [VERIFICADO pelo código] · **Não corrigido**: exige definir a semântica de troca `.bak` ↔ `.bak2`; a cópia continua no aparelho |
| REL-10 | Estado global | `domain/I18n.kt:25` `I18n.lang` | Idioma é um `var` global lido por `L()` em qualquer thread; o receiver e o app o escrevem | [RISCO] baixo: os dois resolvem a mesma preferência. Não alterado (mudança ampla) |
| REL-11 | Idempotência | `ui/AppController.kt:428` | Dois toques em "Salvar" dentro do mesmo quadro, ao criar escala, lançam dois `submitCycle` e podem duplicar o id da escala (na próxima carga, a validação descarta a cópia e avisa) | [HIPÓTESE]: depende de dois toques antes da recomposição. Não alterado |
| REL-12 | Desempenho | `ui/Views.kt:548`, `ui/AppController.kt:200` | A lista de eventos reordena todos os eventos a cada recomposição; a carga inicial valida `.bak` por completo só para saber se há cópia | [RISCO] sem medição; não alterado (sem evidência de lentidão) |

**Verificado sem achado:** sem `GlobalScope`, `runBlocking` ou `Thread` cru; corrotinas ligadas a `viewModelScope`/
`lifecycleScope`; nenhum `catch` engole `CancellationException` (os `catch` existentes envolvem só código não suspenso);
gravação serializada e atômica; leitura inicial fora da Main; `rememberSaveable` nas folhas; datas civis com `LocalDate`
e `ChronoUnit.DAYS` (sem milissegundos); `Dates.mod` correto para datas anteriores à âncora; lembretes de dia inteiro e
horário de verão via `LocalDateTime.atZone` (lacunas avançam a hora); reagendamento em `TIME_SET`/`TIMEZONE_CHANGED`/boot/
atualização; limites de 5.000 eventos, 5.000 notas, 200 períodos, 20 escalas e ciclo ≤ 1000 dias. Os testes existentes
cobrem anos bissextos (âncora em 29/02), virada de mês e ano, eras preservadas e remapeamento de mudanças.

**Não executado:** morte de processo durante gravação no aparelho, disco cheio real, rotação/tema/recriação, permissões
negadas, notificações reais, desempenho e memória (nenhuma medição foi inventada).

---

## D. Auditoria arquitetural

**Avaliação geral:** sem "AI slop" relevante. O código é denso e direto; comentários explicam o porquê (decisões de
compatibilidade com o PWA), não repetem o código. Não há interfaces artificiais: `Platform` existe para testar o
`AppController` sem Android, e isso é usado. Não há dependências sem uso no Gradle. Não encontrei código morto relevante.

Dívidas reais (não alteradas, para não reescrever código funcional):

| Item | Onde | Problema demonstrável | Recomendação |
|---|---|---|---|
| ARQ-01 | `ui/AppController.kt` (752 linhas) | Concentra estado de tela, regras de importação/restauração, textos trilíngues e navegação | Extrair só o fluxo de backup (`parseBackup`/`replaceAll`/`restorePrevious`) para uma classe própria quando houver próxima mudança nessa área |
| ARQ-02 | `domain/I18n.kt` | `I18n.lang` global mutável usado por todo o domínio (ver REL-10); dificulta testes paralelos | Manter enquanto os testes forem sequenciais; documentado |
| ARQ-03 | `ui/PersonalScreen.kt:224` `EventSheet` | `remember {}`/`rememberSaveable {}` sem chave de `e`/`date0`: se a folha trocasse direto de um evento para outro, o formulário ficaria com os dados do anterior | [HIPÓTESE] hoje a troca direta não acontece (a folha é modal). Chavear em `e?.id` se a navegação mudar |
| ARQ-04 | `ui/*.kt` | Textos inline `L("pt","en","es")` em vez de `strings.xml` | Escolha deliberada e documentada (testável fora do Android); não é defeito |
| ARQ-05 | `README.md` | Versão e contagem de testes desatualizadas (dizia 5.2/versionCode 5 e 77 testes) | **Corrigido** |

---

## E. Alterações implementadas

Diff completo: `kalendrium-5.3-auditoria.diff` (entregue junto). Visual, navegação, cores, formato de dados e backup,
`applicationId` e dependências **não mudaram**. Nenhuma biblioteca foi adicionada.

| Arquivo | Problema original | Modificação | Testes |
|---|---|---|---|
| `domain/Codec.kt` | SEC-01 | `MAX_DEPTH = 32` e `depthWithin()` antes do parser | `HardeningTest` (4 testes) |
| `ui/Platform.kt` | SEC-02/SEC-04: "falhou" e "grande demais" apareciam como "Backup inválido" | `FileRead.TooLarge` e `FileRead.Failed` | `AppControllerTest` |
| `ui/AppController.kt` | REL-01, REL-02, REL-08, mensagens de SEC-02/SEC-04 | `replaceAll` aborta sem cópia e codifica em `io`; `readAll` bloqueia a chave se a cópia do original falhar; aviso só promete cópia quando ela existe; mensagens para arquivo grande/ilegível | 4 testes novos |
| `ui/Storage.kt` | REL-01 | `keepBak` devolve `Boolean` e não grava `.bak` se a rotação para `.bak2` falhar; `block(key)`; `persistNow` recebe lambda (codifica em `io`) | `copia de seguranca informa falha de gravacao` |
| `app/AndroidPlatform.kt` | SEC-02, SEC-04, REL-06, REL-07 | `pick` trata `ActivityNotFoundException`; UTF-8 estrito; `TooLarge`/`Failed`; `PdfDocument` sempre fechado; documento apagado se a escrita falhar | Checagem de tipos contra Android 16. **Sem teste executado** |
| `app/Reminders.kt` | REL-03, REL-04, REL-05 | `goAsync` + executor; `@Synchronized`; cancela a faixa 1000–1063; fallback para alarme inexato; sem a lista em `SharedPreferences` | Checagem de tipos. **Sem teste executado** |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | SEC-03 | `<exclude>` de `data/kalendrium.lang.json` | Não testável sem aparelho |
| `test/.../domain/HardeningTest.kt` (novo) | — | Aninhamento, colchetes dentro de strings, backup real mais fundo cabe no limite | — |
| `test/.../ui/AppControllerTest.kt` | — | 5 testes novos (importação aninhada, `TooLarge`/`Failed`, cópia que falha, corrupção sem cópia, `keepBak`) | — |
| `README.md` | ARQ-05 | Versão 5.3/versionCode 6 e 86 testes | — |

**Compatibilidade de dados:** nenhum formato mudou. Backups 5.2/5.3 e do PWA (`version: 3`) continuam importando
(testes de ida e volta existentes passam). O limite de 32 níveis fica bem acima dos 7 do formato real.
A preferência antiga `reminders/codes` em `SharedPreferences` deixa de ser usada; os alarmes que ela registrava estão
dentro da faixa cancelada.

---

## F. Testes executados

| Comando | Resultado real |
|---|---|
| `gradle testDebugUnitTest` no projeto original (Gradle 8.14.3 local) | **Falhou** em 43 s: AGP 8.13.0 não resolvido (Google Maven bloqueado). Nenhum teste rodou |
| `./gradlew assembleDebug`, `assembleRelease`, `connectedAndroidTest`, lint | **Não executados** (mesmo motivo; sem SDK, sem emulador) |
| Harness JVM, código original: `gradle test` | **77 aprovados, 0 falhas, 0 ignorados** |
| Sonda no código original: `Codec.parseOrNull` com profundidade 10 mil, 100 mil, 1 mi, 4 mi (heap 256 MB) | **`StackOverflowError` nas quatro** (SEC-01 comprovado) |
| Harness JVM, código corrigido: `gradle test` | **86 aprovados, 0 falhas, 0 ignorados** (AuditTest 22, EngineTest 13, HardeningTest 4, I18nTest 7, PersonalTest 6, ViewsTest 4, AppControllerTest 30) |
| Checagem de tipos de `AndroidPlatform.kt` e `Reminders.kt` contra `android-all` 16 | Original: compila. Corrigido: **compila, sem avisos** |

Falhas encontradas durante o trabalho: o teste novo `backup real mais fundo cabe no limite` falhou na primeira execução
porque eu tinha estimado 5 níveis para o formato; o real é 7. O comentário de `MAX_DEPTH` e o teste foram corrigidos (o limite, 32, não mudou).

**Pendentes (precisam do Android Studio ou de CI com acesso ao Google Maven):**

1. `./gradlew clean testDebugUnitTest` (deve dar 86/86).
2. `./gradlew assembleDebug` e `assembleRelease` (R8 e shrinkResources; conferir que nada do kotlinx-serialization é removido).
3. `./gradlew connectedAndroidTest` (os 4 testes de `AndroidPlatformTest` e os de `MainActivityTest`).
4. No aparelho: exportar e reimportar backup; importar um arquivo com `[[[[…` (deve dizer "Backup inválido" sem fechar);
   reiniciar o aparelho com lembretes futuros e conferir que tocam; trocar o fuso; excluir um evento com lembrete e
   conferir que não toca; negar a permissão de notificação; Android 12 com "Alarmes e lembretes" desligado.

---

## G. Pendências

**Bloqueadores de release**

1. Build `assembleRelease` com R8 e execução dos testes JVM e instrumentados no ambiente do projeto (seção F).
2. Teste manual dos lembretes após reinício e mudança de fuso (o receiver mudou de thread).

**Melhorias recomendadas**

1. REL-09: "Restaurar" deveria cair para `.bak2` quando `.bak` não é um backup válido.
2. SEC-05: decidir o backup em nuvem no Android 7–8.
3. Verificar o SHA-256 do `gradle-wrapper.jar` com `gradle wrapper --gradle-version 8.13` ou a lista oficial de checksums.
4. Atualizar dependências (SEC-07) com build e testes.

**Melhorias opcionais**

1. ARQ-01: extrair o fluxo de backup do `AppController`.
2. REL-11: guarda de idempotência em `submitCycle` (modo Nova escala).
3. REL-12: `remember` da lista de eventos ordenada por versão dos dados.
4. ARQ-03: chavear o estado da `EventSheet` no id do evento.

---

## H. Veredito da banca

**DevSecOps.** A superfície de ataque é pequena e bem controlada (sem rede, componentes não exportados, FileProvider
restrito, `PendingIntent` imutável, sem segredos). O único vetor real é o arquivo de backup importado; nele havia um
crash comprovado (SEC-01), agora bloqueado antes do parser e coberto por teste. Riscos residuais: backup em nuvem sem
criptografia de ponta a ponta no Android 7–8 (SEC-05) e dependências não verificadas contra bases de CVE.
Parecer: **aprovado com ressalvas**, condicionado ao build release com R8.

**SRE.** A estratégia de persistência (AtomicFile, fila serial, bloqueio após falha de leitura, cópias `.bak`, desfazer
importação parcial) é sólida. As correções fecham os pontos em que o app prometia uma cópia que podia não existir, tiram
trabalho pesado da thread principal do receiver e eliminam a corrida de alarmes. Nada disso foi exercitado em aparelho:
morte de processo, disco cheio real, boot e fuso estão sem evidência de execução.
Parecer: **não aprovado para produção** até os testes instrumentados e manuais da seção F.

**Architect.** Código enxuto, domínio testável, sem camadas artificiais nem dependências sobrando. As alterações são
locais e seguem o estilo existente. A dívida relevante é o tamanho do `AppController` e o idioma global, nenhuma bloqueante.
Parecer: **aprovado com ressalvas**.

### Conclusão conjunta: **REPROVADO** (para distribuição neste momento)

Não por defeito conhecido em aberto (os encontrados e corrigíveis com segurança foram corrigidos), mas porque os critérios
mínimos de evidência não foram atingidos: o app não foi compilado, o release com R8 não foi gerado e os testes
instrumentados e de aparelho não rodaram. Executados os itens de **G › Bloqueadores** com sucesso, a recomendação da banca
passa a **APROVADO COM RESSALVAS** (SEC-05, REL-09 e as melhorias recomendadas).
