package avail.anvil

import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun App() {
	var text by remember { mutableStateOf("Hello, Avail!") }

	Button(onClick = {
		text = "Hello, Anvil"
	}) {
		Text(text)
	}
}
