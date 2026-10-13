<!--
  Kalendrium — Copyright (C) 2026 Juscelino Be
  SPDX-License-Identifier: GPL-3.0-or-later
-->
# Auditoria de licenças e conformidade open source — Kalendrium

Auditoria da versão **6.1** (`versionCode` 8), feita em 13/10/2026 sobre a `main` no commit `ee68a41`, na branch `audit/open-source-compliance`. Escopo: licença do projeto, dependências diretas e transitivas, recursos visuais (fontes, ícones, paletas), dados e algoritmos de calendário, avisos dentro do app, cadeia de compilação e automação no GitHub Actions.

> Este relatório é uma análise técnica de licenças, não um parecer jurídico.

## 1. Resumo executivo

**Antes desta auditoria, o Kalendrium não tinha licença.** Não havia arquivo `LICENSE`, cabeçalho de licença nos arquivos nem licença nos metadados do GitHub, e o repositório é privado. Sem licença, o código é "todos os direitos reservados". A licença foi definida pelo autor nesta auditoria: **GPL-3.0-or-later**.

**Compatibilidade das licenças: nenhum conflito no que é distribuído.** Os 88 componentes do APK de release são Apache-2.0, mais o `desugar_jdk_libs` (GPL-2.0 com Classpath Exception, que a exceção torna utilizável junto com o app). Os recursos embutidos são a fonte Manrope (OFL-1.1) e os ícones Material (Apache-2.0). O único componente com licença incompatível com a GPL é o JUnit 4 (EPL-1.0), que só roda nos testes e não entra no APK. O CI agora impede que isso mude sem aviso.

**Obrigações de distribuição: não estavam cumpridas e foram corrigidas.** O app citava nomes e licenças, mas não trazia nenhum texto de licença nem aviso de copyright (o app não tem internet, e as "licenças completas" ficavam só nos repositórios de cada projeto). Faltavam:
- a cópia da Apache 2.0;
- os NOTICE do Kotlin, do kotlinx.coroutines e do kotlinx.serialization;
- o texto da OFL junto com a fonte;
- a GPL 2.0 do desugar e o **aviso BSD do ThreeTen**, que acompanha o `java.time` copiado para o APK e não era mencionado em lugar nenhum;
- a disponibilização do **código-fonte do desugar_jdk_libs** (GPL 2.0, seção 3);
- o aviso de modificação nos ícones copiados.

**Estado após este PR:**
- licença GPL-3.0-or-later com `LICENSE` e cabeçalhos SPDX;
- avisos e textos completos dentro do app, sem internet;
- `THIRD_PARTY_NOTICES.md` e textos originais em `third_party/licenses/`;
- código-fonte do desugar anexado a cada release publicado;
- SBOM CycloneDX e verificação automática no CI, com regra exata por artefato distribuído;
- Actions fixadas por SHA no workflow de release.

Compilação, os 108 testes unitários e a auditoria passaram no GitHub Actions.

**Não é declarada conformidade integral.** Os motivos estão em *10. Pendências*: o repositório é privado (a GPL exige dar acesso ao código-fonte a quem recebe o APK), a tela de licenças não foi aberta num aparelho e a chave de assinatura de teste continua no repositório.

## 2. Licença principal

| Onde se procurou | Antes | Depois |
|---|---|---|
| `LICENSE` na raiz | Ausente | GPL-3.0 integral (texto oficial da FSF, SHA-256 `3972dc97…986`) |
| README | Sem licença do projeto (só créditos de terceiros) | Seção *Licença, créditos e componentes de terceiros* |
| Cabeçalhos dos arquivos | Nenhum | `SPDX-License-Identifier: GPL-3.0-or-later` em todos os `.kt`, `.kts`, `proguard-rules.pro`, gerador do ícone e workflows; `AppIcons.kt` fica sob `Apache-2.0` (código copiado do Material Icons) |
| Metadados do GitHub | `license: null`, repositório privado | O GitHub detecta o `LICENSE` depois do merge |
| Dentro do app | Sem licença do Kalendrium | *Ajustes › Sobre › Licenças*: aviso de copyright, ausência de garantia e a GPL completa |

