package tea.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `content/chapters/chapters.<lang>.json`: what Home lists, in file order (docs/challenge-boardroom-crisis.md §3.1). */
@Serializable
data class ChapterCatalog(val chapters: List<Chapter> = emptyList())

@Serializable
data class Chapter(
    val id: String,
    val kind: ChapterKind,
    /** Lesson id or challenge id; null while the chapter is not built. */
    val target: String? = null,
    val persona: Persona,
    val title: String,
    val blurb: String,
    val minutes: Int = 10,
    val status: ChapterStatus = ChapterStatus.AVAILABLE,
    /** Challenge id of a short demo of this chapter (about a minute); Home offers it as a second button. */
    val demoTarget: String? = null,
)

@Serializable
enum class ChapterKind {
    @SerialName("lesson") LESSON,
    @SerialName("challenge") CHALLENGE,
}

@Serializable
enum class ChapterStatus {
    @SerialName("available") AVAILABLE,
    @SerialName("soon") SOON,
}
