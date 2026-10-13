// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import com.kalendrium.domain.L
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.kalendrium.domain.I18n
import com.kalendrium.domain.Lang
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.ui.theme.LocalDynamicColors
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.Palettes

@Composable
fun SettingsSheet(ctl: AppController, tab: String) {
    val c = Look.colors
    AppSheet(ctl::closeSheet, L("Configurações", "Settings", "Configuración")) {
        SegTabs(
            listOf("themes", "data", "lang", "about"), tab,
            label = {
                mapOf(
                    "themes" to L("Temas", "Themes", "Temas"), "data" to L("Dados", "Data", "Datos"),
                    "lang" to L("Idiomas", "Languages", "Idiomas"), "about" to L("Sobre", "About", "Acerca de"),
                ).getValue(it)
            },
            onSelect = { ctl.openSettings(it) },
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            icon = { if (it == "about") Icons.Outlined.Info else null },
        )
        when (tab) {
            "themes" -> ThemesTab(ctl)
            "data" -> DataTab(ctl)
            "lang" -> LanguageTab(ctl)
            else -> AboutTab()
        }
        SheetActions { AppButton(L("Fechar", "Close", "Cerrar"), ctl::closeSheet, kind = BtnKind.Text) }
    }
}

@Composable
private fun Choice(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Look.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) c.primaryContainer else c.surface)
            .border(2.dp, if (selected) c.primary else Color.Transparent, shape)
            .press(onClick = onClick),
    ) { content() }
}

@Composable
private fun ThemesTab(ctl: AppController) {
    val c = Look.colors
    val d = ctl.data
    Para(L("Escolha uma paleta e como ela acompanha o tema do aparelho.", "Choose a palette and how it follows the device theme.", "Elija una paleta y cómo sigue el tema del dispositivo."), Modifier.padding(top = 8.dp, bottom = 6.dp))
    SectionLabel(L("Modo", "Mode", "Modo"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("system", AppIcons.BrightnessMedium, L("Sistema", "System", "Sistema") to L("Segue o Android", "Follows Android", "Sigue a Android")),
            Triple("light", AppIcons.LightMode, L("Claro", "Light", "Claro") to L("Variante clara", "Light variant", "Variante clara")),
            Triple("dark", AppIcons.DarkMode, L("Escuro", "Dark", "Oscuro") to L("Variante escura", "Dark variant", "Variante oscura")),
        ).forEach { (key, icon, txt) ->
            val on = d.theme == key
            Choice(on, { ctl.setTheme(key) }, Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(icon, null, tint = if (on) c.onPrimaryContainer else c.onSurface, modifier = Modifier.size(20.dp))
                    Text(txt.first, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (on) c.onPrimaryContainer else c.onSurface)
                    Text(txt.second, fontSize = 9.sp, color = c.onSurfaceVar, maxLines = 1)
                }
            }
        }
    }
    SectionLabel(L("Paleta", "Palette", "Paleta"))
    if (LocalDynamicColors.current != null) {
        CheckRow(d.dynamicColor, ctl::setDynamicColor, L("Cores do papel de parede", "Wallpaper colors", "Colores del fondo de pantalla"), L("Usa as cores do sistema (Android 12+) no lugar da paleta escolhida abaixo.", "Uses the system colors (Android 12+) instead of the palette chosen below.", "Usa los colores del sistema (Android 12+) en lugar de la paleta elegida abajo."))
    }
    Palettes.preview.keys.chunked(2).forEach { pair ->
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { key ->
                val on = d.palette == key && !d.dynamicColor
                Choice(on, { ctl.setPalette(key); if (d.dynamicColor) ctl.setDynamicColor(false) }, Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PalettePreview(Palettes.preview.getValue(key))
                        Spacer(Modifier.width(10.dp))
                        Text(Palettes.names.getValue(key), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (on) c.onPrimaryContainer else c.onSurface)
                    }
                }
            }
        }
    }
}

/** Círculo da paleta com a "lua" mais escura à direita (inset shadow do PWA). */
@Composable
private fun PalettePreview(color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val r = size.minDimension / 2
        drawCircle(color, r)
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
        val shifted = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(-10.dp.toPx(), 0f), size)) }
        clipPath(circle) {
            clipPath(shifted, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .2f)) }
        }
        drawCircle(Color.White.copy(alpha = .33f), r - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
    }
}