A licença foi escolhida pelo autor durante a auditoria (pergunta direta, resposta "GPL-3.0-or-later"). Não foi presumida.

## 3. Inventário

Inventário completo, com versões resolvidas, origem, licença, titular, obrigações e uso: [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md). O SBOM CycloneDX 1.6 é gerado a cada execução do workflow *Auditoria de licenças* (`build/reports/sbom.json`, no artefato `auditoria-licencas`).

As versões **efetivamente resolvidas** vieram do Gradle no CI, não do `libs.versions.toml`, nos classpaths `releaseRuntimeClasspath`, `coreLibraryDesugaring`, `debugRuntimeClasspath`, `debugUnitTestRuntimeClasspath` e `debugAndroidTestRuntimeClasspath`. Exemplos de diferença entre o declarado e o resolvido: o Compose BOM 2024.12.01 resolve Compose 1.7.6 e Material 3 1.3.1; o Kotlin 2.1.0 traz `kotlin-stdlib-jdk7/jdk8` 1.8.20; o Activity e o Lifecycle trazem kotlinx.coroutines 1.7.3.

| Escopo | Componentes | Licenças |
|---|---|---|
| APK de release | 86 (`releaseRuntimeClasspath`) + 2 (`coreLibraryDesugaring`) | Apache-2.0 (86); GPL-2.0 com Classpath Exception (desugar_jdk_libs, código copiado para o APK); BSD-3-Clause (desugar_jdk_libs_configuration, só configuração, nada no APK) + recursos: OFL-1.1 (Manrope), Apache-2.0 (ícones) |
| Só APK de teste (debug) | 1 | Apache-2.0 (`ui-test-manifest`) |
| Só testes | 22 | Apache-2.0, BSD-3-Clause (Hamcrest), EPL-1.0 (JUnit 4) |
| Ferramentas de build | Gradle 8.13, AGP 8.13.0, Kotlin 2.1.0, CycloneDX 3.5.1 | Apache-2.0 (não distribuídas) |

Estado de manutenção: todos os componentes do APK são mantidos ativamente (AndroidX, JetBrains, Google). As versões usadas não são as mais recentes (Kotlin 2.1.0, Compose BOM 2024.12.01, Core 1.15.0), mas a auditoria **não atualizou dependências**: nenhuma tem problema de licença, e atualizar só por haver versão nova está fora do escopo. Nenhuma dependência é dinâmica (`+` ou `latest`).

## 4. Compatibilidade e obrigações

"Compatível" fala da licença; "obrigações" fala do que a distribuição precisa fazer. As duas coisas são avaliadas separadamente.

| Componente | Licença | Compatível com GPL-3.0+? | Obrigações cumpridas antes? | Situação após o PR |
|---|---|---|---|---|
| AndroidX / Jetpack (71 artefatos `androidx.*`, inclui código nativo do graphics-path) | Apache-2.0 | Sim | Não (sem texto da licença no app) | COMPATÍVEL |
| Kotlin stdlib (com trechos GWT/Guava Apache-2.0 e Boost BSL-1.0) | Apache-2.0 + NOTICE | Sim | Não | COMPATÍVEL |
| kotlinx.coroutines, kotlinx.serialization | Apache-2.0 + NOTICE | Sim | Não (NOTICE ausente) | COMPATÍVEL |
| JetBrains Annotations, Guava ListenableFuture | Apache-2.0 | Sim | Não (nem citados no app) | COMPATÍVEL |
| desugar_jdk_libs 2.1.3 | GPL-2.0 com Classpath Exception | Sim, pela exceção | Não (sem texto da GPL 2.0, sem código-fonte, sem o aviso do ThreeTen) | COMPATÍVEL COM OBRIGAÇÕES: código-fonte anexado a cada release (passa a valer no próximo release) |
| ↳ aviso ThreeTen (JSR-310) no java.time | BSD-3-Clause (aviso original) | Sim | Não (desconhecido) | COMPATÍVEL |
| desugar_jdk_libs_configuration 2.1.3 | BSD-3-Clause | Sim | — (não vai no APK) | COMPATÍVEL |
| Manrope 4.505 | OFL-1.1 | Sim (a OFL permite empacotar com software sob qualquer licença) | Não (texto só em `docs/`, fora do APK) | COMPATÍVEL |
| Material Design Icons (9 ícones copiados) | Apache-2.0 | Sim | Parcial (atribuídos, sem aviso de modificação) | COMPATÍVEL |
| Paletas de cores | Valores de cor | Não se aplica | Créditos já existiam | COMPATÍVEL (informativo) |
| JUnit 4.13.2 (só testes) | EPL-1.0 | **Não** para combinar e distribuir | — (não distribuído) | COMPATÍVEL COM OBRIGAÇÕES: manter fora do APK (o CI confere) |

