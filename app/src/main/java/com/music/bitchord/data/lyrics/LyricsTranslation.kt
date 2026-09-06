package com.music.bitchord.data.lyrics

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.BreakIterator
import java.util.Locale

enum class LyricsTranslationStage { IDENTIFYING, DOWNLOADING_MODEL, TRANSLATING }

sealed interface LyricsTranslationState {
    data object Idle : LyricsTranslationState
    data class Loading(val targetLanguageTag: String, val stage: LyricsTranslationStage) : LyricsTranslationState
    data class Ready(
        val targetLanguageTag: String,
        val sourceLanguageTag: String,
        val lines: List<LyricLine>,
    ) : LyricsTranslationState
    data class AlreadyInTargetLanguage(val targetLanguageTag: String) : LyricsTranslationState
    data class Unavailable(val targetLanguageTag: String) : LyricsTranslationState
}

object LyricsTranslation {
    suspend fun translate(
        lines: List<LyricLine>,
        targetLanguageTag: String,
        onStage: (LyricsTranslationStage) -> Unit,
    ): LyricsTranslationState {
        val target = supportedTag(targetLanguageTag)
            ?: return LyricsTranslationState.Unavailable(targetLanguageTag)
        val sample = lines.asSequence()
            .flatMap { sequenceOf(it.text, it.background?.text.orEmpty()) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(4000)
        if (sample.isBlank()) return LyricsTranslationState.Unavailable(target)

        onStage(LyricsTranslationStage.IDENTIFYING)
        val identifier = LanguageIdentification.getClient()
        val source = try {
            supportedTag(identifier.identifyLanguage(sample).await())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            identifier.close()
        } ?: return LyricsTranslationState.Unavailable(target)
        if (source == target) return LyricsTranslationState.AlreadyInTargetLanguage(target)

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        return try {
            onStage(LyricsTranslationStage.DOWNLOADING_MODEL)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            onStage(LyricsTranslationStage.TRANSLATING)
            val texts = lines.asSequence()
                .filterNot { it.isGap }
                .flatMap { sequenceOf(it.text, it.background?.text) }
                .filterNotNull()
                .filter { it.isNotBlank() }
                .distinct()
                .associateWith { translator.translate(it).await() }
            LyricsTranslationState.Ready(
                target,
                source,
                lines.map { line ->
                    if (line.isGap) line else {
                        val translated = texts[line.text]?.trim().orEmpty().ifBlank { line.text }
                        val background = line.background?.let { backing ->
                            backing.retimedForTranslation(
                                texts[backing.text]?.trim().orEmpty().ifBlank { backing.text },
                            )
                        }
                        line.retimedForTranslation(translated, background)
                    }
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LyricsTranslationState.Unavailable(target)
        } finally {
            translator.close()
        }
    }

    private fun supportedTag(tag: String): String? {
        TranslateLanguage.fromLanguageTag(tag)?.let { return it }
        val base = Locale.forLanguageTag(tag).language.takeIf { it.isNotBlank() } ?: return null
        return TranslateLanguage.fromLanguageTag(base)
    }
}

internal fun LyricLine.retimedForTranslation(
    translatedText: String,
    translatedBackground: LyricLine? = background,
): LyricLine {
    val clean = translatedText.trim()
    if (clean.isEmpty()) return copy(background = translatedBackground)
    val translatedWords = if (words.isEmpty()) emptyList() else {
        translationTokenRanges(clean).map { range ->
            val denominator = clean.length.coerceAtLeast(1).toFloat()
            LyricWord(
                timeAtTextFraction(range.first / denominator),
                timeAtTextFraction((range.last + 1) / denominator),
                clean.substring(range),
            )
        }
    }
    return copy(text = clean, words = translatedWords, background = translatedBackground)
}

private fun translationTokenRanges(text: String): List<IntRange> {
    val words = Regex("\\S+").findAll(text).map { it.range }.toList()
    if (words.size != 1 || text.any(Char::isWhitespace)) return words
    val breaker = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val graphemes = mutableListOf<IntRange>()
    var start = breaker.first()
    var end = breaker.next()
    while (end != BreakIterator.DONE) {
        if (text.substring(start, end).isNotBlank()) graphemes += start until end
        start = end
        end = breaker.next()
    }
    return graphemes.ifEmpty { words }
}

private fun LyricLine.timeAtTextFraction(fraction: Float): Long {
    val target = fraction.coerceIn(0f, 1f) * text.length.coerceAtLeast(1)
    val first = words.first()
    val last = words.last()
    if (target <= 0f) return first.startMs
    if (target >= text.length) return last.endMs
    var cursor = 0
    var previousChar = 0
    var previousTime = first.startMs
    words.forEach { word ->
        val start = text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: cursor
        val end = (start + word.text.length).coerceAtMost(text.length)
        if (target <= start) return interpolateTime(previousChar, start, previousTime, word.startMs, target)
        if (target <= end) return interpolateTime(start, end, word.startMs, word.endMs, target)
        cursor = end
        previousChar = end
        previousTime = word.endMs
    }
    return interpolateTime(previousChar, text.length, previousTime, last.endMs, target)
}

private fun interpolateTime(startChar: Int, endChar: Int, startMs: Long, endMs: Long, target: Float): Long {
    if (endChar <= startChar || endMs <= startMs) return startMs
    val through = ((target - startChar) / (endChar - startChar)).coerceIn(0f, 1f)
    return (startMs + (endMs - startMs) * through).toLong()
}