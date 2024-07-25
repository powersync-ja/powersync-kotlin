package com.powersync.demos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.random.Random

@Composable
fun ResultSetTable(columns: List<String>?, rows: List<List<String>>) {
    when {
        columns == null -> Text("No results yet. Execute a query to see results.")
        rows.isEmpty() -> Text("Query executed successfully, but returned no results.")
        else -> {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberLazyListState()

            Column {
                // Sticky header
                Row(
                    Modifier
                        .horizontalScroll(horizontalScrollState)
                        .background(MaterialTheme.colors.surface)
                        .padding(bottom = 8.dp)
                        .zIndex(1f)
                ) {
                    columns.forEach { columnName ->
                        Text(
                            text = columnName,
                            modifier = Modifier.width(150.dp),
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.subtitle1
                        )
                    }
                }
                Divider(Modifier.zIndex(1f))

                // Scrollable content
                LazyColumn(
                    state = verticalScrollState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(rows.size) { rowIndex ->
                        Row(
                            Modifier
                                .horizontalScroll(horizontalScrollState)
                                .padding(vertical = 4.dp)
                        ) {
                            rows[rowIndex].forEach { cellData ->
                                Text(
                                    text = cellData,
                                    modifier = Modifier.width(150.dp)
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

data class FakeResultSet(
    val columns: List<String>,
    val rows: List<List<String>>
)

fun generateFakeData(): FakeResultSet {
    val columns = listOf("ID", "Name", "Email", "Age", "City", "Country", "Occupation")
    val rows = (1..20).map {
        listOf(
            it.toString(),
            "User$it",
            "user$it@example.com",
            Random.nextInt(18, 70).toString(),
            listOf("New York", "London", "Tokyo", "Paris", "Berlin").random(),
            listOf("USA", "UK", "Japan", "France", "Germany").random(),
            listOf("Engineer", "Teacher", "Doctor", "Artist", "Scientist").random()
        )
    }
    return FakeResultSet(columns, rows)
}