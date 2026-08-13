package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var micButton: Button

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    )
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        micButton = findViewById(R.id.micButton)

        requestNeededPermissions()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        micButton.setOnClickListener {
            startListening()
        }
    }

    private fun requestNeededPermissions() {
        val notGranted = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // বাংলা কমান্ড শুনতে চাইলে "bn-BD", ইংরেজির জন্য "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "বলুন...")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "শুনছি..."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.get(0) ?: ""
                statusText.text = "কমান্ড: $command"
                handleCommand(command)
            }

            override fun onError(error: Int) {
                statusText.text = "শুনতে সমস্যা হয়েছে, আবার চেষ্টা করুন।"
            }

            // অব্যবহৃত কলব্যাক
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    /**
     * এখানে মূল "বুদ্ধিমত্তা" - বলা কথা থেকে কী করতে হবে সেটা বের করা
     */
    private fun handleCommand(rawCommand: String) {
        val command = rawCommand.lowercase().trim()

        when {
            // উদাহরণ: "রহিমকে হোয়াটসঅ্যাপে কল করো" বা "হোয়াটসঅ্যাপ কল রহিম"
            command.contains("হোয়াটসঅ্যাপ") || command.contains("whatsapp") -> {
                val name = extractName(command)
                if (name.isNotEmpty()) {
                    val number = findPhoneNumberByName(name)
                    if (number != null) {
                        openWhatsAppCall(number)
                    } else {
                        Toast.makeText(this, "$name কন্টাক্টে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "কার সাথে কথা বলতে চান বুঝতে পারিনি", Toast.LENGTH_SHORT).show()
                }
            }

            // উদাহরণ: "রহিমকে কল করো" বা "কল করো রহিম"
            command.contains("কল") || command.contains("call") -> {
                val name = extractName(command)
                if (name.isNotEmpty()) {
                    val number = findPhoneNumberByName(name)
                    if (number != null) {
                        makeNormalCall(number)
                    } else {
                        Toast.makeText(this, "$name কন্টাক্টে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "কাকে কল করতে চান বুঝতে পারিনি", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                Toast.makeText(this, "কমান্ডটি বুঝতে পারিনি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * খুব সাধারণ নাম বের করার লজিক - "কল", "করো", "হোয়াটসঅ্যাপ", "কে" ইত্যাদি শব্দ বাদ দিয়ে বাকিটা নাম ধরে নেওয়া হচ্ছে।
     * বাস্তব ব্যবহারের জন্য এটি আরও উন্নত করা দরকার (NLP ব্যবহার করে)।
     */
    private fun extractName(command: String): String {
        val stopWords = listOf("কে", "কল", "করো", "কর", "হোয়াটসঅ্যাপে", "হোয়াটসঅ্যাপ", "এ", "call", "whatsapp")
        var result = command
        stopWords.forEach { result = result.replace(it, " ") }
        return result.trim()
    }

    private fun findPhoneNumberByName(name: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(numberIndex)
            }
        }
        return null
    }

    private fun makeNormalCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "কল করার অনুমতি নেই", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppCall(number: String) {
        // নাম্বার থেকে + এবং স্পেস বাদ দেওয়া, দেশের কোড সহ ফরম্যাট দরকার হতে পারে
        val cleanNumber = number.replace(" ", "").replace("-", "")
        try {
            val uri = Uri.parse("https://wa.me/$cleanNumber")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.whatsapp")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp খুলতে সমস্যা হয়েছে। WhatsApp ইনস্টল আছে কিনা দেখুন।", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
