// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kalell.ui.KalellApp
import com.kalell.ui.theme.LocalDynamicColors
import com.kalell.ui.theme.resolveColors
import com.kalell.ui.theme.toAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val p = vm.platform
        p.launchers = Launchers(
            activity = this,
            createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { p.onPicked(it) },
            createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { p.onPicked(it) },
            open = registerForActivityResult(ActivityResultContracts.OpenDocument()) { p.onPicked(it) },
            // depois de permitir ou negar, o aviso "Notificações desativadas" se atualiza
            notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.controller.onResume() },
        )
        // relógio do resumo: acorda a cada minuto enquanto o app está na tela (e vira o dia à meia-noite)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    vm.controller.tick()
                    delay(60_000L - System.currentTimeMillis() % 60_000L)
                }
            }
        }
        setContent {
            val ctl = vm.controller
            // cores do papel de parede (Android 12+); a opção só aparece nos ajustes quando existem
            val dynamic = if (Build.VERSION.SDK_INT >= 31) {
                val ctx = this
                remember(resources.configuration.uiMode) {
                    dynamicLightColorScheme(ctx).toAppColors(false) to dynamicDarkColorScheme(ctx).toAppColors(true)
                }
            } else null
            val d = ctl.data
            val dark = resolveColors(d.palette, d.theme, isSystemInDarkTheme(), if (d.dynamicColor) dynamic else null).dark
            SideEffect {
                // ícones da barra de status/navegação seguem o modo escolhido no app, não só o do sistema
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                // "Ocultar na tela de recentes": FLAG_SECURE esconde a miniatura e bloqueia capturas
                if (d.hideRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // idioma escolhido no app: os seletores de data e hora do Material seguem a configuração local
            val lang = ctl.lang
            val baseCfg = LocalConfiguration.current
            val localized = remember(baseCfg, lang) { Configuration(baseCfg).apply { setLocale(lang.locale) } }
            val localizedCtx = remember(localized) { LocalizedContext(this, createConfigurationContext(localized).resources) }
            CompositionLocalProvider(
                LocalDynamicColors provides dynamic,
                LocalConfiguration provides localized,
                LocalContext provides localizedCtx,
            ) {
                KalellApp(ctl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // "Hoje" muda ao voltar ao app depois da meia-noite; a permissão de notificação pode ter mudado nos ajustes
        vm.controller.onResume()
    }

    override fun onDestroy() {
        if (vm.platform.launchers?.activity === this) vm.platform.launchers = null
        super.onDestroy()
    }
}

/** A Activity com os recursos no idioma do app (mantém a Activity como base para quem precisar dela). */
private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}
