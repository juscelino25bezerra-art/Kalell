package com.kalendrium.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Interface no aparelho: troca de modo, criação de evento e recriação da Activity (C10). */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun trocaEntrePessoalEWork() {
        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Pessoal").performClick()
        rule.onNodeWithText("Kalendrium").assertIsDisplayed()
    }

    @Test fun criaEventoEMostraNoDia() {
        rule.onNodeWithText("Pessoal").performClick()
        rule.onNodeWithText("Novo evento").performClick()
        rule.onAllNodesWithText("Ex.: Dentista, aniversário, reunião...")[0].performTextInput("Teste instrumentado")
        rule.onNodeWithText("Salvar").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Teste instrumentado")[0].assertIsDisplayed()
    }

    @Test fun textoDaFolhaSobreviveARecriacao() {
        rule.onNodeWithText("Pessoal").performClick()
        rule.onNodeWithText("Novo evento").performClick()
        rule.onAllNodesWithText("Ex.: Dentista, aniversário, reunião...")[0].performTextInput("Não perder")
        rule.activityRule.scenario.recreate()
        rule.onAllNodesWithText("Não perder")[0].assertIsDisplayed()
    }
}
