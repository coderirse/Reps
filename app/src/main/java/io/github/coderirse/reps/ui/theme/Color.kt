package io.github.coderirse.reps.ui.theme

import androidx.compose.ui.graphics.Color

// Brand: calm indigo for a study-focused app.
val IndigoPrimaryLight = Color(0xFF4355B9)
val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
val IndigoPrimaryContainerLight = Color(0xFFDCE0FF)
val IndigoOnPrimaryContainerLight = Color(0xFF001159)

val IndigoPrimaryDark = Color(0xFFBBC3FF)
val IndigoOnPrimaryDark = Color(0xFF101C78)
val IndigoPrimaryContainerDark = Color(0xFF2C3F9E)
val IndigoOnPrimaryContainerDark = Color(0xFFDCE0FF)

// Semantic status colors for grading UI (Phase 2: correct/wrong highlighting).
val SuccessLight = Color(0xFF2E7D32)
val SuccessDark = Color(0xFF81C995)
val WrongLight = Color(0xFFC62828)
val WrongDark = Color(0xFFF28B82)

// Text/icons directly on Success*/Wrong* (e.g. answer-card dots).
val OnSuccessLight = Color(0xFFFFFFFF)
val OnSuccessDark = Color(0xFF101314)
val OnWrongLight = Color(0xFFFFFFFF)
val OnWrongDark = Color(0xFF101314)

// Container pairs for graded option rows. Light theme: soft green surface with
// deep green text; dark theme inverts. These used to be hardcoded literals at
// three call sites (QuestionCard / WrongBookScreen / AnswerCardSheet).
val SuccessContainerLight = Color(0xFFC8E6C9)
val OnSuccessContainerLight = Color(0xFF1B5E20)
val SuccessContainerDark = Color(0xFF1B3A1F)
val OnSuccessContainerDark = Color(0xFFB9F6CA)