@Composable
private fun DataTab(ctl: AppController) {
    val ready = ctl.ready
    val colors = Look.colors
    SectionLabel(L("Backup", "Backup", "Copia de seguridad"))
    Para(L("Seus dados ficam só neste aparelho. O backup leva o calendário pessoal e as escalas de trabalho juntos, para guardar ou levar para outro celular.", "Your data stays only on this device. The backup takes the personal calendar and the work schedules together, to keep or move to another phone.", "Sus datos quedan solo en este dispositivo. La copia lleva juntos el calendario personal y los horarios de trabajo, para guardarla o pasarla a otro teléfono."), Modifier.padding(bottom = 6.dp))
    Para(ctl.backupInfo())
    ButtonRow {
        AppButton(L("Exportar backup", "Export backup", "Exportar copia"), ctl::exportBackup)
        AppButton(L("Importar backup", "Import backup", "Importar copia"), ctl::importBackup)
    }
    SectionLabel(L("Escala de trabalho", "Work schedule", "Horario de trabajo"))
    Para(L("Compartilhe um intervalo da escala como imagem ou gere o calendário anual da escala em PDF.", "Share a range of the schedule as an image or create the yearly calendar as a PDF.", "Comparta un intervalo del horario como imagen o genere el calendario anual en PDF."))
    ButtonRow {
        AppButton(L("Compartilhar período", "Share period", "Compartir período"), ctl::openShare, enabled = ready)
        AppButton(L("PDF do ano", "Year PDF", "PDF del año"), { ctl.exportPdf(colors) }, enabled = ready)
    }
    if (ctl.previousAvailable) {
        SectionLabel(L("Dados anteriores", "Previous data", "Datos anteriores"))
        Para(L("Antes de importar um backup (ou de corrigir dados inválidos), o app guarda uma cópia do que havia no aparelho. Use para desfazer uma importação.", "Before importing a backup (or fixing invalid data), the app keeps a copy of what was on the device. Use it to undo an import.", "Antes de importar una copia (o de corregir datos no válidos), la app guarda una copia de lo que había en el dispositivo. Úsela para deshacer una importación."))
        ButtonRow { AppButton(L("Restaurar dados anteriores à importação", "Restore data from before the import", "Restaurar los datos anteriores a la importación"), ctl::restorePrevious) }
    }
    SectionLabel(L("Privacidade", "Privacy", "Privacidad"))
    CheckRow(
        ctl.data.hideRecents, ctl::setHideRecents, L("Ocultar na tela de recentes", "Hide in recent apps", "Ocultar en apps recientes"),
        L("Esconde o conteúdo do app na lista de apps abertos e impede capturas de tela.", "Hides the app's content in the list of open apps and blocks screenshots.", "Oculta el contenido de la app en la lista de apps abiertas e impide capturas de pantalla."),
    )
    SectionLabel(L("Restaurar", "Reset", "Restablecer"))
    ButtonRow { AppButton(L("Restaurar padrão", "Reset to default", "Restablecer predeterminado"), { ctl.resetDefault() }, kind = BtnKind.Danger, enabled = ready) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) { content() }
}

