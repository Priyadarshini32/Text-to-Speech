package com.example.text_voice

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.text_voice.databinding.ActivityMainBinding
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private val historyList = mutableListOf<HistoryItem>()
    private lateinit var historyAdapter: HistoryAdapter
    private var currentLanguageCode: String = "en-US"
    private var isPaused = false
    private var lastPosition = 0
    private var spokenText = ""
    private val MAX_CHARACTER_LIMIT = 1000
    private var selectedVoiceIndex = 0
    private var availableVoices = mutableListOf<Voice>()

    // Translation properties
    private var translators = mutableMapOf<String, Translator>()
    private val supportedLanguages = mapOf(
        "English" to TranslateLanguage.ENGLISH,
        "Spanish" to TranslateLanguage.SPANISH,
        "French" to TranslateLanguage.FRENCH,
        "German" to TranslateLanguage.GERMAN,
        "Italian" to TranslateLanguage.ITALIAN,
        "Chinese" to TranslateLanguage.CHINESE,
        "Japanese" to TranslateLanguage.JAPANESE,
        "Korean" to TranslateLanguage.KOREAN,
        "Hindi" to TranslateLanguage.HINDI,
        "Russian" to TranslateLanguage.RUSSIAN,
        "Arabic" to TranslateLanguage.ARABIC
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Setup RecyclerView for history
        setupHistoryRecyclerView()

        // Setup language spinner
        setupLanguageSpinner()

        // Setup seekbar listeners
        setupSeekbars()

        // Setup button listeners
        setupButtonListeners()

        // Setup translate button
        setupTranslateButton()

        // Character count display
        binding.editTextInput.setOnKeyListener { _, _, _ ->
            updateCharacterCount()
            false
        }
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(historyList) { historyItem ->
            binding.editTextInput.setText(historyItem.text)
        }
        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }
    }

    private fun setupLanguageSpinner() {
        val languages = mapOf(
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "Spanish" to "es-ES",
            "French" to "fr-FR",
            "German" to "de-DE",
            "Italian" to "it-IT",
            "Chinese" to "zh-CN",
            "Japanese" to "ja-JP",
            "Korean" to "ko-KR",
            "Hindi" to "hi-IN",
            "Russian" to "ru-RU",
            "Arabic" to "ar"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.keys.toList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages.keys.toList()[position]
                currentLanguageCode = languages[selectedLanguage] ?: "en-US"
                setLanguage(currentLanguageCode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupVoiceSpinner(voiceOptions: List<String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            voiceOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVoice.adapter = adapter

        binding.spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVoiceIndex = position
                if (availableVoices.isNotEmpty() && position < availableVoices.size) {
                    val selectedVoice = availableVoices[position]
                    textToSpeech.setVoice(selectedVoice)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupSeekbars() {
        // Speech rate seekbar
        binding.seekBarRate.max = 30 // 0.5 to 3.5
        binding.seekBarRate.progress = 10 // Default 1.0
        binding.textViewRateValue.text = "1.0"

        binding.seekBarRate.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress / 10.0f)
                binding.textViewRateValue.text = String.format("%.1f", rate)
                textToSpeech.setSpeechRate(rate)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Do nothing
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Do nothing
            }
        })

        // Pitch seekbar
        binding.seekBarPitch.max = 20 // 0.5 to 2.5
        binding.seekBarPitch.progress = 10 // Default 1.0
        binding.textViewPitchValue.text = "1.0"

        binding.seekBarPitch.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress / 10.0f)
                binding.textViewPitchValue.text = String.format("%.1f", pitch)
                textToSpeech.setPitch(pitch)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Do nothing
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Do nothing
            }
        })
    }

    private fun setupButtonListeners() {
        // Speak button
        binding.buttonSpeak.setOnClickListener {
            speakText()
        }

        // Pause/Resume button
        binding.buttonPauseResume.setOnClickListener {
            if (isPaused) {
                resumeSpeech()
            } else {
                pauseSpeech()
            }
        }

        // Stop button
        binding.buttonStop.setOnClickListener {
            stopSpeech()
        }

        // Clear button
        // Clear button
        binding.buttonClear.setOnClickListener {
            binding.editTextInput.text.clear()
            updateCharacterCount()

            // Reset the current text language to English
            currentTextLanguage = TranslateLanguage.ENGLISH

            // Optionally, also reset TTS language to default English
            currentLanguageCode = "en-US"
            setLanguage(currentLanguageCode)

            // Update spinner to English position
            val languageNames = binding.spinnerLanguage.adapter.count
            for (i in 0 until languageNames) {
                val item = binding.spinnerLanguage.getItemAtPosition(i).toString()
                if (item.contains("English (US)")) {
                    binding.spinnerLanguage.setSelection(i)
                    break
                }
            }
        }

        // Paste button
        binding.buttonPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Load button
        binding.buttonLoad.setOnClickListener {
            showLoadTextDialog()
        }

        // Clear history button
        binding.buttonClearHistory.setOnClickListener {
            historyList.clear()
            historyAdapter.notifyDataSetChanged()
            saveHistory()
        }
    }

    private fun setupTranslateButton() {
        binding.buttonTranslate.setOnClickListener {
            showTranslateDialog()
        }
    }

    private fun updateCharacterCount() {
        val currentLength = binding.editTextInput.text.length
        binding.textViewCharacterCount.text = "$currentLength/$MAX_CHARACTER_LIMIT"

        // Disable input if character limit is reached
        if (currentLength >= MAX_CHARACTER_LIMIT) {
            binding.editTextInput.error = "Character limit reached"
        } else {
            binding.editTextInput.error = null
        }
    }

    private fun speakText() {
        val text = binding.editTextInput.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter text to speak", Toast.LENGTH_SHORT).show()
            return
        }

        if (text.length > MAX_CHARACTER_LIMIT) {
            val truncatedText = text.substring(0, MAX_CHARACTER_LIMIT)
            binding.editTextInput.setText(truncatedText)
            Toast.makeText(this, "Text has been truncated to $MAX_CHARACTER_LIMIT characters", Toast.LENGTH_SHORT).show()
            spokenText = truncatedText
        } else {
            spokenText = text
        }

        stopSpeech()

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")

        // Add to history
        addToHistory(spokenText)

        // Start speaking
        textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, "utteranceId")

        // Update UI
        binding.buttonPauseResume.text = "Pause"
        isPaused = false
    }

    private fun pauseSpeech() {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
            isPaused = true
            binding.buttonPauseResume.text = "Resume"
        }
    }

    private fun resumeSpeech() {
        if (isPaused) {
            val textToResume = spokenText.substring(lastPosition)
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")

            textToSpeech.speak(textToResume, TextToSpeech.QUEUE_FLUSH, params, "utteranceId")

            isPaused = false
            binding.buttonPauseResume.text = "Pause"
        }
    }

    private fun stopSpeech() {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
            lastPosition = 0
            isPaused = false
            binding.buttonPauseResume.text = "Pause"
            resetTextHighlighting()
        }
    }

    private fun resetTextHighlighting() {
        val originalText = binding.editTextInput.text.toString()
        binding.editTextInput.setText(originalText)
    }

    private fun highlightText(start: Int, end: Int) {
        val text = binding.editTextInput.text.toString()
        val spannableString = SpannableString(text)

        // Apply highlight
        spannableString.setSpan(
            BackgroundColorSpan(Color.BLACK), // Background color
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(
            ForegroundColorSpan(Color.WHITE), // Ensure text is visible
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.editTextInput.setText(spannableString)

        // Move cursor to highlight position
        binding.editTextInput.setSelection(start, end)

        // Ensure highlighted text is visible
        binding.editTextInput.post {
            binding.editTextInput.bringPointIntoView(start)
        }
    }

    private fun setLanguage(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        val result = textToSpeech.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
        } else {
            // Update available voices for this language
            updateVoiceOptions(locale)
        }
    }

    private fun updateVoiceOptions(locale: Locale) {
        val voices = textToSpeech.voices
        availableVoices = voices?.filter { it.locale.language == locale.language }?.toMutableList() ?: mutableListOf()

        val voiceNames = if (availableVoices.isEmpty()) {
            listOf("Default Voice")
        } else {
            availableVoices.map { it.name.substringAfterLast(":") }
        }

        setupVoiceSpinner(voiceNames)
    }

    private fun addToHistory(text: String) {
        // Don't add duplicates at the top
        if (historyList.isNotEmpty() && historyList[0].text == text) {
            return
        }

        val timestamp = System.currentTimeMillis()
        val historyItem = HistoryItem(text, timestamp)

        // Remove if exists elsewhere and add to top
        historyList.removeIf { it.text == text }
        historyList.add(0, historyItem)

        // Limit history size
        if (historyList.size > 20) {
            historyList.removeAt(historyList.size - 1)
        }

        historyAdapter.notifyDataSetChanged()
        saveHistory()
    }

    private fun saveHistory() {
        try {
            val file = File(filesDir, "tts_history.txt")
            val fos = FileOutputStream(file)

            historyList.forEach { item ->
                val line = "${item.timestamp}|${item.text}\n"
                fos.write(line.toByteArray())
            }

            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHistory() {
        try {
            val file = File(filesDir, "tts_history.txt")
            if (!file.exists()) return

            val fis = FileInputStream(file)
            val scanner = Scanner(fis)

            historyList.clear()
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    val timestamp = parts[0].toLongOrNull() ?: continue
                    val text = parts[1]
                    historyList.add(HistoryItem(text, timestamp))
                }
            }

            scanner.close()
            fis.close()
            historyAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text.toString()

            if (text.length > MAX_CHARACTER_LIMIT) {
                val truncatedText = text.substring(0, MAX_CHARACTER_LIMIT)
                binding.editTextInput.setText(truncatedText)
                Toast.makeText(this, "Text has been truncated to $MAX_CHARACTER_LIMIT characters", Toast.LENGTH_SHORT).show()
            } else {
                binding.editTextInput.setText(text)
            }

            updateCharacterCount()
        } else {
            Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoadTextDialog() {
        val options = arrayOf("Enter Text", "Upload File")

        AlertDialog.Builder(this)
            .setTitle("Load Text")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTextInputDialog()
                    1 -> openFilePicker()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_load_text, null)
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Enter Text")
            .setView(dialogView)
            .setPositiveButton("Load") { _, _ ->
                val editText = dialogView.findViewById<android.widget.EditText>(R.id.editTextDialogInput)
                val text = editText.text.toString()
                processLoadedText(text)
            }
            .setNegativeButton("Cancel", null)
            .create()

        alertDialog.show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, READ_FILE_REQUEST_CODE)
    }

    private fun processLoadedText(text: String) {
        if (text.length > MAX_CHARACTER_LIMIT) {
            val truncatedText = text.substring(0, MAX_CHARACTER_LIMIT)
            binding.editTextInput.setText(truncatedText)
            Toast.makeText(this, "Text has been truncated to $MAX_CHARACTER_LIMIT characters", Toast.LENGTH_SHORT).show()
        } else {
            binding.editTextInput.setText(text)
        }
        updateCharacterCount()
    }


    private var currentTextLanguage = TranslateLanguage.ENGLISH

    private fun showTranslateDialog() {
        val text = binding.editTextInput.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val languageNames = supportedLanguages.keys.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Translate To")
            .setItems(languageNames) { _, which ->
                val targetLanguage = supportedLanguages[languageNames[which]] ?: TranslateLanguage.ENGLISH
                // Don't translate if target is same as current
                if (targetLanguage != currentTextLanguage) {
                    translateText(text, currentTextLanguage, targetLanguage)
                } else {
                    Toast.makeText(this, "Text is already in this language", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun translateText(text: String, sourceLanguage: String, targetLanguage: String) {
        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE

        // Get or create translator
        val translatorKey = "$sourceLanguage-$targetLanguage"
        val translator = translators.getOrPut(translatorKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            Translation.getClient(options)
        }

        // Ensure model is downloaded
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                // Translate text
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        binding.editTextInput.setText(translatedText)
                        binding.progressBar.visibility = View.GONE
                        updateCharacterCount()

                        // Update the current text language
                        currentTextLanguage = targetLanguage

                        // Update TTS language based on target language
                        updateTTSLanguageAfterTranslation(targetLanguage)

                        Toast.makeText(this, "Translation complete", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { exception ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Translation failed: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Failed to download language model: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    // Add this new method to map ML Kit language codes to TTS language codes and update the spinner
    private fun updateTTSLanguageAfterTranslation(targetLanguage: String) {
        // Map from ML Kit language code to TTS language code
        val languageMapping = mapOf(
            TranslateLanguage.ENGLISH to "en-US",
            TranslateLanguage.SPANISH to "es-ES",
            TranslateLanguage.FRENCH to "fr-FR",
            TranslateLanguage.GERMAN to "de-DE",
            TranslateLanguage.ITALIAN to "it-IT",
            TranslateLanguage.CHINESE to "zh-CN",
            TranslateLanguage.JAPANESE to "ja-JP",
            TranslateLanguage.KOREAN to "ko-KR",
            TranslateLanguage.HINDI to "hi-IN",
            TranslateLanguage.RUSSIAN to "ru-RU",
            TranslateLanguage.ARABIC to "ar"
        )

        // Get the corresponding TTS language code
        val ttsLanguageCode = languageMapping[targetLanguage] ?: "en-US"
        currentLanguageCode = ttsLanguageCode

        // Update the spinner to match the new language
        val languages = mapOf(
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "Spanish" to "es-ES",
            "French" to "fr-FR",
            "German" to "de-DE",
            "Italian" to "it-IT",
            "Chinese" to "zh-CN",
            "Japanese" to "ja-JP",
            "Korean" to "ko-KR",
            "Hindi" to "hi-IN",
            "Russian" to "ru-RU",
            "Arabic" to "ar"
        )

        // Find the position in the spinner for this language
        val languageNames = languages.keys.toList()
        val position = languageNames.indexOfFirst { languages[it] == ttsLanguageCode }

        if (position >= 0) {
            // Set spinner selection without triggering the listener
            binding.spinnerLanguage.setSelection(position)
        }

        // Update TTS with the new language
        setLanguage(ttsLanguageCode)
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.US
            val result = textToSpeech.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            } else {
                // Setup utterance progress listener
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String) {
                        runOnUiThread {
                            lastPosition = 0
                            isPaused = false
                            binding.buttonPauseResume.text = "Pause"
                            resetTextHighlighting()
                        }
                    }

                    override fun onError(utteranceId: String) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Error in speech synthesis", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onStart(utteranceId: String) {
                        runOnUiThread {
                            // Initial text highlight
                            val firstSpace = spokenText.indexOf(" ")
                            if (firstSpace > 0) {
                                highlightText(0, firstSpace)
                                lastPosition = firstSpace
                            }
                        }
                    }

                    override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                        runOnUiThread {
                            highlightText(start, end)
                            lastPosition = end
                        }
                    }
                })

                // Initialize voice options
                updateVoiceOptions(locale)

                // Load saved history
                loadHistory()

                binding.buttonPauseResume.isEnabled = true
                binding.buttonStop.isEnabled = true
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == READ_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader().use { it?.readText() ?: "" }
                    processLoadedText(content)
                    inputStream?.close()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                AlertDialog.Builder(this)
                    .setTitle("About")
                    .setMessage("Text to Speech App\nVersion 1.0\n\nConvert text to speech with customizable options.")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        // Clean up translators
        translators.values.forEach { it.close() }

        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val READ_FILE_REQUEST_CODE = 123
    }
}