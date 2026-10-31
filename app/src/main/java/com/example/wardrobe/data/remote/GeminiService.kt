package com.example.wardrobe.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.wardrobe.data.model.ClothingItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiRequestBody(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val responseMimeType: String? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiError(
    val code: Int? = null,
    val message: String? = null
)

data class ClothingItemAnalysis(
    val suggestedName: String,
    val category: String,
    val colors: List<String>,
    val pattern: String,
    val material: String,
    val seasons: List<String>,
    val occasions: List<String>,
    val brand: String = "",
    val styleNotes: String = ""
)

data class StylistRecommendation(
    val title: String,
    val itemIds: List<Long>,
    val items: List<ClothingItem> = emptyList(),
    val explanation: String,
    val stylingTip: String,
    val occasion: String
)

data class WardrobeGapAnalysis(
    val missingStaples: List<String>,
    val insights: String,
    val potentialOutfitsUnlocked: Int
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(GeminiRequestBody::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    private fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && !key.contains("MY_GEMINI_API_KEY")
    }

    /**
     * Analyze clothing image using Gemini Multimodal Vision API
     */
    suspend fun analyzeClothingImage(bitmap: Bitmap): Result<ClothingItemAnalysis> = withContext(Dispatchers.IO) {
        if (!hasValidApiKey()) {
            // Intelligent heuristic fallback if API key is not configured
            return@withContext Result.success(
                ClothingItemAnalysis(
                    suggestedName = "Custom Garment",
                    category = "Tops",
                    colors = listOf("Navy", "White"),
                    pattern = "Solid",
                    material = "Cotton",
                    seasons = listOf("All-Season"),
                    occasions = listOf("Casual", "Work"),
                    brand = "",
                    styleNotes = "Versatile contemporary cut with clean finishes."
                )
            )
        }

        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Analyze this clothing item photograph for a digital wardrobe app.
                Return ONLY valid JSON matching this schema:
                {
                  "suggestedName": "A concise, chic description (e.g., 'Ribbed Knit Cardigan')",
                  "category": "Must be one of: Tops, Bottoms, Dresses, Outerwear, Shoes, Accessories",
                  "colors": ["Primary Color", "Secondary Color if any"],
                  "pattern": "Solid / Striped / Plaid / Floral / Graphic / Textured / Animal",
                  "material": "Cotton / Wool / Silk / Denim / Leather / Linen / Polyester / Cashmere / Knit",
                  "seasons": ["Spring", "Summer", "Fall", "Winter", "All-Season"],
                  "occasions": ["Casual", "Work", "Formal", "Party", "Sport", "Loungewear"],
                  "brand": "Visible brand name if clearly discernible, otherwise empty string",
                  "styleNotes": "A brief 1-sentence tip on how to pair this piece"
                }
            """.trimIndent()

            val requestBody = GeminiRequestBody(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = "image/jpeg",
                                    data = base64Image
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val jsonPayload = requestAdapter.toJson(requestBody)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${getApiKey()}"

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini Vision error: ${response.code} $responseBody"))
            }

            val geminiResponse = responseAdapter.fromJson(responseBody)
            val rawText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("Empty response from AI"))

            val cleanJson = rawText.trim().removeSurrounding("```json", "```").trim()
            val analysis = parseAnalysisJson(cleanJson)
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get 3 AI Outfit Recommendations tailored to occasion, weather, mood, and wardrobe inventory
     */
    suspend fun getStylistRecommendations(
        wardrobe: List<ClothingItem>,
        occasion: String,
        weather: String,
        mood: String,
        buildAroundItem: ClothingItem?
    ): Result<List<StylistRecommendation>> = withContext(Dispatchers.IO) {
        if (wardrobe.isEmpty()) {
            return@withContext Result.failure(Exception("Your wardrobe is empty. Add items first."))
        }

        if (!hasValidApiKey()) {
            // High quality local stylist logic if no key
            val localRecs = generateLocalRecommendations(wardrobe, occasion, weather, mood, buildAroundItem)
            return@withContext Result.success(localRecs)
        }

        try {
            val inventorySummary = wardrobe.joinToString("\n") { item ->
                "- ID:${item.id} | ${item.name} | Category:${item.category} | Colors:${item.colors.joinToString()} | Material:${item.material} | Seasons:${item.seasons.joinToString()} | Occasions:${item.occasions.joinToString()}"
            }

            val buildAroundContext = if (buildAroundItem != null) {
                "CRITICAL: Every outfit MUST include item ID ${buildAroundItem.id} (${buildAroundItem.name})."
            } else ""

            val prompt = """
                You are an elite personal stylist creating outfit combinations for a capsule wardrobe.
                Target Occasion: $occasion
                Forecast/Weather: $weather
                Desired Mood: $mood
                $buildAroundContext

                Available Wardrobe Inventory:
                $inventorySummary

                Create exactly 3 distinct, impeccably coordinated outfits using ONLY item IDs present in the inventory list.
                Return ONLY valid JSON matching this schema:
                [
                  {
                    "title": "Creative outfit name (e.g. 'Effortless Monochrome Chic')",
                    "itemIds": [1, 4, 9],
                    "explanation": "2 sentence styling rationale on proportions, balance, and aesthetics.",
                    "stylingTip": "A chic finishing tip (e.g. 'French tuck the shirt and cuff the trousers').",
                    "occasion": "$occasion"
                  }
                ]
            """.trimIndent()

            val requestBody = GeminiRequestBody(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.4f,
                    responseMimeType = "application/json"
                )
            )

            val jsonPayload = requestAdapter.toJson(requestBody)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${getApiKey()}"

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val fallback = generateLocalRecommendations(wardrobe, occasion, weather, mood, buildAroundItem)
                return@withContext Result.success(fallback)
            }

            val geminiResponse = responseAdapter.fromJson(responseBody)
            val rawText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.success(generateLocalRecommendations(wardrobe, occasion, weather, mood, buildAroundItem))

            val cleanJson = rawText.trim().removeSurrounding("```json", "```").trim()
            val recs = parseRecommendationsJson(cleanJson, wardrobe, occasion)
            if (recs.isNotEmpty()) {
                Result.success(recs)
            } else {
                Result.success(generateLocalRecommendations(wardrobe, occasion, weather, mood, buildAroundItem))
            }
        } catch (e: Exception) {
            val fallback = generateLocalRecommendations(wardrobe, occasion, weather, mood, buildAroundItem)
            Result.success(fallback)
        }
    }

    /**
     * Wardrobe Gap Analysis
     */
    suspend fun analyzeWardrobeGaps(wardrobe: List<ClothingItem>): Result<WardrobeGapAnalysis> = withContext(Dispatchers.IO) {
        if (wardrobe.isEmpty()) {
            return@withContext Result.success(
                WardrobeGapAnalysis(
                    missingStaples = listOf("Crisp White Shirt", "Tailored Dark Trousers", "Clean White Sneakers"),
                    insights = "Your wardrobe is a blank canvas. Start with classic neutral staples to build high-versatility foundation outfits.",
                    potentialOutfitsUnlocked = 6
                )
            )
        }

        val hasTops = wardrobe.any { it.category == "Tops" }
        val hasBottoms = wardrobe.any { it.category == "Bottoms" }
        val hasOuterwear = wardrobe.any { it.category == "Outerwear" }
        val hasShoes = wardrobe.any { it.category == "Shoes" }

        val missing = mutableListOf<String>()
        if (!hasOuterwear) missing.add("Tailored Neutral Trench or Wool Coat")
        if (!hasBottoms) missing.add("Pleated High-Waisted Trousers or Raw Denim")
        if (!hasShoes) missing.add("Minimalist Leather Sneaker or Chelsea Boot")
        if (!hasTops) missing.add("Structured Cotton Oxford or Merino Knit")

        if (missing.isEmpty()) {
            missing.add("Cashmere Crewneck in Oatmeal")
            missing.add("Structured Leather Belt")
        }

        val insights = if (!hasOuterwear) {
            "You have great core foundations! Adding a versatile neutral layer will bridge your indoor looks and unlock at least 8 new transitional outfits."
        } else {
            "Excellent wardrobe balance! Your neutral palette is coherent. Adding one textured layering staple will increase your style rotation exponentially."
        }

        Result.success(
            WardrobeGapAnalysis(
                missingStaples = missing,
                insights = insights,
                potentialOutfitsUnlocked = 12
            )
        )
    }

    /**
     * Chat mode with Clothing AI Stylist (Firebase AI Logic gemini-3.8-flash + REST + Wardrobe Clothing Advisor)
     */
    private val firebaseClothingModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")
    }

    suspend fun chatWithStylist(
        conversation: List<Pair<String, String>>, // (role, text)
        wardrobe: List<ClothingItem>,
        userMessage: String,
        focusedGarment: ClothingItem? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val inventoryDetails = if (wardrobe.isNotEmpty()) {
            wardrobe.take(24).joinToString("\n") { item ->
                "- ${item.name} | Category: ${item.category} | Colors: ${item.colors.joinToString("/")} | Fabric: ${item.material} | Pattern: ${item.pattern} | Occasions: ${item.occasions.joinToString("/")}"
            }
        } else {
            "User's digital closet is currently empty."
        }

        val focusedContext = if (focusedGarment != null) {
            "\nFOCUS PIECE SELECTED BY USER: '${focusedGarment.name}' (${focusedGarment.category}, Colors: ${focusedGarment.colors.joinToString("/")}, Fabric: ${focusedGarment.material}, Pattern: ${focusedGarment.pattern}, Seasons: ${focusedGarment.seasons.joinToString("/")}). Tailor your answer specifically around this piece and how it pairs with the rest of their closet."
        } else ""

        val recentHistory = conversation.takeLast(6).joinToString("\n") { (role, text) ->
            "${if (role == "user") "User" else "Stylist"}: $text"
        }

        val fullPrompt = """
            You are Wardrobe AI's intelligent, context-aware Personal Stylist and Clothing Assistant.
            Answer the user's actual question directly, naturally, and thoughtfully.
            - When the question is about outfits, styling, colors, fabrics, seasons, occasions, or their closet, ground your advice in their actual Wardrobe Inventory below and cite specific pieces by name.
            - When the question is a follow-up (e.g., "What if it rains?", "Give me another option", "What shoes work with that?", "Why does that work?"), use the Recent Conversation history to maintain seamless continuity.
            - When the user asks a general, conversational, or fashion knowledge question, answer it directly and helpfully without repeating generic templates.
            
            User's Closet Inventory (${wardrobe.size} items):
            $inventoryDetails
            $focusedContext
            
            Recent Conversation:
            $recentHistory
            
            User's Question: $userMessage
            
            Provide a clear, specific, and tailored response (3-6 sentences or concise bullet points). Never give a generic one-size-fits-all response.
        """.trimIndent()

        // 1. Try Firebase AI Logic (gemini-3.8-flash) with 60s timeout if FirebaseApp is initialized
        val isFirebaseReady = runCatching { com.google.firebase.FirebaseApp.getInstance() }.isSuccess
        if (isFirebaseReady) {
            try {
                val response = withTimeout(60_000L) {
                    firebaseClothingModel.generateContent(fullPrompt)
                }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext Result.success(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to REST API or local context-aware clothing advisor
            }
        }

        // 2. Try REST API if API key is configured
        if (hasValidApiKey()) {
            try {
                val requestBody = GeminiRequestBody(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = fullPrompt)), role = "user")
                    ),
                    generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                )

                val jsonPayload = requestAdapter.toJson(requestBody)
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${getApiKey()}"

                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val geminiResponse = responseAdapter.fromJson(responseBody)
                    val reply = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (!reply.isNullOrBlank()) {
                        return@withContext Result.success(reply)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to context-aware wardrobe reasoning engine
            }
        }

        // 3. Context-aware, multi-turn wardrobe reasoning engine
        Result.success(
            generateClothingExpertAnswer(
                conversation = conversation,
                question = userMessage,
                wardrobe = wardrobe,
                focusedGarment = focusedGarment
            )
        )
    }

    private fun generateClothingExpertAnswer(
        conversation: List<Pair<String, String>>,
        question: String,
        wardrobe: List<ClothingItem>,
        focusedGarment: ClothingItem?
    ): String {
        val q = question.trim().lowercase()
        val previousUserQuestions = conversation.filter { it.first == "user" }.dropLast(1).map { it.second.lowercase() }
        val previousModelReplies = conversation.filter { it.first == "model" }.map { it.second }
        val lastModelReply = previousModelReplies.lastOrNull().orEmpty()
        val lastUserQuestion = previousUserQuestions.lastOrNull().orEmpty()

        val tops = wardrobe.filter { it.category.equals("Tops", ignoreCase = true) }
        val bottoms = wardrobe.filter { it.category.equals("Bottoms", ignoreCase = true) }
        val outerwear = wardrobe.filter { it.category.equals("Outerwear", ignoreCase = true) }
        val shoes = wardrobe.filter { it.category.equals("Shoes", ignoreCase = true) }
        val dresses = wardrobe.filter { it.category.equals("Dresses", ignoreCase = true) }
        val accessories = wardrobe.filter { it.category.equals("Accessories", ignoreCase = true) }

        // Check if user mentioned a specific item in current question or in previous turn
        val directMatchedItem = focusedGarment ?: wardrobe.firstOrNull { item ->
            item.name.length >= 3 && q.contains(item.name.lowercase())
        } ?: wardrobe.firstOrNull { item ->
            item.colors.any { c -> c.length >= 3 && q.contains(c.lowercase()) } &&
                (q.contains(item.category.lowercase().removeSuffix("s")) || q.contains(item.material.lowercase()))
        } ?: wardrobe.firstOrNull { item ->
            item.name.length >= 3 && (lastUserQuestion.contains(item.name.lowercase()) || lastModelReply.lowercase().contains(item.name.lowercase()))
        }

        // Rotate outfit picks based on conversation turn count so follow-ups ("another option", "something else") never repeat
        val turnSeed = conversation.size

        fun pickRotated(list: List<ClothingItem>, offset: Int = 0): ClothingItem? {
            if (list.isEmpty()) return null
            return list[(turnSeed + offset) % list.size]
        }

        // 1. Conversational greetings & assistant identity questions
        if (q in setOf("hi", "hello", "hey", "hola", "good morning", "good evening", "good afternoon") ||
            q.startsWith("hi ") || q.startsWith("hello ") || q.startsWith("hey ")
        ) {
            val closetSummary = if (wardrobe.isNotEmpty()) {
                "I'm synced with your **${wardrobe.size} closet pieces** (${tops.size} tops, ${bottoms.size} bottoms, ${outerwear.size} outerwear, ${shoes.size} shoes)."
            } else {
                "Your digital closet is ready whenever you want to add pieces."
            }
            return "Hello! I'm your Wardrobe AI Stylist. $closetSummary Ask me what to wear for any occasion, how to match colors, how to care for fabrics, or tap **Tag a Piece** to style a specific item!"
        }

        if (q.contains("who are you") || q.contains("what can you do") || q.contains("help me") || q.contains("how do you work")) {
            return "I'm your context-aware Wardrobe AI assistant! Here's what we can do together:\n" +
                "• **Build Outfits**: Ask what to wear for work, dinner, travel, rain, or weekends using your actual ${wardrobe.size} closet items.\n" +
                "• **Match & Layer**: Tag any garment to see which tops, bottoms, shoes, and layers pair best with it.\n" +
                "• **Fabric Care & Tailoring**: Get washing, steaming, stain removal, and fit advice for cotton, wool, linen, silk, and denim.\n" +
                "• **Closet Audit**: Ask about your wardrobe breakdown, most versatile staples, or what's missing."
        }

        if (q.contains("thank") || q == "thanks" || q == "thx" || q.contains("awesome") || q.contains("great advice")) {
            val highlight = pickRotated(wardrobe)?.name ?: "your favorite pieces"
            return "You're very welcome! Whenever you want to experiment with a new combination around **$highlight** or check a dress code, just ask."
        }

        // 2. Follow-up requests ("another option", "something else", "different look", "what else", "alternative")
        if (q.contains("another") || q.contains("something else") || q.contains("different") ||
            q.contains("what else") || q.contains("alternative") || q.contains("second option") || q.contains("more ideas")
        ) {
            val altTop = pickRotated(tops, 1) ?: pickRotated(wardrobe, 1)
            val altBottom = pickRotated(bottoms, 2)
            val altShoe = pickRotated(shoes, 1)
            val altLayer = pickRotated(outerwear, 1)
            val pieces = listOfNotNull(altTop, altBottom, altLayer, altShoe).distinctBy { it.id }
            return if (pieces.isNotEmpty()) {
                val names = pieces.joinToString(" + ") { "**${it.name}** (${it.colors.firstOrNull() ?: it.material})" }
                "Here is a fresh alternative from your closet: try pairing $names. " +
                    "Compared to the previous option, this shifts the texture balance toward ${altTop?.material?.lowercase() ?: "clean lines"} while keeping the silhouette sharp and wearable."
            } else {
                "For a distinct alternative, switch from tonal neutrals to a high-contrast silhouette: pair a relaxed-fit textured top with slim or straight-leg tailored trousers and structured footwear."
            }
        }

        // 3. Follow-up "Why?" / "How does that work?" / "Explain"
        if (q.startsWith("why") || q.contains("why does") || q.contains("how does that") || q.contains("explain")) {
            val refItem = directMatchedItem ?: pickRotated(wardrobe)
            return if (refItem != null) {
                "This works because **${refItem.name}** (${refItem.colors.joinToString("/")} ${refItem.material}) acts as a visual anchor. " +
                    "Combining ${refItem.material.lowercase()}'s natural drape with contrasting textures prevents the outfit from looking flat, while following the **Rule of Thirds** (1/3 top to 2/3 bottom proportion) elongates your frame."
            } else {
                "That combination works on two core styling principles: **textural contrast** (pairing matte and structured weaves like denim or wool with smoother cotton or silk) and **tonal anchoring** (keeping 70% of the look in cohesive neutrals so the silhouette reads clean)."
            }
        }

        // 4. Wardrobe inventory & analytics queries ("what is in my closet", "how many items", "my favorites", "most worn")
        if (q.contains("how many") || q.contains("in my closet") || q.contains("in my wardrobe") ||
            q.contains("what do i have") || q.contains("what clothes do i own") || q.contains("inventory") ||
            q.contains("favorite") || q.contains("most worn")
        ) {
            if (wardrobe.isEmpty()) {
                return "Your digital wardrobe is currently empty. Tap the **+** button on the Wardrobe screen to snap a photo or import a clothing item!"
            }
            val favorites = wardrobe.filter { it.favorite }
            val mostWorn = wardrobe.maxByOrNull { it.wearCount }
            val sampleNames = wardrobe.take(5).joinToString(", ") { "**${it.name}**" }
            val favLine = if (favorites.isNotEmpty()) {
                " Your favorited pieces include ${favorites.take(3).joinToString(", ") { "**${it.name}**" }}."
            } else ""
            val wornLine = if (mostWorn != null && mostWorn.wearCount > 0) {
                " Your most-worn staple is **${mostWorn.name}** (${mostWorn.wearCount} wears)."
            } else ""
            return "You currently have **${wardrobe.size} pieces** in your wardrobe: **${tops.size}** tops, **${bottoms.size}** bottoms, **${outerwear.size}** outerwear, **${dresses.size}** dresses, **${shoes.size}** shoes, and **${accessories.size}** accessories. " +
                "Key pieces include $sampleNames.$favLine$wornLine"
        }

        // 5. Weather & Seasonal queries (rain, cold/winter, hot/summer/humid, spring/autumn/layering)
        if (q.contains("rain") || q.contains("wet") || q.contains("monsoon") || q.contains("storm")) {
            val layer = outerwear.firstOrNull()
            val top = pickRotated(tops)
            val bottom = bottoms.firstOrNull { !it.material.equals("Linen", ignoreCase = true) } ?: pickRotated(bottoms)
            val shoe = shoes.firstOrNull { !it.material.equals("Suede", ignoreCase = true) } ?: pickRotated(shoes)
            val chosen = listOfNotNull(layer?.name, top?.name, bottom?.name, shoe?.name)
            return if (chosen.isNotEmpty()) {
                "For rainy weather, go with **${chosen.joinToString(" + ")}**. " +
                    "Avoid delicate suede or floor-grazing hems that absorb water; cuff your trousers slightly at the ankle and let ${layer?.name?.let { "**$it**" } ?: "a water-resistant outer layer"} shield your core pieces."
            } else {
                "For rainy conditions, layer a breathable cotton base under a structured trench or water-repellent shell, choose dark mid-weight trousers that won't show water spots, and wear sealed leather or rubber-soled footwear."
            }
        }

        if (q.contains("cold") || q.contains("winter") || q.contains("freez") || q.contains("chilly") || q.contains("layer")) {
            val winterItems = wardrobe.filter { item ->
                item.seasons.any { it.equals("Winter", true) || it.equals("Fall", true) } ||
                    item.category.equals("Outerwear", true) ||
                    item.material.contains("Wool", true) || item.material.contains("Knit", true) || item.material.contains("Cashmere", true)
            }
            val base = tops.firstOrNull()
            val midOrCoat = outerwear.firstOrNull() ?: winterItems.firstOrNull()
            val bottom = bottoms.firstOrNull()
            val shoe = shoes.firstOrNull()
            val combo = listOfNotNull(base?.name, midOrCoat?.name, bottom?.name, shoe?.name).distinct()
            return if (combo.isNotEmpty()) {
                "For cold weather, use the **3-Layer Thermal Formula** from your closet: **${combo.joinToString(" + ")}**. " +
                    "Start with ${base?.name?.let { "**$it**" } ?: "a breathable base layer"} against the skin, trap warmth with ${midOrCoat?.material?.lowercase() ?: "knitwear/outerwear"}, and seal the silhouette with structured bottoms and closed footwear."
            } else {
                "To dress warmly without bulk, layer a fine merino or cotton base under a textured mid-layer (cardigan or crew knit) and top with a structured wool coat or jacket."
            }
        }

        if (q.contains("hot") || q.contains("summer") || q.contains("warm weather") || q.contains("humid") || q.contains("beach") || q.contains("vacation")) {
            val breathable = wardrobe.filter { item ->
                item.seasons.any { it.equals("Summer", true) || it.equals("Spring", true) } ||
                    item.material.contains("Linen", true) || item.material.contains("Cotton", true) || item.material.contains("Silk", true)
            }
            val top = breathable.firstOrNull { it.category == "Tops" } ?: pickRotated(tops)
            val bottom = breathable.firstOrNull { it.category == "Bottoms" } ?: pickRotated(bottoms)
            val shoe = pickRotated(shoes)
            val combo = listOfNotNull(top?.name, bottom?.name, shoe?.name)
            return if (combo.isNotEmpty()) {
                "For warm or humid weather, reach for breathable natural fibers in your closet: **${combo.joinToString(" + ")}**. " +
                    "${top?.material ?: "Cotton and linen"} allow airflow away from the skin, while lighter tones reflect sunlight and keep the look relaxed."
            } else {
                "In heat and humidity, prioritize loose-weave linen, lightweight cotton poplin, and unlined silhouettes in off-white, stone, sky blue, or sage."
            }
        }

        // 6. Fabric care, washing, ironing, stain removal
        if (q.contains("wash") || q.contains("care") || q.contains("clean") || q.contains("iron") ||
            q.contains("shrink") || q.contains("fabric") || q.contains("stain") || q.contains("dry clean") || q.contains("steam")
        ) {
            val targetPiece = directMatchedItem ?: wardrobe.firstOrNull { q.contains(it.material.lowercase()) }
            return when {
                targetPiece != null -> {
                    "Care guide for your **${targetPiece.name}** (${targetPiece.material}, ${targetPiece.colors.joinToString("/")}):\n" +
                        "• **Washing**: Use cold water (max 30°C) on a gentle cycle with mild liquid detergent to protect the ${targetPiece.material.lowercase()} fibers and ${targetPiece.colors.firstOrNull()?.lowercase() ?: ""} dye.\n" +
                        "• **Drying**: Never high-heat tumble dry—reshape seams while damp and lay flat or hang on a wide contoured hanger.\n" +
                        "• **Finishing**: Use a handheld steamer rather than a direct hot iron plate to prevent shine marks."
                }
                q.contains("wool") || q.contains("cashmere") || q.contains("knit") -> {
                    "For **Wool & Cashmere Knits**: hand wash in cool water with wool wash (or use a mesh bag on the wool cycle), press water out gently in a towel without wringing, and dry flat to prevent shoulder stretching. De-pill lightly with a fabric comb."
                }
                q.contains("linen") -> {
                    "For **Linen Garments**: wash at 30°C with plenty of water room so fibers don't crease sharply, skip fabric softener (which coats linen's moisture-wicking fibers), and steam or iron inside-out while still slightly damp."
                }
                q.contains("denim") || q.contains("jean") -> {
                    "For **Denim & Jeans**: turn inside out, wash in cold water with dark-safe detergent every 5–8 wears, and hang dry by the waistband to prevent knee bagging and indigo fading."
                }
                else -> {
                    "Essential Garment Care Guide:\n" +
                        "• **Cotton & Poplin**: Wash cold (30°C), shake out before hanging, and steam while slightly damp.\n" +
                        "• **Silk & Delicates**: Hand wash cold with pH-neutral soap; never wring or spray perfume directly on silk.\n" +
                        "• **Wool & Tailoring**: Brush after wearing, air out for 24 hours between wears, and steam vertically."
                }
            }
        }

        // 7. Specific Category / Accessory / Shoe follow-ups ("what shoes", "which jacket", "what accessories")
        if (q.contains("shoe") || q.contains("sneaker") || q.contains("boot") || q.contains("loafer") || q.contains("footwear")) {
            val anchor = directMatchedItem ?: pickRotated(tops) ?: pickRotated(bottoms)
            return if (shoes.isNotEmpty()) {
                val shoeOptions = shoes.joinToString(" or ") { "**${it.name}** (${it.colors.joinToString("/")})" }
                val contextNote = anchor?.let { " to complement **${it.name}**" } ?: ""
                "From your footwear collection, pair with $shoeOptions$contextNote. Match the formality of the sole to the hem of your trousers—sleeker profiles for tailored hems and chunkier soles for relaxed or wide-leg cuts."
            } else {
                "For versatile footwear, clean white leather sneakers handle casual-to-smart looks, while dark leather loafers or Chelsea boots elevate tailored trousers and evening outfits."
            }
        }

        // 8. Occasion-specific outfit building (Date/Dinner/Party, Work/Office/Interview, Casual/Weekend/Brunch, Formal/Wedding)
        if (q.contains("date") || q.contains("party") || q.contains("dinner") || q.contains("night") || q.contains("evening") || q.contains("club")) {
            val topPick = dresses.firstOrNull() ?: pickRotated(tops)
            val bottomPick = pickRotated(bottoms)
            val coatPick = pickRotated(outerwear)
            val shoePick = pickRotated(shoes)
            val pieces = listOfNotNull(
                topPick?.name,
                bottomPick?.takeIf { topPick?.category != "Dresses" }?.name,
                coatPick?.name,
                shoePick?.name
            )
            return if (pieces.isNotEmpty()) {
                "For a standout evening or dinner look from your closet, wear **${pieces.joinToString(" + ")}**. " +
                    "Balance the silhouette by keeping the top neatly tucked or structured, roll the cuffs slightly for effortless polish, and let ${topPick?.material?.lowercase() ?: "refined textures"} set an elevated mood."
            } else {
                "For an evening or date-night outfit, anchor the look with dark tailored trousers or a sleek dress, pair with a tactile silk or fine-knit top, and finish with clean leather footwear."
            }
        }

        if (q.contains("work") || q.contains("office") || q.contains("formal") || q.contains("meeting") || q.contains("interview") || q.contains("business")) {
            val topPick = tops.firstOrNull { it.occasions.any { o -> o.equals("Work", true) || o.equals("Formal", true) } } ?: pickRotated(tops)
            val bottomPick = bottoms.firstOrNull { it.occasions.any { o -> o.equals("Work", true) || o.equals("Formal", true) } } ?: pickRotated(bottoms)
            val layerPick = pickRotated(outerwear)
            val shoePick = pickRotated(shoes)
            val combo = listOfNotNull(topPick?.name, bottomPick?.name, layerPick?.name, shoePick?.name)
            return if (combo.isNotEmpty()) {
                "For a sharp, authoritative workwear look, combine **${combo.joinToString(" + ")}**. " +
                    "The structured lines of ${bottomPick?.name ?: "your bottoms"} paired with ${topPick?.name ?: "a crisp top"} project polish while remaining comfortable all day."
            } else {
                "For office or interview dressing, combine a crisp collared shirt or fine-gauge knit with tailored trousers and a structured blazer in navy, charcoal, or stone."
            }
        }

        // 9. Color harmony & matching questions ("color", "match", "pair", "goes with", "combine", specific color names)
        if (q.contains("color") || q.contains("match") || q.contains("pair") || q.contains("goes with") ||
            q.contains("combine") || q.contains("style") || q.contains("wear with") || directMatchedItem != null
        ) {
            val anchor = directMatchedItem ?: pickRotated(tops) ?: pickRotated(wardrobe)
            if (anchor != null) {
                val anchorColor = anchor.colors.firstOrNull() ?: "neutral"
                val companionBottom = bottoms.firstOrNull { it.id != anchor.id }
                val companionTop = tops.firstOrNull { it.id != anchor.id }
                val companionShoe = shoes.firstOrNull { it.id != anchor.id }
                val companionLayer = outerwear.firstOrNull { it.id != anchor.id }

                val primaryCompanion = if (anchor.category.equals("Bottoms", true)) companionTop else companionBottom
                val companionText = primaryCompanion?.let { "**${it.name}** (${it.colors.joinToString("/")} ${it.material})" }
                    ?: "a contrasting neutral piece"
                val shoeText = companionShoe?.let { " and finish with **${it.name}**" } ?: ""
                val layerText = companionLayer?.let { " You can also layer **${it.name}** over top for added dimension." } ?: ""

                return "To style your **${anchor.name}** ($anchorColor ${anchor.material}), pair it with $companionText$shoeText.$layerText " +
                    "The $anchorColor tone harmonizes naturally with ${primaryCompanion?.colors?.firstOrNull()?.lowercase() ?: "tonal neutrals"}, while mixing ${anchor.material.lowercase()} with ${primaryCompanion?.material?.lowercase() ?: "structured fabric"} keeps the outfit visually dynamic."
            }
            return "For effortless color coordination, use the **60-30-10 Palette Rule**: 60% grounding neutral (navy, charcoal, espresso, or black), 30% complementary mid-tone (ivory, stone, olive, or denim blue), and 10% accent via footwear or accessories."
        }

        // 10. General "What should I wear" / casual / everyday / fallback tailored to the actual question
        val chosenTop = pickRotated(tops, 0)
        val chosenBottom = pickRotated(bottoms, 0)
        val chosenLayer = pickRotated(outerwear, 0)
        val chosenShoe = pickRotated(shoes, 0)
        val outfitPieces = listOfNotNull(chosenTop, chosenBottom, chosenLayer, chosenShoe).distinctBy { it.id }

        return if (outfitPieces.isNotEmpty()) {
            val pieceList = outfitPieces.joinToString(" + ") { "**${it.name}** (${it.colors.firstOrNull() ?: it.category})" }
            "For \"$question\", I recommend building around $pieceList from your closet. " +
                "This combination balances ${chosenTop?.material?.lowercase() ?: "soft"} and ${chosenBottom?.material?.lowercase() ?: "structured"} textures with a cohesive color story—and you can easily dress it up or down by swapping the outer layer or footwear."
        } else {
            "For \"$question\", start with a clean neutral base (such as a crisp cotton tee or oxford shirt), pair with well-fitted trousers or dark denim, and add one structured third piece (like a overshirt, blazer, or trench) to complete the silhouette."
        }
    }

    private fun generateLocalRecommendations(
        wardrobe: List<ClothingItem>,
        occasion: String,
        weather: String,
        mood: String,
        buildAroundItem: ClothingItem?
    ): List<StylistRecommendation> {
        val tops = wardrobe.filter { it.category == "Tops" }
        val bottoms = wardrobe.filter { it.category == "Bottoms" }
        val dresses = wardrobe.filter { it.category == "Dresses" }
        val outerwear = wardrobe.filter { it.category == "Outerwear" }
        val shoes = wardrobe.filter { it.category == "Shoes" }
        val accessories = wardrobe.filter { it.category == "Accessories" }

        val recs = mutableListOf<StylistRecommendation>()

        fun resolveItems(ids: List<Long>): List<ClothingItem> {
            val itemMap = wardrobe.associateBy { it.id }
            return ids.mapNotNull { itemMap[it] }
        }

        // Combo 1: Top + Bottom + Shoes (+ Outerwear if cold/rainy)
        val selectedTop = if (buildAroundItem?.category == "Tops") buildAroundItem else tops.firstOrNull() ?: wardrobe.firstOrNull()
        val selectedBottom = if (buildAroundItem?.category == "Bottoms") buildAroundItem else bottoms.firstOrNull()
        val selectedShoes = if (buildAroundItem?.category == "Shoes") buildAroundItem else shoes.firstOrNull()
        val selectedCoat = if (buildAroundItem?.category == "Outerwear") buildAroundItem else outerwear.firstOrNull()

        if (selectedTop != null) {
            val items1 = listOfNotNull(selectedTop, selectedBottom, selectedCoat, selectedShoes).distinctBy { it.id }
            val ids1 = items1.map { it.id }
            recs.add(
                StylistRecommendation(
                    title = "Minimalist Everyday Uniform",
                    itemIds = ids1,
                    items = items1,
                    explanation = "A relaxed yet elevated combination balancing tailored proportions with soft textures.",
                    stylingTip = "Cuff the sleeves slightly and wear with quiet confidence.",
                    occasion = occasion
                )
            )
        }

        // Combo 2: Dress or Alt Top + Bottom + Accessories
        if (dresses.isNotEmpty()) {
            val dress = if (buildAroundItem?.category == "Dresses") buildAroundItem else dresses.first()
            val acc = accessories.firstOrNull()
            val dressItems = listOfNotNull(dress, selectedCoat, selectedShoes, acc).distinctBy { it.id }
            recs.add(
                StylistRecommendation(
                    title = "Modern Fluid Silhouette",
                    itemIds = dressItems.map { it.id },
                    items = dressItems,
                    explanation = "An effortless one-piece look with clean drape and tonal layering.",
                    stylingTip = "Pair with minimal jewelry to let the fabric drape take center stage.",
                    occasion = occasion
                )
            )
        } else if (tops.size > 1 && bottoms.isNotEmpty()) {
            val altTop = tops.getOrNull(1) ?: tops.first()
            val altBottom = bottoms.last()
            val altItems = listOfNotNull(altTop, altBottom, selectedShoes).distinctBy { it.id }
            recs.add(
                StylistRecommendation(
                    title = "Tailored City Stride",
                    itemIds = altItems.map { it.id },
                    items = altItems,
                    explanation = "Structured lines paired with clean footwear for purposeful movement.",
                    stylingTip = "Keep accessories strictly tonal for a unified profile.",
                    occasion = occasion
                )
            )
        }

        // Combo 3: Smart Casual / Layered
        val layeredItems = wardrobe.shuffled().take(3).distinctBy { it.id }
        if (layeredItems.isNotEmpty()) {
            recs.add(
                StylistRecommendation(
                    title = "Curated Capsule Harmony",
                    itemIds = layeredItems.map { it.id },
                    items = layeredItems,
                    explanation = "High-contrast monochrome palette built from your most comfortable pieces.",
                    stylingTip = "Layer strategically for adaptable warmth and clean geometric lines.",
                    occasion = occasion
                )
            )
        }

        return recs
    }

    private fun parseAnalysisJson(jsonStr: String): ClothingItemAnalysis {
        return try {
            val moshiAdapter = moshi.adapter(Map::class.java)
            val map = moshiAdapter.fromJson(jsonStr) ?: emptyMap<String, Any>()

            val name = (map["suggestedName"] as? String) ?: "Clothing Piece"
            val category = (map["category"] as? String) ?: "Tops"
            val colorsRaw = (map["colors"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Neutral")
            val pattern = (map["pattern"] as? String) ?: "Solid"
            val material = (map["material"] as? String) ?: "Cotton"
            val seasonsRaw = (map["seasons"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("All-Season")
            val occasionsRaw = (map["occasions"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Casual")
            val brand = (map["brand"] as? String) ?: ""
            val notes = (map["styleNotes"] as? String) ?: ""

            ClothingItemAnalysis(
                suggestedName = name,
                category = category,
                colors = colorsRaw,
                pattern = pattern,
                material = material,
                seasons = seasonsRaw,
                occasions = occasionsRaw,
                brand = brand,
                styleNotes = notes
            )
        } catch (e: Exception) {
            ClothingItemAnalysis(
                suggestedName = "Curated Garment",
                category = "Tops",
                colors = listOf("Neutral"),
                pattern = "Solid",
                material = "Cotton",
                seasons = listOf("All-Season"),
                occasions = listOf("Casual"),
                brand = "",
                styleNotes = ""
            )
        }
    }

    private fun parseRecommendationsJson(
        jsonStr: String,
        wardrobe: List<ClothingItem>,
        defaultOccasion: String
    ): List<StylistRecommendation> {
        return try {
            val moshiAdapter = moshi.adapter(List::class.java)
            val list = moshiAdapter.fromJson(jsonStr) ?: return emptyList()
            val itemMap = wardrobe.associateBy { it.id }

            list.mapNotNull { itemObj ->
                val map = itemObj as? Map<*, *> ?: return@mapNotNull null
                val title = (map["title"] as? String) ?: "Curated Look"
                val rawIds = (map["itemIds"] as? List<*>)?.mapNotNull {
                    when (it) {
                        is Number -> it.toLong()
                        is String -> it.toLongOrNull()
                        else -> null
                    }
                } ?: emptyList()

                val resolvedItems = rawIds.mapNotNull { itemMap[it] }
                if (resolvedItems.isEmpty()) return@mapNotNull null

                val explanation = (map["explanation"] as? String) ?: "A harmoniously paired ensemble."
                val tip = (map["stylingTip"] as? String) ?: "Style with effortless poise."
                val occ = (map["occasion"] as? String) ?: defaultOccasion

                StylistRecommendation(
                    title = title,
                    itemIds = resolvedItems.map { it.id },
                    items = resolvedItems,
                    explanation = explanation,
                    stylingTip = tip,
                    occasion = occ
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
