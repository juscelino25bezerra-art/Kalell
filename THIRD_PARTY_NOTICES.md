# Componentes de terceiros — Kalendrium

O Kalendrium é software livre sob a **GNU GPL versão 3 ou posterior** (`GPL-3.0-or-later`, ver [`LICENSE`](LICENSE)), Copyright (C) 2026 Juscelino Be. Este arquivo lista tudo de terceiros que compõe o app, com versão, origem, licença e o que cada licença exige. Cada componente continua sob a própria licença.

- **Dentro do app:** *Ajustes › Sobre* mostra os projetos usados (lista em [`Credits.kt`](app/src/main/java/com/kalendrium/ui/Credits.kt)) e, em **Licenças**, a GPL do Kalendrium e os avisos de terceiros com os textos completos, sem internet ([`assets/licenca/`](app/src/main/assets/licenca/)).
- **Textos originais:** [`third_party/licenses/`](third_party/licenses/), copiados das fontes primárias indicadas abaixo, sem alteração.
- **Conferência automática:** o workflow [*Auditoria de licenças*](.github/workflows/license-audit.yml) gera o SBOM CycloneDX e falha se aparecer dependência ou versão nova sem revisão, licença desconhecida ou alterada, licença incompatível no APK ou aviso faltando no app. Política revisada: [`third_party/license-policy.json`](third_party/license-policy.json).
- **Relatório da auditoria:** [`docs/OPEN_SOURCE_COMPLIANCE_AUDIT.md`](docs/OPEN_SOURCE_COMPLIANCE_AUDIT.md).
- **Ao adicionar ou trocar uma dependência:** atualize este arquivo, `Credits.kt`, `assets/licenca/TERCEIROS.txt` e a política; o CI aponta o que faltar.

Versões: as **efetivamente resolvidas** pelo Gradle na versão 6.1 (`releaseRuntimeClasspath` e `coreLibraryDesugaring`), não só as declaradas em `gradle/libs.versions.toml`.

## 1. Distribuídos no APK

