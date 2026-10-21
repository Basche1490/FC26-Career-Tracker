package com.fctracker.career.ai

import com.fctracker.career.AppState
import com.fctracker.career.Note
import com.fctracker.career.logic.CareerOps

/** One message in the assistant's conversation. */
data class ChatTurn(val role: String, val text: String, val local: Boolean = false)

/**
 * The assistant's flow: check the question on the phone, answer it there when it is not about the career, otherwise
 * build the request (rules + everything the app holds + the conversation) and check the agent's answer again.
 */
object Assistant {

    const val MODE_CHAT = "chat"
    const val MODE_PRESS = "press"
    const val MODE_PLAYER = "player"

    sealed class Step {
        /** Answered by the app itself; nothing is sent. */
        data class Local(val reply: String) : Step()
        /** Send this to the AI agent. */
        data class Send(val system: String, val prompt: String, val history: List<AiClient.Turn>) : Step()
    }

    /** At most this many earlier turns are sent with a question. */
    const val HISTORY_TURNS = 12

    fun prepare(state: AppState, notes: List<Note>, message: String, mode: String, history: List<ChatTurn>): Step {
        val names = AiContext.names(state)
        // A short follow-up is fine when the conversation so far was about the career.
        val lastUser = history.lastOrNull { it.role == "user" && !it.local }
        val followUp = lastUser != null
        val check = AiGuard.check(message, names, followUp = followUp, rolePlay = mode != MODE_CHAT)
        val nextOpponent = CareerOps.nextFixture(state)?.opponent
        return when (check.verdict) {
            AiGuard.Verdict.EMPTY -> Step.Local("Ask me anything about your career.")
            AiGuard.Verdict.GREETING -> Step.Local(AiGuard.greeting(state.clubName))
            AiGuard.Verdict.OFF_TOPIC, AiGuard.Verdict.INJECTION -> Step.Local(AiGuard.refusal(state.clubName, nextOpponent))
            AiGuard.Verdict.ALLOW -> {
                val system = AiContext.systemPrompt(state, mode) + "\n\n=== DATA (everything FC Tracker holds for this career) ===\n" + AiContext.build(state, notes)
                // Only turns that really went to the agent are part of its conversation; the app's own refusals are not.
                val turns = history.filter { !it.local }.takeLast(HISTORY_TURNS).map { AiClient.Turn(it.role, it.text) }
                Step.Send(system, message.take(AiGuard.MAX_QUESTION_LENGTH), turns)
            }
        }
    }

    /** The agent's answer, or the app's refusal when the agent said the question was not about the career. */
    fun finish(state: AppState, reply: String): String =
        if (AiGuard.isOffTopicReply(reply)) AiGuard.refusal(state.clubName, CareerOps.nextFixture(state)?.opponent) else reply.trim()
}