Nenhum componente ficou **INCOMPATÍVEL** ou **NÃO VERIFICADO**.

**Apache 2.0.** Compatível com a GPLv3 (não com a GPLv2). Exige entregar cópia da licença, manter os avisos e reproduzir os arquivos NOTICE (§4(d)). Os NOTICE do Kotlin, do kotlinx.coroutines (tag `1.7.3`) e do kotlinx.serialization (tag `v1.7.3`) foram copiados das tags exatas. Nenhum artefato do APK traz NOTICE dentro do JAR/AAR (conferido no CI). Arquivos modificados precisam de aviso de mudança (§4(b)): anotado em `AppIcons.kt` e `ic_stat_event.xml`.

**desugar_jdk_libs e a GPL 2.0.** O `coreLibraryDesugaring` não aparece no `releaseRuntimeClasspath`, mas o L8 copia o código da biblioteca para dentro do APK: há 141 classes `j$.time` no APK de release (conferido). Por isso ele conta como distribuído e a política o trata como escopo de release. A Classpath Exception permite ligar a biblioteca a um programa sob outra licença, então não há conflito com a GPLv3. O código da biblioteca, porém, continua GPL-2.0, e a seção 3 exige acompanhar o binário com o código-fonte ou oferecê-lo no mesmo lugar. O código da versão 2.1.3 é o commit `3302248e2780372a647789d714e03b02dcf559e7` de `google/desugar_jdk_libs` (o repositório não tem tags 2.x; o commit foi identificado pelo histórico de `VERSION_JDK11.txt` e pelo `CHANGELOG.md`). O workflow de release passa a anexar esse código a cada release. Em 93 dos 96 arquivos do `java.time` há também o aviso BSD do ThreeTen, que exige reproduzir o copyright na documentação do binário: agora está nos avisos do app.

**EPL 1.0 e JUnit.** A EPL-1.0 é incompatível com a GPL (a FSF a lista entre as licenças livres incompatíveis). O JUnit é `testImplementation`/`androidTestImplementation`: só aparece nos classpaths de teste, não em `releaseRuntimeClasspath` nem em `debugRuntimeClasspath`, e não entra em nenhum APK. Rodar testes não é distribuir o JUnit junto com o Kalendrium. A política só permite o JUnit no escopo de testes; se ele vazar para o APK, o CI falha.

**Kotlin stdlib e Boost.** O `license/README.md` do Kotlin 2.1.0 aponta código derivado do Boost em `MathJVM.kt` (Boost Software License 1.0). A BSL-1.0 dispensa o aviso em cópias em forma de código-objeto, mas o aviso foi incluído mesmo assim.

## 5. Recursos visuais

