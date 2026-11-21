package tea.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.time.Instant
import tea.model.GotchaResponse
import tea.model.StepKey
import tea.ui.screens.GotchaContent
import tea.ui.theme.TeaTheme

@OptIn(ExperimentalTestApi::class)
class GotchaContentTest {
    private val response = GotchaResponse(
        sendId = "gallery-send",
        lessonId = "email-01",
        templateId = "gallery-sample",
        clickedAt = Instant.parse("2026-11-21T10:00:00Z"),
        subject = "A quick check about your account",
        text = "Please review the practice page.",
        steps = mapOf(
            StepKey.WHO to "Check the real sender.",
            StepKey.EMOTION to "Notice urgency.",
            StepKey.ASK to "Identify the request.",
            StepKey.VERIFY to "Use a route you control.",
        ),
        hints = listOf("A familiar name is not proof."),
    )

    @Test
    fun recognitionBranchShowsClickFactsWithoutStepReveal() = runComposeUiTest {
        setContent {
            TeaTheme(dark = false) {
                GotchaContent(
                    data = response,
                    clickedWhen = "yesterday evening",
                    answer = "yes",
                    scrollEnabled = false,
                    animateTransition = false,
                )
            }
        }

        onNodeWithText("It was clicked yesterday evening.").assert(hasText("It was clicked yesterday evening."))
        onNodeWithText("The subject was: A quick check about your account.").assert(hasText("The subject was: A quick check about your account."))
    }

    @Test
    fun newMessageBranchShowsReasonAndFourStepInformation() = runComposeUiTest {
        setContent {
            var answer by remember { mutableStateOf<String?>(null) }
            var reason by remember { mutableStateOf<String?>(null) }
            TeaTheme(dark = true) {
                GotchaContent(
                    data = response,
                    clickedWhen = "yesterday evening",
                    answer = answer,
                    reason = reason,
                    onAnswer = { answer = it },
                    onReason = { reason = it },
                    scrollEnabled = false,
                    animateTransition = false,
                )
            }
        }

        onNodeWithText("No, this one was new to me").performClick()
        onNodeWithText("Who is really writing?").performClick()
        onNodeWithText("Your four checks").assert(hasText("Your four checks"))
        onNodeWithText("Check the real sender.").assert(hasText("Check the real sender."))
        onNodeWithText("A clue to keep").assert(hasText("A clue to keep"))
        onNodeWithText("Continue to lesson").assert(hasText("Continue to lesson"))
    }
}
