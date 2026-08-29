package ia.off

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private sealed interface MessageSegment {
    data class Markdown(val text: String) : MessageSegment
    data class Code(val language: String?, val code: String) : MessageSegment
}

@Composable
fun RichMessageContent(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMessageSegments(text).forEach { segment ->
            when (segment) {
                is MessageSegment.Markdown -> MarkdownSegment(segment.text)
                is MessageSegment.Code -> CodeBlock(segment.language, segment.code)
            }
        }
    }
}

@Composable
private fun MarkdownSegment(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Text("")
                line.startsWith("### ") -> Text(inlineMarkdown(line.removePrefix("### ")), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") -> Text(inlineMarkdown(line.removePrefix("## ")), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Text(inlineMarkdown(line.removePrefix("# ")), style = MaterialTheme.typography.titleLarge)
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row {
                        Text("• ")
                        Text(inlineMarkdown(line.drop(2)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                line.startsWith("> ") -> Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        inlineMarkdown(line.removePrefix("> ")),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(language?.ifBlank { "código" } ?: "código", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copiar") }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

private fun parseMessageSegments(text: String): List<MessageSegment> {
    if (!text.contains("```")) return listOf(MessageSegment.Markdown(text))
    val result = mutableListOf<MessageSegment>()
    var cursor = 0
    while (cursor < text.length) {
        val start = text.indexOf("```", cursor)
        if (start < 0) {
            if (cursor < text.length) result += MessageSegment.Markdown(text.substring(cursor))
            break
        }
        if (start > cursor) result += MessageSegment.Markdown(text.substring(cursor, start))

        val headerEnd = text.indexOf('\n', start + 3)
        if (headerEnd < 0) {
            result += MessageSegment.Markdown(text.substring(start))
            break
        }
        val language = text.substring(start + 3, headerEnd).trim().ifBlank { null }
        val end = text.indexOf("```", headerEnd + 1)
        if (end < 0) {
            result += MessageSegment.Code(language, text.substring(headerEnd + 1).trimEnd())
            break
        }
        result += MessageSegment.Code(language, text.substring(headerEnd + 1, end).trimEnd())
        cursor = end + 3
    }
    return result.ifEmpty { listOf(MessageSegment.Markdown(text)) }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index++])
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            else -> append(text[index++])
        }
    }
}
