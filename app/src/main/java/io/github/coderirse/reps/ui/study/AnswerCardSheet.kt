package io.github.coderirse.reps.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.AnswerActionType
import io.github.coderirse.reps.ui.theme.SuccessDark
import io.github.coderirse.reps.ui.theme.SuccessLight
import io.github.coderirse.reps.ui.theme.WrongDark
import io.github.coderirse.reps.ui.theme.WrongLight

enum class CardCellStatus { UNTOUCHED, CORRECT, WRONG, BROWSED, CURRENT }

fun cellStatusFor(state: QuestionUiState?, isCurrent: Boolean): CardCellStatus = when {
    state?.graded == true && state.isCorrect == true -> CardCellStatus.CORRECT
    state?.graded == true -> CardCellStatus.WRONG
    state?.actionType == AnswerActionType.BROWSED -> CardCellStatus.BROWSED
    isCurrent -> CardCellStatus.CURRENT
    else -> CardCellStatus.UNTOUCHED
}

/** Collapsible question-number grid; tap jumps to the question. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AnswerCardSheet(
    total: Int,
    currentIndex: Int,
    perQuestion: Map<Long, QuestionUiState>,
    questionIdAt: (Int) -> Long?,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val correctColor = if (dark) SuccessDark else SuccessLight
    val wrongColor = if (dark) WrongDark else WrongLight
    val browsedColor = MaterialTheme.colorScheme.secondary

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(correctColor, stringResource(R.string.card_legend_correct))
                LegendDot(wrongColor, stringResource(R.string.card_legend_wrong))
                LegendDot(browsedColor, stringResource(R.string.card_legend_browsed))
                LegendDot(MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.card_legend_undo))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed((0 until total).toList()) { index, _ ->
                    val status = cellStatusFor(questionIdAt(index)?.let { perQuestion[it] }, index == currentIndex)
                    val container = when (status) {
                        CardCellStatus.CORRECT -> correctColor
                        CardCellStatus.WRONG -> wrongColor
                        CardCellStatus.BROWSED -> browsedColor
                        CardCellStatus.CURRENT -> MaterialTheme.colorScheme.primaryContainer
                        CardCellStatus.UNTOUCHED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when (status) {
                        CardCellStatus.CORRECT, CardCellStatus.WRONG ->
                            if (dark) Color(0xFF101314) else Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(container, CircleShape)
                            .let {
                                if (status == CardCellStatus.CURRENT) {
                                    it.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    it
                                }
                            }
                            .clickable { onJump(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = contentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