| Componente | Versão | Origem | Licença (SPDX) | Titular / aviso | Obrigações | Alteração pelo Kalendrium | Para que serve |
|---|---|---|---|---|---|---|---|
| **AndroidX (Jetpack)**: Core, Activity, Lifecycle, Compose (UI, Foundation, Animation, Runtime, Material 3, Material Icons Core, Ripple) e as dependências AndroidX que eles trazem (lista abaixo) | Compose BOM 2024.12.01 (Compose 1.7.6, Material 3 1.3.1); Core 1.15.0; Activity 1.9.3; Lifecycle 2.8.7 | https://github.com/androidx/androidx | Apache-2.0 | The Android Open Source Project | Cópia da licença | Nenhuma | Base do app e interface |
| ↳ `androidx.graphics:graphics-path`: biblioteca nativa `libandroidx.graphics.path.so` (arm64-v8a, armeabi-v7a, x86, x86_64) | 1.0.1 | AOSP | Apache-2.0 | The Android Open Source Project | Cópia da licença | Nenhuma | Desenho de formas no Compose |
| **Kotlin** (`kotlin-stdlib`, `-common`; `-jdk7` e `-jdk8` 1.8.20) | 2.1.0 | https://github.com/JetBrains/kotlin | Apache-2.0 (com código derivado do GWT e do Guava, Apache-2.0, e do Boost, BSL-1.0) | JetBrains s.r.o. e colaboradores; NOTICE do Kotlin | Cópia da licença; NOTICE (reproduzido); a BSL-1.0 dispensa aviso em código-objeto | Nenhuma | Linguagem |
| **kotlinx.coroutines** (`-core`, `-android`, `-bom`) | 1.7.3 | https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0 | NOTICE: *kotlinx.coroutines library. Copyright 2016-2021 JetBrains s.r.o and respective authors and developers* | Cópia da licença; **reproduzir o NOTICE** | Nenhuma | Tarefas em segundo plano |
| **kotlinx.serialization** (`-core`, `-json`, `-bom`) | 1.7.3 | https://github.com/Kotlin/kotlinx.serialization | Apache-2.0 | NOTICE: *kotlinx.serialization library. Copyright 2017-2019 JetBrains s.r.o and respective authors and developers* | Cópia da licença; **reproduzir o NOTICE** | Nenhuma | Leitura e gravação do JSON (dados e backup) |
| JetBrains Java Annotations | 23.0.0 | https://github.com/JetBrains/java-annotations | Apache-2.0 | JetBrains s.r.o. | Cópia da licença | Nenhuma | Transitiva (Kotlin) |
| Guava ListenableFuture | 1.0 | https://github.com/google/guava | Apache-2.0 | The Guava Authors | Cópia da licença | Nenhuma | Transitiva (AndroidX) |
| **desugar_jdk_libs** (código copiado para o APK pelo R8/L8: ~141 classes `j$.time`) | 2.1.3 | https://github.com/google/desugar_jdk_libs, commit `3302248e2780372a647789d714e03b02dcf559e7` | GPL-2.0-only WITH Classpath-exception-2.0 (no POM: `GPL-2.0-with-classpath-exception`) | Oracle and/or its affiliates (OpenJDK); Google | Cópia da licença; **código-fonte correspondente** junto ao binário (anexado a cada release, ver [`SOURCE.txt`](third_party/licenses/desugar_jdk_libs/SOURCE.txt)) | Nenhuma | `java.time` (datas) no Android 7 |
| ↳ Aviso do ThreeTen (JSR-310) nos arquivos do `java.time` | — | Projeto ThreeTen, via OpenJDK | BSD-3-Clause (aviso que acompanhava o original; o código segue a GPL-2.0) | Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos | **Reproduzir o aviso na documentação do binário** | Nenhuma | Parte do desugar_jdk_libs |
| **Manrope** (Regular, Medium, SemiBold, Bold, ExtraBold em `res/font`) | 4.505 | https://github.com/googlefonts/manrope | OFL-1.1 | Copyright 2018 The Manrope Project Authors (2019 nos metadados das fontes) | **Distribuir o texto da OFL com a fonte**; não vender a fonte sozinha | Nenhuma (arquivos originais) | Fonte de todo o app |
| **Material Design Icons**: Schedule, GridView, BrightnessMedium, CalendarMonth, CalendarToday, DarkMode, LightMode, Today (Outlined, em [`AppIcons.kt`](app/src/main/java/com/kalendrium/ui/AppIcons.kt)) e "event" (notificação, [`ic_stat_event.xml`](app/src/main/res/drawable/ic_stat_event.xml)) | `master` de google/material-design-icons (conferido em 13/10/2026) | https://github.com/google/material-design-icons | Apache-2.0 | Google | Cópia da licença; **aviso de modificação nos arquivos alterados** | Forma do código (objeto `AppIcons`, `by lazy`) e cor branca na notificação; desenhos idênticos aos originais (conferidos pixel a pixel), anotado no topo de cada arquivo | Ícones da interface |

`com.android.tools:desugar_jdk_libs_configuration` 2.1.3 (BSD-3-Clause) também é resolvido pelo build, mas é só a configuração do L8: nenhuma classe dele está no APK (conferido no APK de release em 13/10/2026).

### Lista completa dos componentes `androidx.*` no APK (todos Apache-2.0)

