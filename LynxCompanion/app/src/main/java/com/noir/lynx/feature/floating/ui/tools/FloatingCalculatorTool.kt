package com.noir.lynx.feature.floating.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun FloatingCalculatorTool(
    modifier: Modifier = Modifier
) {
    var displayExpr by remember { mutableStateOf("0") }
    var resultText by remember { mutableStateOf("") }
    var selectedFormula by remember { mutableStateOf<String?>(null) }

    // Formula inputs
    var statInput1 by remember { mutableStateOf("100") }
    var statInput2 by remember { mutableStateOf("2500") }

    val formatter = remember { DecimalFormat("#,###.##") }

    fun appendInput(char: String) {
        if (displayExpr == "0" && char !in setOf(".", "+", "-", "×", "÷")) {
            displayExpr = char
        } else {
            displayExpr += char
        }
    }

    fun clear() {
        displayExpr = "0"
        resultText = ""
    }

    fun deleteLast() {
        displayExpr = if (displayExpr.length <= 1) "0" else displayExpr.dropLast(1)
    }

    fun evaluate() {
        try {
            val expr = displayExpr.replace("×", "*").replace("÷", "/")
            val res = evaluateSimpleExpression(expr)
            resultText = formatter.format(res)
        } catch (_: Exception) {
            resultText = "Error"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Gaming Math Presets Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Kalkulator", "Armor DR%", "Haste CDR%", "EHP").forEach { tab ->
                val isSelected = (selectedFormula == null && tab == "Kalkulator") || selectedFormula == tab
                Surface(
                    onClick = { selectedFormula = if (tab == "Kalkulator") null else tab },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF141D2B),
                    border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF223046)),
                    modifier = Modifier.weight(1f).height(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        if (selectedFormula == null) {
            // Standard Calculator Screen
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF141C29),
                border = BorderStroke(0.8.dp, Color(0xFF223147)),
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = displayExpr,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8CA0BA),
                        maxLines = 1
                    )
                    Text(
                        text = if (resultText.isNotEmpty()) "= $resultText" else "",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF),
                        maxLines = 1
                    )
                }
            }

            // Keypad Grid
            val buttons = listOf(
                listOf("C", "DEL", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("00", "0", ".", "=")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { key ->
                            val isOp = key in setOf("+", "-", "×", "÷", "=")
                            val isAction = key in setOf("C", "DEL", "%")
                            val btnColor = when {
                                key == "=" -> Color(0xFF00E5FF)
                                isOp -> Color(0xFF1B273A)
                                isAction -> Color(0xFF1A2332)
                                else -> Color(0xFF101722)
                            }
                            val textColor = when {
                                key == "=" -> Color.Black
                                isOp -> Color(0xFF00E5FF)
                                isAction -> Color(0xFFFF5252)
                                else -> Color.White
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = btnColor,
                                border = BorderStroke(0.6.dp, if (key == "=") Color(0xFF00E5FF) else Color(0xFF1F2B3D)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        when (key) {
                                            "C" -> clear()
                                            "DEL" -> deleteLast()
                                            "=" -> evaluate()
                                            else -> appendInput(key)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Gaming Formula Calculator Mode
            GamingFormulaCard(
                formulaName = selectedFormula ?: "",
                input1 = statInput1,
                input2 = statInput2,
                onInput1Change = { statInput1 = it },
                onInput2Change = { statInput2 = it }
            )
        }
    }
}

@Composable
private fun GamingFormulaCard(
    formulaName: String,
    input1: String,
    input2: String,
    onInput1Change: (String) -> Unit,
    onInput2Change: (String) -> Unit
) {
    val val1 = input1.toDoubleOrNull() ?: 0.0
    val val2 = input2.toDoubleOrNull() ?: 0.0

    val (desc, formula, result) = when (formulaName) {
        "Armor DR%" -> {
            val dr = if (val1 + 100 > 0) (val1 / (val1 + 100.0)) * 100.0 else 0.0
            Triple(
                "Persentase Pengurangan Damage (Armor)",
                "DR% = Armor / (Armor + 100) × 100%",
                String.format(java.util.Locale.US, "%.1f%% DR", dr)
            )
        }
        "Haste CDR%" -> {
            val cdr = if (val1 + 100 > 0) (val1 / (val1 + 100.0)) * 100.0 else 0.0
            val cdReduction = cdr.coerceAtMost(70.0)
            Triple(
                "Cooldown Reduction dari Ability Haste",
                "CDR% = Haste / (Haste + 100) × 100%",
                String.format(java.util.Locale.US, "%.1f%% CDR", cdReduction)
            )
        }
        else -> { // EHP
            val ehp = val2 * (1.0 + (val1 / 100.0))
            Triple(
                "Effective HP (Total Ketahanan Nyata)",
                "EHP = HP × (1 + Armor / 100)",
                String.format(java.util.Locale.US, "%,.0f EHP", ehp)
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF141D2B),
            border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(desc, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(formula, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00E5FF))
                Spacer(Modifier.height(4.dp))
                Text(result, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF00E5FF))
            }
        }

        // Inputs
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF101722),
            border = BorderStroke(0.6.dp, Color(0xFF223147)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label1 = if (formulaName == "EHP") "Nilai Armor / Resist" else if (formulaName == "Armor DR%") "Total Armor" else "Ability Haste"
                Text(label1, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("50", "100", "150", "200", "300").forEach { preset ->
                        Surface(
                            onClick = { onInput1Change(preset) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (input1 == preset) Color(0xFF00E5FF) else Color(0xFF1A2433),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(preset, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (input1 == preset) Color.Black else Color.White)
                            }
                        }
                    }
                }

                if (formulaName == "EHP") {
                    Spacer(Modifier.height(4.dp))
                    Text("Total Max HP", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("1500", "2500", "3500", "5000").forEach { preset ->
                            Surface(
                                onClick = { onInput2Change(preset) },
                                shape = RoundedCornerShape(4.dp),
                                color = if (input2 == preset) Color(0xFF00E5FF) else Color(0xFF1A2433),
                                modifier = Modifier.height(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                    Text(preset, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (input2 == preset) Color.Black else Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateSimpleExpression(expr: String): Double {
    val tokens = mutableListOf<String>()
    var currentNumber = ""
    for (char in expr) {
        if (char in setOf('+', '-', '*', '/', '%')) {
            if (currentNumber.isNotEmpty()) {
                tokens.add(currentNumber)
                currentNumber = ""
            }
            tokens.add(char.toString())
        } else {
            currentNumber += char
        }
    }
    if (currentNumber.isNotEmpty()) tokens.add(currentNumber)
    if (tokens.isEmpty()) return 0.0

    // Multiply, divide, modulo
    var i = 0
    while (i < tokens.size) {
        if (tokens[i] in setOf("*", "/", "%")) {
            val left = tokens[i - 1].toDoubleOrNull() ?: 0.0
            val right = tokens[i + 1].toDoubleOrNull() ?: 1.0
            val res = when (tokens[i]) {
                "*" -> left * right
                "/" -> if (right != 0.0) left / right else 0.0
                else -> left % right
            }
            tokens[i - 1] = res.toString()
            tokens.removeAt(i)
            tokens.removeAt(i)
            i--
        } else {
            i++
        }
    }

    // Add and subtract
    var total = tokens.getOrNull(0)?.toDoubleOrNull() ?: 0.0
    var j = 1
    while (j < tokens.size) {
        val op = tokens[j]
        val next = tokens.getOrNull(j + 1)?.toDoubleOrNull() ?: 0.0
        total = if (op == "+") total + next else total - next
        j += 2
    }
    return total
}