| Recurso | Origem | Licença | Atribuição | Verificação |
|---|---|---|---|---|
| Fonte Manrope (5 pesos, `res/font`) | googlefonts/manrope, versão 4.505 | OFL-1.1 | Copyright e licença nos metadados das fontes e agora no app | Metadados lidos com fontTools (nome, versão, copyright, licença); `OFL.txt` idêntico ao do repositório oficial (commit `468c0db`) |
| 8 ícones em `AppIcons.kt` (Outlined: Schedule, GridView, BrightnessMedium, CalendarMonth, CalendarToday, DarkMode, LightMode, Today) | Material Icons Extended / google/material-design-icons | Apache-2.0 | Já atribuídos no cabeçalho; aviso de modificação acrescentado | **Pixel a pixel**: cada ícone renderizado a 240 px e comparado com o SVG original (`materialiconsoutlined/24px.svg`): 0 pixel diferente nos 8 |
| `ic_stat_event.xml` (notificação) | Material Icons "event" (filled) | Apache-2.0 | Já atribuído; origem exata e modificação (VectorDrawable branco) acrescentadas | Pixel a pixel contra `materialicons/24px.svg`: 0 pixel diferente |
| Ícones Material Icons Core (`Icons.Outlined.Info`, `Icons.Filled.Check` etc.) | Biblioteca `material-icons-core` (AndroidX) | Apache-2.0 | Coberto pela seção AndroidX | Dependência resolvida |
| Ícone do app ("Dia e noite"), `ic_launcher_*`, gerador e imagens em `docs/icone/` | Criação própria | GPL-3.0-or-later | — | Gerado pelo `docs/icone/gerador/gerar_icones.py` do próprio repositório |
| Paletas (`Palettes.kt`) | Valores de cor de Gruvbox, Nord, Dracula, Catppuccin, Tokyo Night, Solarized, Everforest, One Dark, Rosé Pine e Material 3 | Projetos MIT/Apache; aqui só valores de cor | Créditos mantidos no app | Nenhum arquivo copiado; valores de cor isolados não costumam ser protegidos por direito autoral |

Não há imagens, ilustrações, animações ou componentes visuais de terceiros além desses. A identidade visual não foi alterada.

## 6. Calendário, feriados e escalas

- **Feriados:** não há base de dados de feriados nem biblioteca, cálculo (Páscoa, Carnaval) ou lista embutida. "Feriado" é uma marcação que o usuário põe numa nota (`Note.holiday` em `Model.kt`). Não há dados de terceiros a licenciar.
- **Escalas (4×1, 4×2, 5×1, personalizadas, combinadas):** o motor (`domain/Engine.kt`) percorre blocos de dias de trabalho e folga a partir de uma data-âncora, com mudanças e eras preservadas. O código diz "mesmo algoritmo do PWA (app.js)": é o porte do PWA **Minha Escala**, do mesmo autor. Não foi encontrada implementação de terceiros, e padrões de escala, como conceitos, não são protegidos.
- **Datas:** `java.time` (OpenJDK, via desugar_jdk_libs no Android 7, ver seção 4) e utilitários próprios em `Dates.kt`.
- **ICS / iCalendar:** **não existe** no Kalendrium: nenhuma biblioteca, nenhum código que gere ou leia `.ics`, nenhum `text/calendar`. A importação e a exportação são o backup JSON próprio (`Codec.kt`, compatível com o PWA), o compartilhamento de período como imagem e o PDF do ano. Por isso não há conformidade com a RFC 5545 a verificar.
- **Lembretes e eventos:** `Reminders.kt` usa `AlarmManager` e notificações do próprio Android; nenhuma biblioteca de agenda.

## 7. Problemas encontrados

