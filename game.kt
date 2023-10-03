// src/jsMain/kotlin/com/example/pages/Game.kt
package com.example.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.text.Text
import com.varabyte.kobweb.compose.ui.widgets.Button
import com.varabyte.kobweb.silk.components.Page
import kotlin.random.Random

// Define the possible choices for the game
enum class Choice {
    Rock, Paper, Scissors
}

// Define the logic to determine the winner of the game
fun getWinner(userChoice: Choice, computerChoice: Choice): String {
    return when {
        userChoice == computerChoice -> "It's a tie!"
        userChoice == Choice.Rock && computerChoice == Choice.Scissors -> "You win!"
        userChoice == Choice.Paper && computerChoice == Choice.Rock -> "You win!"
        userChoice == Choice.Scissors && computerChoice == Choice.Paper -> "You win!"
        else -> "You lose!"
    }
}

@Page
@Composable
fun GamePage() {
    // Create a state variable to store the user's choice
    var userChoice by remember { mutableStateOf<Choice?>(null) }
    // Create a state variable to store the computer's choice
    var computerChoice by remember { mutableStateOf<Choice?>(null) }
    // Create a state variable to store the game result
    var result by remember { mutableStateOf("") }
    // Create a function to play the game
    fun playGame(choice: Choice) {
        // Set the user's choice to the given choice
        userChoice = choice
        // Set the computer's choice to a random choice
        computerChoice = Choice.values().random()
        // Set the result to the winner of the game
        result = getWinner(userChoice!!, computerChoice!!)
    }
    // Use the Page component to create a web page
    Page {
        // Use the Text tag to display the title
        Text("Rock-Paper-Scissors Game", Modifier.center())
        // Use the Text tag to display the instructions
        Text("Choose your move:", Modifier.center())
        // Use the Button tag to create a button for each choice
        Button(onClick = { playGame(Choice.Rock) }, Modifier.center()) {
            // Use the Text tag to display the button label
            Text("Rock")
        }
        Button(onClick = { playGame(Choice.Paper) }, Modifier.center()) {
            Text("Paper")
        }
        Button(onClick = { playGame(Choice.Scissors) }, Modifier.center()) {
            Text("Scissors")
        }
        // Use the Text tag to display the user's choice
        Text("You chose: ${userChoice ?: "Nothing"}", Modifier.center())
        // Use the Text tag to display the computer's choice
        Text("Computer chose: ${computerChoice ?: "Nothing"}", Modifier.center())
        // Use the Text tag to display the result
        Text(result, Modifier.center())
    }
}
