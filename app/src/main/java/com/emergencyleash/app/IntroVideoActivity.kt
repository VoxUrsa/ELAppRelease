package com.emergencyleash.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroVideoActivity : AppCompatActivity() {

    private lateinit var introVideoWebView: WebView
    private lateinit var cancelButton: Button
    private lateinit var continueButton: Button

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_video)

        // Initialize UI components
        introVideoWebView = findViewById(R.id.introVideoWebView)
        cancelButton = findViewById(R.id.cancelButton)
        continueButton = findViewById(R.id.continueButton)

        // Configure WebView settings
        val webSettings = introVideoWebView.settings
        webSettings.javaScriptEnabled = true // Enable JavaScript for Vimeo video playback

        // Use a custom WebViewClient to restrict navigation to trusted URLs
        introVideoWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Only allow URLs from Vimeo (or other trusted domains)
                return if (url.startsWith("https://player.vimeo.com")) {
                    false // Allow this URL to load in the WebView
                } else {
                    true // Block any other URLs
                }
            }
        }
        introVideoWebView.webChromeClient = WebChromeClient()

        // Load Vimeo video
        introVideoWebView.loadUrl("https://player.vimeo.com/video/178617862")

        // Button listeners
        cancelButton.setOnClickListener {
            finish() // Exit activity
        }

        continueButton.setOnClickListener {
            val intent = Intent(this, RegisterTagActivity::class.java)
            startActivity(intent)
        }
    }
}