activity, activity-ktx, activity-compose (1.9.3) · annotation, annotation-jvm (1.8.1), annotation-experimental (1.4.1) · arch.core core-common, core-runtime (2.2.0) · autofill (1.0.0) · collection, collection-jvm, collection-ktx (1.4.4) · compose-bom (2024.12.01) · compose animation, animation-core, foundation, foundation-layout, material-icons-core, material-ripple, runtime, runtime-saveable, ui, ui-geometry, ui-graphics, ui-text, ui-unit, ui-util (1.7.6, com as variantes `-android`) · compose material3 (1.3.1) · concurrent-futures (1.1.0) · core, core-ktx (1.15.0) · customview-poolingcontainer (1.0.0) · emoji2 (1.3.0) · graphics-path (1.0.1) · interpolator (1.0.0) · lifecycle common, common-java8, common-jvm, livedata-core, process, runtime, runtime-compose, runtime-ktx, viewmodel, viewmodel-ktx, viewmodel-savedstate (2.8.7, com as variantes `-android`) · profileinstaller (1.3.1) · savedstate, savedstate-ktx (1.2.1) · startup-runtime (1.1.1) · tracing (1.2.0) · versionedparcelable (1.1.1).

## 2. Só no APK de teste (debug, não publicado)

`androidx.compose.ui:ui-test-manifest` 1.7.6, Apache-2.0.

## 3. Só nos testes (não distribuídos)

| Componente | Versão | Licença |
|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 (incompatível com a GPL para combinar e distribuir; aqui só roda os testes, ver o relatório) |
| Hamcrest (core, library, integration) | 1.3 | BSD-3-Clause |
| AndroidX Test (core 1.6.1, runner 1.6.2, monitor 1.7.2, ext-junit 1.2.1, annotation, services storage), Espresso 3.5.0, Compose UI Test 1.7.6, concurrent-futures-ktx 1.1.0 | — | Apache-2.0 |
| kotlinx-coroutines-test | 1.7.3 | Apache-2.0 |
| jsr305 2.0.2, javawriter 2.1.1, javax.inject 1 | — | Apache-2.0 |

## 4. Paletas de cores

As paletas usam valores de cor inspirados nestes temas, adaptados para claro e escuro ([`Palettes.kt`](app/src/main/java/com/kalendrium/ui/theme/Palettes.kt)). Nenhum arquivo desses projetos é copiado; os créditos ficam como reconhecimento. A paleta **Kalendrium** é própria.

| Paleta | Autor | Licença do projeto | Repositório |
|---|---|---|---|
| Material | Google (Material 3) | Apache 2.0 | https://github.com/material-components/material-components-android |
| Gruvbox | Pavel Pertsev (morhetz) | MIT | https://github.com/morhetz/gruvbox |
| Nord | Sven Greb (Arctic Ice Studio) | MIT | https://github.com/nordtheme/nord |
| Dracula | Dracula Theme (Zeno Rocha) | MIT | https://github.com/dracula/dracula-theme |
| Catppuccin | Catppuccin | MIT | https://github.com/catppuccin/catppuccin |
| Tokyo Night | enkia | MIT | https://github.com/tokyo-night/tokyo-night-vscode-theme |
| Solarized | Ethan Schoonover | MIT | https://github.com/altercation/solarized |
| Everforest | sainnhe | MIT | https://github.com/sainnhe/everforest |
| One Dark | Atom (GitHub) | MIT | https://github.com/atom/one-dark-syntax |
| Rosé Pine | Rosé Pine | MIT | https://github.com/rose-pine/rose-pine-theme |

## 5. Ferramentas de build (não distribuídas)

Gradle 8.13 (Apache-2.0, wrapper com `distributionSha256Sum` e conferido no CI), Android Gradle Plugin 8.13.0 (Apache-2.0), compilador Kotlin e plugin do Compose 2.1.0 (Apache-2.0) e, só na auditoria do CI, CycloneDX Gradle Plugin 3.5.1 (Apache-2.0). Usadas para compilar; não entram no APK.

## 6. Ícone e identidade do Kalendrium

O ícone do app ("Dia e noite"), o gerador em [`docs/icone/gerador/`](docs/icone/gerador/) e as imagens em `docs/icone/` são criação própria do Kalendrium, sob a GPL-3.0-or-later.

## Relatar um problema de licença

Se encontrar um componente sem atribuição, uma licença errada ou outro problema de licenciamento, abra uma *issue* no repositório com o título começando por **[licença]**, indicando o arquivo ou a dependência e a fonte da informação.
