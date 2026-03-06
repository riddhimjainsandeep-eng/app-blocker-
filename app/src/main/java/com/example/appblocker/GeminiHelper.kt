package com.example.appblocker

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject

/**
 * Singleton to handle Gemini API interactions for logical puzzles.
 */
object GeminiHelper {

    private const val MODEL_NAME = "gemini-3.1-flash-lite-preview"

    // Only asking for numeric answers to integrate seamlessly with the 3x4 custom keypad.
    private val puzzleInstructions = """
        You are a puzzle master generating a multiple-choice logical reasoning question.
        The user must solve this math or logic puzzle to unlock a blocked app.
        
        Your puzzle must be:
        1. A word problem, numerical series sequence, logic puzzle, or syllogism deduction.
        2. Solvable without a calculator.
        3. Feature exactly ONE clear NUMERIC answer.
        4. Do NOT use huge numbers. Keep the answer between 0 and 999999.
        
        Return ONLY a perfectly formatted JSON object with no markdown syntax, no wrapping, matching exactly this structure:
        {
          "question": "The text of your logic puzzle. You can use newline \n characters if needed.",
          "answer": 1234
        }
    """.trimIndent()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = com.google.ai.client.generativeai.type.content { text(puzzleInstructions) },
            generationConfig = generationConfig {
                temperature = 0.5f // Low temp for more deterministic puzzles
                responseMimeType = "application/json"
            }
        )
    }

    /**
     * Data class to hold the generated puzzle
     */
    data class LogicPuzzle(val question: String, val correctAnswer: Int)

    /**
     * Calls the Gemini API to generate a logic puzzle.
     * Returns a default fallback puzzle on failure to prevent locking the user out.
     */
    suspend fun generateLogicPuzzle(): LogicPuzzle {
        return try {
            val response = generativeModel.generateContent("Give me a new, unique numeric logic puzzle.")
            val text = response.text ?: throw Exception("Empty response from model")
            
            // Clean markdown if the model accidentally included it despite instructions
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(cleanJson)
            
            LogicPuzzle(
                question = jsonObj.getString("question"),
                correctAnswer = jsonObj.getInt("answer")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback puzzle in case of network error, API exhaustion, or parsing failure
            LogicPuzzle(
                question = "API Error. Fallback puzzle:\n(10 × 20) − (5²) = ?",
                correctAnswer = 175
            )
        }
    }
}
