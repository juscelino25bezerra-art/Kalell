// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.app

import com.kalell.domain.L
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalell.ui.AppController

/** Mantém o estado do app vivo durante rotações e troca de tema do sistema. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val platform = AndroidPlatform(app)
    val controller = AppController(platform, viewModelScope)

    init {
        // o Android encerrou o app com o seletor de arquivos aberto: a exportação/importação não foi concluída
        platform.onOrphanResult = {
            controller.notify(
                L(
                    "O Android fechou o app enquanto o seletor de arquivos estava aberto, e a operação não foi concluída. Nada foi importado; se era uma exportação, o arquivo criado pode estar vazio. Repita a operação.",
                    "Android closed the app while the file picker was open, so the operation didn't finish. Nothing was imported; if it was an export, the file created may be empty. Please try again.",
                    "Android cerró la app mientras el selector de archivos estaba abierto y la operación no terminó. No se importó nada; si era una exportación, el archivo creado puede estar vacío. Repita la operación.",
                ),
            )
        }
    }
}
