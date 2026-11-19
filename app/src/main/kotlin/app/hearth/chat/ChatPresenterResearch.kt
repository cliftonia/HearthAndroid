package app.hearth.chat

import android.util.Log
import app.hearth.domain.ChatModel
import app.hearth.domain.EffortChoiceKind
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ResearchCitationTransformer
import app.hearth.domain.ResearchInteractor
import app.hearth.domain.ResearchStepKind
import app.hearth.domain.ServerSettings
import app.hearth.domain.startingNewVersion
import app.hearth.domain.titleAfterSending
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

// Deep research: switching it on, running it, and keeping the report it writes.

// Choosing

/** Whether what is sent next is researched. Research needs a search server, and gives way to picture mode. */
fun ChatViewModel.isResearchChosen(settings: ServerSettings): Boolean = isResearchMode && settings.searchUrl != null && !isPictureMode

/** What the next message will be given: research, or thought at the level saved in the settings. */
fun ChatViewModel.effortChoice(settings: ServerSettings): EffortChoiceKind =
    if (isResearchChosen(settings)) EffortChoiceKind.Research else EffortChoiceKind.Thinking(settings.thinkingLevel)

/** The thinking levels the effort menu offers for the chosen model; a single one for a model that cannot think. */
fun ChatViewModel.effortChoices(settings: ServerSettings): List<EffortChoiceKind> =
    EffortChoiceKind.choices(canThink = selectedModel?.canThink == true, savedLevel = settings.thinkingLevel)

/** Whether what the open chat sends next is researched. */
internal val ChatPresenter.isResearchChosen: Boolean
    get() = state.value.isResearchChosen(dependencies.settingsStore.settings.value)

/**
 * Chooses how hard the model thinks, for every chat. Research, if it is switched on, stays on: its report is thought
 * about at the level chosen here.
 */
fun ChatPresenter.selectEffortAction(choice: EffortChoiceKind) {
    when (choice) {
        is EffortChoiceKind.Thinking -> if (choice.level != dependencies.settingsStore.settings.value.thinkingLevel) thinkingLevelAction(choice.level)
        EffortChoiceKind.Research -> setResearch(true)
    }
}

/** Switches research on or off for this chat. It needs a search address, and takes the place of picture mode. */
fun ChatPresenter.toggleResearchAction() = setResearch(!isResearchChosen)

private fun ChatPresenter.setResearch(isOn: Boolean) {
    val isResearching = isOn && dependencies.settingsStore.settings.value.searchUrl != null
    // A message asks for a picture or for research, never both.
    state.update { it.copy(isResearchMode = isResearching, isPictureMode = it.isPictureMode && !isResearching) }
}

// Running

/** Adds the user's question and answers it with a researched report. */
internal suspend fun ChatPresenter.submitResearch(message: Message) {
    val model = state.value.selectedModel
    if (model == null) {
        state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.NO_MODEL)) }
        return
    }
    val conversation = state.value.conversation
    val isFirstMessage = conversation.messages.isEmpty()
    state.update {
        it.copy(
            conversation = conversation.copy(
                title = conversation.titleAfterSending(message.text),
                modelId = model.id,
                messages = conversation.messages + message,
            ),
        )
    }
    research(model)
    if (isFirstMessage) nameConversation(conversation.id, message.text, model)
    suggestFollowUps(model)
}

/**
 * Researches the last message of the conversation and fills a new reply with the report. Returns once the run is
 * over, whether it finished or failed, with what there is saved; being stopped saves it too, on the way out.
 *
 * [previous] is the report this one is another attempt at, whose versions it keeps behind it.
 */
internal suspend fun ChatPresenter.research(model: ChatModel, previous: Message? = null) {
    val settings = dependencies.settingsStore.settings.value
    val conversation = state.value.conversation
    val asked = conversation.messages.lastOrNull()?.takeIf { it.role == MessageRole.USER } ?: return
    var report = (previous?.startingNewVersion(now()) ?: Message(role = MessageRole.ASSISTANT, text = "", createdAt = now())).copy(isResearch = true)
    var thinkingSince: Long? = null
    var isFinished = false
    state.update {
        it.copy(
            conversation = conversation.copy(messages = conversation.messages + report),
            isStreaming = true,
            researchStep = ResearchStepKind.Planning,
            searchingFor = null,
            hasSearchFailed = false,
            followUps = emptyList(),
            memoryNotice = null,
            error = null,
        )
    }
    stopSpeaking()
    val request = ResearchInteractor.Request(question = asked.text, context = conversation.messages.dropLast(1), model = model, settings = settings)
    try {
        dependencies.research.run(request).collect { event ->
            when (event) {
                is ResearchInteractor.Event.Step -> state.update { it.copy(researchStep = event.step) }
                is ResearchInteractor.Event.Sources -> report = report.copy(sources = event.sources)
                is ResearchInteractor.Event.Thinking -> {
                    thinkingSince = thinkingSince ?: now()
                    report = report.copy(thinking = report.thinking + event.text)
                }
                is ResearchInteractor.Event.Text -> {
                    val since = thinkingSince
                    if (since != null && report.thinkingSeconds == null) {
                        report = report.copy(thinkingSeconds = ((now() - since) / 1000).toInt())
                    }
                    report = report.copy(text = report.text + event.text)
                }
                is ResearchInteractor.Event.Finished -> {
                    report = report.copy(text = event.report.text, sources = event.report.sources)
                    isFinished = true
                }
            }
            if (event !is ResearchInteractor.Event.Step) state.update { it.copy(conversation = it.conversation.replacing(report)) }
        }
    } catch (failure: CancellationException) {
        // Stopped by the user; what has been written so far is kept below.
        throw failure
    } catch (failure: Exception) {
        // Only the kind of failure is logged: its words may carry an address or what was searched for.
        Log.e(ChatPresenter.TAG, "Research failed: ${failure.javaClass.simpleName}")
        state.update { it.copy(error = ErrorViewConfiguration.from(failure)) }
    } finally {
        // Whatever ended the run, what arrived is kept and written down, even when the turn was cancelled.
        withContext(NonCancellable) {
            if (!isFinished) {
                report = tidiedUnfinishedReport(report)
                state.update { it.copy(conversation = it.conversation.replacing(report)) }
            }
            finish(conversation.id, report)
            announceReplyFinished(report.id)
        }
    }
}

/**
 * A report that was stopped or failed part of the way through is kept as far as it got, with its citations and
 * its sources made to agree just as a finished one's are. One with no words yet keeps no sources either.
 */
private fun tidiedUnfinishedReport(report: Message): Message {
    val tidied = ResearchCitationTransformer().tidy(report.text, report.sources)
    return report.copy(text = tidied.text, sources = if (tidied.text.isEmpty()) emptyList() else tidied.sources)
}