| Id | Gravidade | Problema | Situação |
|---|---|---|---|
| C1 | Crítico | Projeto sem licença (sem `LICENSE`, cabeçalhos ou metadados): o código era "todos os direitos reservados" e não podia ser publicado como software livre nem ir para o F-Droid | Corrigido: GPL-3.0-or-later escolhida pelo autor, `LICENSE` e cabeçalhos SPDX |
| A1 | Alto | Código do desugar_jdk_libs (GPL-2.0) dentro do APK sem o texto da licença e sem o código-fonte correspondente (seção 3) | Corrigido: licença no app e código-fonte anexado pelo workflow a cada release |
| A2 | Alto | Aviso BSD do ThreeTen (JSR-310), que acompanha o `java.time` copiado para o APK, não reproduzido | Corrigido |
| A3 | Alto | Nenhum texto de licença dentro do app (Apache 2.0, OFL, GPL 2.0) e NOTICE do Kotlin/kotlinx ausentes; o app não tem internet e remetia aos repositórios | Corrigido: *Ajustes › Sobre › Licenças*, offline |
| A4 | Alto | Repositório privado: a GPL exige que quem recebe o APK tenha acesso ao código-fonte correspondente; o link do app para o código não abre para quem não é colaborador | **Pendente** (decisão do autor, ver 10.1) |
| M1 | Médio | Fonte Manrope distribuída no APK sem o texto da OFL (só em `docs/`) | Corrigido |
| M2 | Médio | Sem inventário, SBOM nem verificação de licenças: uma dependência nova poderia entrar sem revisão | Corrigido: SBOM, política com regra **exata** por artefato distribuído (sem curinga de grupo) e versão revisada |
| M3 | Médio | `release.yml` usava Actions por tag móvel (`@v4`), não por SHA | Corrigido (lógica de assinatura e publicação intacta) |
| M4 | Médio | Chave de assinatura de **teste** versionada (`ci/kalendrium-teste.jks`, senha pública), usada quando os Secrets não existem: qualquer pessoa com acesso ao repositório consegue assinar uma "atualização" aceita por cima de um APK publicado com ela | **Pendente** (documentado em `ci/README.md`; a auditoria não altera chaves, ver 10.3) |
| M5 | Médio | `release.yml` dá `contents: write` também às execuções de pull request | Pendente (repositório privado, sem PRs de forks; ver 10.4) |
| B1 | Baixo | Ícones copiados (Apache-2.0) sem aviso de modificação em cada arquivo (§4(b)) | Corrigido |
| B2 | Baixo | Créditos do app sem JetBrains Annotations e Guava ListenableFuture (dentro do APK) | Corrigido |
| B3 | Baixo | `pluginManagement` e `dependencyResolutionManagement` sem filtro de conteúdo por repositório e sem verificação de hashes (`verification-metadata.xml`) | Recomendação (10.5) |
| I1 | Informativo | JUnit 4 (EPL-1.0) só nos testes: sem conflito | Monitorado pelo CI |
| I2 | Informativo | Não existe ICS, base de feriados nem biblioteca de calendário (o pedido da auditoria previa) | Registrado |
| I3 | Informativo | Biblioteca nativa `libandroidx.graphics.path.so` (AOSP, Apache-2.0) no APK, 4 ABIs | Registrado no inventário; o CI confere o artefato |
| I4 | Informativo | Permissões do APK final: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` (até API 32), `USE_EXACT_ALARM` e a permissão interna de receiver do AndroidX Core. Sem internet, telemetria ou SDK de terceiros | Sem mudança |
| I5 | Informativo | Gradle Wrapper com `distributionSha256Sum`; repositórios só Google Maven, Maven Central e Gradle Plugin Portal | Sem mudança; wrapper conferido no CI |
| I6 | Informativo | `kotlinx-serialization-json` declarado sem o plugin de serialização: é usado só pela API de árvore `JsonElement` (`Personal.kt`, `AppController.kt`), sem `@Serializable` | Sem mudança (uso legítimo) |

## 8. Correções implementadas

| Arquivo | Mudança | Por quê |
|---|---|---|
| `LICENSE` (novo) | GPL-3.0, texto oficial | C1 |
| Todos os `.kt` (código e testes), `*.gradle.kts`, `proguard-rules.pro`, `gerar_icones.py` | Cabeçalho `Copyright (C) 2026 Juscelino Be` + `SPDX-License-Identifier: GPL-3.0-or-later` (só comentário) | C1 |
| `ui/AppIcons.kt`, `res/drawable/ic_stat_event.xml` | Só o comentário do topo: SPDX Apache-2.0, origem exata e aviso de modificação | B1 |
| `app/src/main/assets/licenca/LICENSE.txt`, `TERCEIROS.txt` (novos) | GPL do app; avisos de terceiros com NOTICE e textos originais (Apache 2.0, GPL 2.0 + CPE, ThreeTen BSD, OFL) | A1–A3, M1 |
| `ui/SettingsSheet.kt` | Em *Sobre*: seção **Licenças** com aviso de copyright e de ausência de garantia e dois botões ("Ver licença do Kalendrium", "Ver avisos e licenças de terceiros") que mostram os textos dos assets, sem internet, nos componentes que o app já usa; o rodapé antigo apontava para `docs/` | A3 |
| `ui/Credits.kt` | Acrescentados JetBrains Annotations/Guava e o aviso ThreeTen no desugar; comentário aponta os arquivos que andam juntos | B2 |
| `THIRD_PARTY_NOTICES.md` (antes `docs/CREDITOS.md`, renomeado) | Inventário completo com versões resolvidas, titulares, obrigações e modificações | M2 |
| `third_party/licenses/` (novo; a OFL veio de `docs/MANROPE-OFL.txt`) | Textos originais, cada um copiado da fonte primária (tags e commits citados acima) | A1–A3, M1 |
| `third_party/licenses/desugar_jdk_libs/SOURCE.txt` (novo) | Versão e commit do código-fonte correspondente | A1 |
| `third_party/license-policy.json` (novo) | Política revisada: regra exata e versão por artefato distribuído, escopos permitidos e seção do aviso exigida | M2 |
| `tools/license-audit/audit.init.gradle.kts`, `check_licenses.py` (novos) | Init script do CI (SBOM e versões resolvidas, **sem alterar o build do app**) e verificador (só biblioteca padrão do Python) | M2 |
| `.github/workflows/license-audit.yml` (novo) | Auditoria em PR, push com mudança de dependências e manual; só leitura; Actions por SHA; confere o código-fonte registrado do desugar | M2 |
| `.github/workflows/release.yml` | Actions fixadas por SHA (checkout v6.1.0, setup-java v5.7.0, setup-gradle v6.4.0 com `cache-provider: basic`, upload-artifact v6.0.0); no passo de publicação, anexa o código-fonte do desugar ao release | M3, A1 |
| `app/src/test/.../LicenseAssetsTest.kt` (novo) | Confere que a GPL do app é igual ao `LICENSE` e que os avisos trazem os textos originais inteiros | A3 |
| `README.md`, `ui/theme/AppTheme.kt` (comentário), `.gitignore` | Seção de licença, contribuições e como relatar problemas; caminho novo da OFL; `__pycache__` ignorado | — |

**O que não mudou:** calendário, escalas, banco de dados/armazenamento, notas, eventos, férias e afastamentos, backup, temas, navegação, ícone e identidade visual; `AndroidManifest.xml` (nenhuma permissão nova); dependências e versões; assinatura (chaves, aliases, Secrets, `ci/`). O plugin CycloneDX só é carregado pelo init script no CI.

## 9. Testes

O ambiente da auditoria não alcança o Maven Central, o Google Maven nem o servidor do Gradle (bloqueio de rede), então **nenhum comando Gradle rodou localmente**. Tudo rodou no GitHub Actions, na branch da auditoria, num job de validação temporário (removido antes do PR) que executou estes comandos e devolveu os resultados:

| Verificação | Comando | Resultado |
|---|---|---|
| Limpeza | `./gradlew clean` | Passou |
| Testes unitários | `./gradlew testDebugUnitTest` | Passou: **108 testes, 0 falhas** (105 existentes + 3 do `LicenseAssetsTest`) |
| APK de teste | `./gradlew assembleDebug` | Passou |
| APK de release (sem assinatura) | `./gradlew assembleRelease` | Passou |
| SBOM e dependências resolvidas | `./gradlew --init-script tools/license-audit/audit.init.gradle.kts :app:cyclonedxDirectBom :app:kalResolvedDependencies` e `:app:dependencies` nos 5 classpaths | Passou: 111 componentes (88 release, 1 debug, 22 testes) |
| Conferência de licenças | `python3 tools/license-audit/check_licenses.py` | Passou: 0 erros, 0 avisos |
| O verificador detecta falhas | 6 cenários simulados: JUnit no APK, artefato AndroidX novo no APK, versão nova, licença alterada, licença desconhecida, aviso ausente no app | Todos falharam como esperado (local) |
| Conteúdo do APK de release | APK do CI aberto com androguard/unzip | GPL idêntica ao `LICENSE`, `TERCEIROS.txt` com as 12 seções, 141 classes `j$.time` (desugar), nenhuma classe do `desugar_jdk_libs_configuration`, `libandroidx.graphics.path.so` em 4 ABIs; permissões iguais às de antes |
| Ícones iguais ao original | Renderização e comparação pixel a pixel com os SVG do `google/material-design-icons` | 9/9 idênticos |
| Fontes | Metadados dos 5 TTF (fontTools) e OFL contra o repositório oficial | Manrope 4.505, OFL idêntica |
| Passo de publicação do release | Trecho do `release.yml` executado localmente com `gh`/`curl` simulados | Extrai versão e commit do desugar e anexa o código ao release |
| Workflows | *Auditoria de licenças* e *Release* (build de PR com as Actions novas) no pull request | Ver as verificações do PR |
| Tela de licenças no aparelho | — | **Não executado** (sem emulador; o projeto não tem Robolectric). Ver 10.2 |
| `./gradlew connectedAndroidTest` | — | **Não executado** (exige aparelho ou emulador) |

## 10. Pendências

1. **Código-fonte para quem recebe o APK (GPL-3.0, seção 6).** O repositório é privado. Enquanto for, um APK entregue a alguém de fora (por link de Release, mensagem ou loja) precisa vir com o código-fonte correspondente ou uma oferta escrita, e o link do app para o código não abre para essa pessoa. Antes do primeiro release público: **tornar o repositório público** (o caminho mais simples, também exigido pelo F-Droid) ou anexar o código-fonte do Kalendrium a cada release. Hoje não há nenhum release publicado.
2. **Tela de licenças num aparelho.** Instalar o APK de teste do PR, ativar o modo avião, abrir *Ajustes › Sobre*, tocar em "Ver licença do Kalendrium" e "Ver avisos e licenças de terceiros" e rolar até o fim, nos três idiomas.
3. **Chave de teste no repositório** (M4). Antes do primeiro release público, cadastrar os Secrets `KALENDRIUM_*` com a chave de verdade (o workflow já prioriza os Secrets). Um APK publicado com a chave de teste só aceita atualizações assinadas com ela, que é pública. A auditoria não mexeu em chaves, aliases nem Secrets.
4. **Permissão de escrita nos PRs** (M5). Separar o `release.yml` em um job de build (`contents: read`) e um de publicação (`contents: write`, só em tag ou manual). Não foi feito para não reestruturar o pipeline de assinatura nesta auditoria.
5. **Cadeia de dependências** (B3): filtrar grupos por repositório em `settings.gradle.kts` (`content { includeGroupByRegex(...) }`) e considerar a verificação de dependências do Gradle (`gradle/verification-metadata.xml`).
6. **Primeiro release com o anexo do desugar.** O passo novo de publicação só roda em tag ou execução manual. Conferir no próximo release que o `desugar_jdk_libs-2.1.3-codigo-fonte.tar.gz` foi anexado.

## 11. Conclusão

Demonstrável com as evidências acima:
- licença definida (GPL-3.0-or-later) e aplicada;
- todas as dependências diretas e transitivas inventariadas pelas versões resolvidas;
- nenhuma licença incompatível no APK;
- avisos, NOTICE e textos de licença exigidos dentro do app, sem internet;
- código-fonte do componente GPL-2.0 disponibilizado junto com cada release;
- SBOM e verificação automática que barram dependência, versão ou licença nova sem revisão;
- projeto compilando e testes passando.

**Não é declarada conformidade integral** enquanto o repositório for privado (pendência 10.1) e a tela de licenças não tiver sido vista num aparelho (10.2). Os riscos de distribuição 10.3 e 10.4 são de segurança da assinatura, não de licença, e precisam de decisão do autor antes do primeiro release público.