@Composable
private fun AboutTab() {
    val c = Look.colors
    Text(L("Sobre o Kalendrium", "About Kalendrium", "Acerca de Kalendrium"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
    when (I18n.lang) {
        Lang.PT -> ABOUT_PT
        Lang.EN -> ABOUT_EN
        Lang.ES -> ABOUT_ES
    }.forEach { Para(it, Modifier.padding(bottom = 12.dp), size = 15.sp) }
    Text(L("Desenvolvido por: Juscelino Be.\nFeita para privacidade ❤️", "Developed by: Juscelino Be.\nMade for privacy ❤️", "Desarrollado por: Juscelino Be.\nHecha para la privacidad ❤️"), fontWeight = FontWeight.Bold, color = c.onSurface, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 6.dp))
    CreditsSection()
}

/** Ajustes › Sobre: tudo de terceiros que compõe o Kalendrium, com licença e link (lista em Credits.kt). */
@Composable
private fun CreditsSection() {
    val c = Look.colors
    val uri = LocalUriHandler.current
    Text(L("Projetos usados no Kalendrium", "Projects used in Kalendrium", "Proyectos usados en Kalendrium"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
    Para(L(
        "O Kalendrium é feito sobre projetos de código aberto. Toque em um item para abrir o repositório no navegador; o app em si continua sem acesso à internet.",
        "Kalendrium is built on open source projects. Tap an item to open its repository in the browser; the app itself still has no internet access.",
        "Kalendrium está hecho sobre proyectos de código abierto. Toque un elemento para abrir el repositorio en el navegador; la app en sí sigue sin acceso a internet.",
    ), Modifier.padding(bottom = 4.dp))
    Credits.groups.forEach { g ->
        SectionLabel(g.title)
        if (g.note != null) Para(g.note, Modifier.padding(bottom = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            g.items.forEach { item ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                        // sem navegador instalado, o link só não abre (o endereço continua visível)
                        .press(role = Role.Button) { runCatching { uri.openUri(item.url) } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface)
                        Text(item.license, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                    Text(item.use, fontSize = 12.sp, color = c.onSurfaceVar, lineHeight = 16.sp)
                    Text(item.url.removePrefix("https://"), fontSize = 11.sp, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    LicensesSection()
}

/**
 * Ajustes › Sobre › Licenças: licença do Kalendrium e avisos de terceiros, lidos dos assets (sem internet).
 * Textos em app/src/main/assets/licenca/ (fonte: THIRD_PARTY_NOTICES.md e third_party/licenses/).
 */
@Composable
private fun LicensesSection() {
    val c = Look.colors
    Text(L("Licenças", "Licenses", "Licencias"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
    Para(L(
        "Kalendrium — Copyright (C) 2026 Juscelino Be. Software livre sob a GNU GPL versão 3 ou posterior (GPL-3.0-or-later): você pode usar, estudar, compartilhar e modificar o app nos termos dessa licença. Ele é distribuído SEM NENHUMA GARANTIA. Cada componente de terceiros continua sob a própria licença; os avisos e textos completos estão abaixo e funcionam sem internet.",
        "Kalendrium — Copyright (C) 2026 Juscelino Be. Free software under the GNU GPL version 3 or later (GPL-3.0-or-later): you may use, study, share and modify the app under the terms of that license. It comes WITHOUT ANY WARRANTY. Each third-party component stays under its own license; the notices and full texts are below and work offline.",
        "Kalendrium — Copyright (C) 2026 Juscelino Be. Software libre bajo la GNU GPL versión 3 o posterior (GPL-3.0-or-later): puede usar, estudiar, compartir y modificar la app según esa licencia. Se distribuye SIN NINGUNA GARANTÍA. Cada componente de terceros mantiene su propia licencia; los avisos y textos completos están abajo y funcionan sin internet.",
    ), Modifier.padding(bottom = 4.dp))
    var gpl by rememberSaveable { mutableStateOf(false) }
    var third by rememberSaveable { mutableStateOf(false) }
    ButtonRow {
        AppButton(if (gpl) L("Ocultar licença do Kalendrium", "Hide Kalendrium license", "Ocultar licencia de Kalendrium") else L("Ver licença do Kalendrium (GPL 3.0)", "View Kalendrium license (GPL 3.0)", "Ver licencia de Kalendrium (GPL 3.0)"), { gpl = !gpl })
    }
    if (gpl) LicenseText("licenca/LICENSE.txt")
    ButtonRow {
        AppButton(if (third) L("Ocultar avisos de terceiros", "Hide third-party notices", "Ocultar avisos de terceros") else L("Ver avisos e licenças de terceiros", "View third-party notices and licenses", "Ver avisos y licencias de terceros"), { third = !third })
    }
    if (third) LicenseText("licenca/TERCEIROS.txt")
}

/** Texto de licença dos assets, em blocos (parágrafos) para não montar um único Text enorme. */
@Composable
private fun LicenseText(asset: String) {
    val c = Look.colors
    val context = LocalContext.current
    val blocks = remember(asset) {
        runCatching { context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }
            .getOrDefault("").replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).map { it.trimEnd() }.filter { it.isNotBlank() }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (blocks.isEmpty()) Para(L("Texto indisponível.", "Text unavailable.", "Texto no disponible."))
        blocks.forEach { Text(it, fontSize = 11.sp, lineHeight = 15.sp, color = c.onSurface, fontFamily = FontFamily.Monospace) }
    }
}

private val ABOUT_PT = listOf(
    "O Kalendrium nasceu de uma necessidade pessoal: organizar escalas de trabalho de forma simples, flexível e personalizada.",
    "Depois de testar diferentes aplicativos, gratuitos, open source e pagos, percebi que nenhum atendia completamente ao que eu precisava, principalmente na criação e combinação de diferentes ciclos de trabalho.",
    "A privacidade também foi uma prioridade. O aplicativo não exige conta ou cadastro, não acessa a internet e suas escalas, eventos, anotações e configurações ficam armazenados no dispositivo.",
    "O backup automático do Android pode levar uma cópia desses dados para a sua conta Google, mas só quando o aparelho oferece criptografia de ponta a ponta (protegida pelo bloqueio de tela). Também é possível exportar o backup manualmente em Ajustes › Dados.",
    "Com o Kalendrium, é possível combinar diferentes ciclos, criar escalas personalizadas e acompanhar facilmente os dias de trabalho e folga pelo calendário.",
    "Criado para resolver uma necessidade real e continuar evoluindo.",
)

private val ABOUT_EN = listOf(
    "Kalendrium was born from a personal need: organizing work schedules in a simple, flexible and personal way.",
    "After trying different apps, free, open source and paid, I found that none fully met my needs, especially for creating and combining different work cycles.",
    "Privacy was also a priority. The app needs no account or sign-up, doesn't access the internet, and your schedules, events, notes and settings stay stored on the device.",
    "Android's automatic backup can copy this data to your Google account, but only when the device offers end-to-end encryption (protected by the screen lock). You can also export the backup manually in Settings › Data.",
    "With Kalendrium you can combine different cycles, create custom schedules and easily follow work days and days off on the calendar.",
    "Made to solve a real need and to keep evolving.",
)

private val ABOUT_ES = listOf(
    "Kalendrium nació de una necesidad personal: organizar horarios de trabajo de forma sencilla, flexible y personalizada.",
    "Después de probar distintas aplicaciones, gratuitas, de código abierto y de pago, vi que ninguna cubría del todo lo que necesitaba, sobre todo para crear y combinar distintos ciclos de trabajo.",
    "La privacidad también fue una prioridad. La aplicación no exige cuenta ni registro, no accede a internet y sus horarios, eventos, notas y ajustes se guardan en el dispositivo.",
    "La copia automática de Android puede llevar estos datos a su cuenta de Google, pero solo cuando el dispositivo ofrece cifrado de extremo a extremo (protegido por el bloqueo de pantalla). También puede exportar la copia manualmente en Ajustes › Datos.",
    "Con Kalendrium puede combinar distintos ciclos, crear horarios personalizados y seguir fácilmente los días de trabajo y libres en el calendario.",
    "Creada para resolver una necesidad real y seguir evolucionando.",
)

/** Ajustes › Idiomas: segue o aparelho ou fixa português, inglês ou espanhol. */
@Composable
private fun LanguageTab(ctl: AppController) {
    val c = Look.colors
    val system = ctl.systemLang
    Para(
        L(
            "Escolha o idioma do app. Em \"Sistema\", o Kalendrium segue o idioma do aparelho; se ele não for português nem espanhol, usa inglês.",
            "Choose the app language. With \"System\", Kalendrium follows the device language; if it isn't Portuguese or Spanish, it uses English.",
            "Elija el idioma de la app. Con \"Sistema\", Kalendrium sigue el idioma del dispositivo; si no es portugués ni español, usa inglés.",
        ),
        Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
    val options = listOf(I18n.SYSTEM to (L("Sistema", "System", "Sistema") to L("Idioma do aparelho", "Device language", "Idioma del dispositivo") + ": ${system.nativeName}")) +
        Lang.entries.map { it.code to (it.nativeName to L("Usar sempre este idioma", "Always use this language", "Usar siempre este idioma")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, txt) ->
            val on = ctl.langPref == key
            Choice(on, { ctl.setLanguage(key) }, Modifier.fillMaxWidth().semantics { selected = on; role = Role.RadioButton }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(txt.first, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (on) c.onPrimaryContainer else c.onSurface)
                        Text(txt.second, fontSize = 12.sp, color = c.onSurfaceVar)
                    }
                    if (on) Icon(Icons.Filled.Check, null, tint = c.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
