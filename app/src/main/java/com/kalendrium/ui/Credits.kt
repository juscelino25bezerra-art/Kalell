// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import com.kalendrium.domain.L

/**
 * Projetos de terceiros usados no Kalendrium, mostrados em Ajustes › Sobre.
 * Manter igual a THIRD_PARTY_NOTICES.md e a app/src/main/assets/licenca/TERCEIROS.txt: ao adicionar ou trocar
 * uma dependência, atualize os três (o CI de licenças aponta o que faltar).
 */
data class Credit(val name: String, val use: String, val license: String, val url: String)

data class CreditGroup(val title: String, val note: String?, val items: List<Credit>)

object Credits {
    private const val APACHE = "Apache 2.0"
    private const val MIT = "MIT"

    val groups: List<CreditGroup> get() = listOf(
        CreditGroup(
            L("Bibliotecas dentro do app", "Libraries inside the app", "Bibliotecas dentro de la app"),
            null,
            listOf(
                Credit("Kotlin", L("Linguagem e biblioteca padrão", "Language and standard library", "Lenguaje y biblioteca estándar"), APACHE, "https://github.com/JetBrains/kotlin"),
                Credit("kotlinx.coroutines", L("Tarefas em segundo plano", "Background work", "Tareas en segundo plano"), APACHE, "https://github.com/Kotlin/kotlinx.coroutines"),
                Credit("kotlinx.serialization", L("Leitura e gravação do JSON (dados e backup)", "Reading and writing JSON (data and backup)", "Lectura y escritura del JSON (datos y copia)"), APACHE, "https://github.com/Kotlin/kotlinx.serialization"),
                Credit("Jetpack Compose", L("Interface: UI, Foundation, Material 3 e Material Icons", "Interface: UI, Foundation, Material 3 and Material Icons", "Interfaz: UI, Foundation, Material 3 y Material Icons"), APACHE, "https://github.com/androidx/androidx"),
                Credit("AndroidX Core, Activity e Lifecycle", L("Base do app Android", "Android app foundation", "Base de la app Android"), APACHE, "https://github.com/androidx/androidx"),
                Credit("JetBrains Annotations e Guava ListenableFuture", L("Dependências internas do Kotlin e do AndroidX", "Internal dependencies of Kotlin and AndroidX", "Dependencias internas de Kotlin y AndroidX"), APACHE, "https://github.com/google/guava"),
                Credit("desugar_jdk_libs", L("Datas (java.time) no Android 7; inclui o aviso BSD do ThreeTen", "Dates (java.time) on Android 7; includes the ThreeTen BSD notice", "Fechas (java.time) en Android 7; incluye el aviso BSD de ThreeTen"), "GPL 2.0 + Classpath Exception", "https://github.com/google/desugar_jdk_libs"),
            ),
        ),
        CreditGroup(
            L("Fonte e ícones", "Font and icons", "Fuente e iconos"),
            null,
            listOf(
                Credit("Manrope", L("Fonte de todo o app, embutida no APK", "Font of the whole app, bundled in the APK", "Fuente de toda la app, incluida en el APK"), "SIL OFL 1.1", "https://github.com/googlefonts/manrope"),
                Credit("Material Design Icons", L("Ícones (alguns copiados do pacote Extended)", "Icons (some copied from the Extended set)", "Iconos (algunos copiados del paquete Extended)"), APACHE, "https://github.com/google/material-design-icons"),
            ),
        ),
        CreditGroup(
            L("Paletas de cores", "Color palettes", "Paletas de colores"),
            L(
                "As paletas usam as cores destes temas, adaptadas para claro e escuro. A paleta Kalendrium é própria.",
                "The palettes use the colors of these themes, adapted for light and dark. The Kalendrium palette is original.",
                "Las paletas usan los colores de estos temas, adaptados a claro y oscuro. La paleta Kalendrium es propia.",
            ),
            listOf(
                Credit("Material 3", L("Paleta Material", "Material palette", "Paleta Material"), APACHE, "https://github.com/material-components/material-components-android"),
                Credit("Gruvbox", "Pavel Pertsev (morhetz)", MIT, "https://github.com/morhetz/gruvbox"),
                Credit("Nord", "Arctic Ice Studio / Sven Greb", MIT, "https://github.com/nordtheme/nord"),
                Credit("Dracula", "Zeno Rocha", MIT, "https://github.com/dracula/dracula-theme"),
                Credit("Catppuccin", "Catppuccin", MIT, "https://github.com/catppuccin/catppuccin"),
                Credit("Tokyo Night", "enkia", MIT, "https://github.com/tokyo-night/tokyo-night-vscode-theme"),
                Credit("Solarized", "Ethan Schoonover", MIT, "https://github.com/altercation/solarized"),
                Credit("Everforest", "sainnhe", MIT, "https://github.com/sainnhe/everforest"),
                Credit("One Dark", "Atom", MIT, "https://github.com/atom/one-dark-syntax"),
                Credit("Rosé Pine", "Rosé Pine", MIT, "https://github.com/rose-pine/rose-pine-theme"),
            ),
        ),
        CreditGroup(
            L("Ferramentas de desenvolvimento", "Development tools", "Herramientas de desarrollo"),
            L("Usadas só para compilar e testar; não vão dentro do app.", "Used only to build and test; not included in the app.", "Usadas solo para compilar y probar; no van dentro de la app."),
            listOf(
                Credit("Gradle", L("Compilação", "Build", "Compilación"), APACHE, "https://github.com/gradle/gradle"),
                Credit("Android Gradle Plugin", L("Compilação do APK", "APK build", "Compilación del APK"), APACHE, "https://android.googlesource.com/platform/tools/base"),
                Credit("JUnit 4", L("Testes", "Tests", "Pruebas"), "EPL 1.0", "https://github.com/junit-team/junit4"),
                Credit("AndroidX Test", L("Testes no aparelho", "On-device tests", "Pruebas en el dispositivo"), APACHE, "https://github.com/android/android-test"),
            ),
        ),
    )
}
