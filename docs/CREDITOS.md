# Créditos e licenças

Tudo de terceiros que compõe o Kalendrium. A mesma lista aparece no app em **Ajustes › Sobre** (código em `app/src/main/java/com/kalendrium/ui/Credits.kt`); ao adicionar ou trocar uma dependência, atualize os dois.

O Kalendrium não pede permissão de internet. Os links do app abrem no navegador do aparelho.

## Bibliotecas dentro do app

Versões resolvidas no build release (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`).

| Projeto | Para que serve | Versão | Licença | Repositório |
|---|---|---|---|---|
| Kotlin (stdlib) | Linguagem e biblioteca padrão | 2.1.0 | Apache 2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.coroutines | Tarefas em segundo plano | 1.7.3 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization (JSON) | Leitura e gravação dos dados e do backup | 1.7.3 | Apache 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Jetpack Compose (BOM 2024.12.01): UI, Foundation, Animation, Runtime, Material 3, Material Icons Core | Interface | UI 1.7.6, Material 3 1.3.1 | Apache 2.0 | https://github.com/androidx/androidx |
| AndroidX Core KTX, Activity Compose, Lifecycle (e dependências AndroidX que eles trazem) | Base do app Android | Core 1.15.0, Activity 1.9.3, Lifecycle 2.8.7 | Apache 2.0 | https://github.com/androidx/androidx |
| desugar_jdk_libs | `java.time` (datas) no Android 7 | 2.1.3 | GPL 2.0 + Classpath Exception | https://github.com/google/desugar_jdk_libs |

Dependências indiretas também no APK: `com.google.guava:listenablefuture` 1.0 (Apache 2.0, https://github.com/google/guava) e `org.jetbrains:annotations` 23.0.0 (Apache 2.0, https://github.com/JetBrains/java-annotations).

## Fonte e ícones

| Projeto | Uso | Licença | Repositório |
|---|---|---|---|
| Manrope | Fonte de todo o app, embutida em `res/font` | SIL OFL 1.1 (texto em `docs/MANROPE-OFL.txt`) | https://github.com/googlefonts/manrope |
| Material Design Icons | Ícones (pacote core + alguns copiados do Extended em `ui/AppIcons.kt`) | Apache 2.0 | https://github.com/google/material-design-icons |

## Paletas de cores

As paletas usam as cores destes temas, adaptadas para variante clara e escura (`ui/theme/Palettes.kt`). A paleta **Kalendrium** é própria.

| Paleta | Autor | Licença | Repositório |
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

## Ferramentas de desenvolvimento (não vão no app)

| Projeto | Uso | Versão | Licença | Repositório |
|---|---|---|---|---|
| Gradle | Compilação | 8.13 | Apache 2.0 | https://github.com/gradle/gradle |
| Android Gradle Plugin | Compilação do APK | 8.13.0 | Apache 2.0 | https://android.googlesource.com/platform/tools/base |
| Kotlin Gradle Plugin + Compose Compiler | Compilação | 2.1.0 | Apache 2.0 | https://github.com/JetBrains/kotlin |
| JUnit 4 | Testes | 4.13.2 | EPL 1.0 | https://github.com/junit-team/junit4 |
| AndroidX Test (ext-junit, runner) e Compose UI Test | Testes no aparelho | 1.2.1 / 1.6.2 | Apache 2.0 | https://github.com/android/android-test |
