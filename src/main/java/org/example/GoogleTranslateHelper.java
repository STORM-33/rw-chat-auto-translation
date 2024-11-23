package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * A helper class that provides translation functionality using Google Translate API.
 * Supports automatic language detection and configurable target language.
 * Includes logging capabilities for debugging and monitoring.
 */
public class GoogleTranslateHelper {
    // Constants for API endpoint and file paths
    private static final String TRANSLATE_API_URL = "https://translate.googleapis.com/translate_a/single";
    private static final String LOG_FILE = "translator_log.txt";
    private static final String CONFIG_FILE = "translator_config.txt";
    private static String targetLanguage = "en"; // Default target language code

    // Load configuration when class is initialized
    static {
        loadConfig();
    }

    /**
     * Loads target language configuration from file.
     * Creates a default config file with 'en' if it doesn't exist.
     */
    private static void loadConfig() {
        try {
            if (Files.exists(Paths.get(CONFIG_FILE))) {
                // Read bytes and convert to String
                String configContent = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)), StandardCharsets.UTF_8).trim();
                if (!configContent.isEmpty()) {
                    targetLanguage = configContent.toLowerCase();
                    log("Loaded target language from config: " + targetLanguage);
                }
            } else {
                // Create config file with default value if it doesn't exist
                Files.write(Paths.get(CONFIG_FILE), "en".getBytes(StandardCharsets.UTF_8)); // No need for StandardOpenOption
                log("Created default config file with 'en' target language");
            }
        } catch (IOException e) {
            log("Error loading config: " + e.getMessage());
        }
    }

    /**
     * Logs messages to file with timestamp.
     * Creates log file if it doesn't exist.
     * Appends new log entries to existing file.
     *
     * @param message Message to log
     */
    private static void log(String message) {
        try {
            String logMessage = System.currentTimeMillis() + ":... " + message + "\n";
            // Append log message to file
            Files.write(Paths.get(LOG_FILE), logMessage.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Ignore logging errors
        }
    }

    /**
     * Main method for processing messages. Handles translation workflow:
     * 1. Validates input
     * 2. Detects source language
     * 3. Performs translation if needed
     * 4. Formats output with original and translated text
     *
     * @param originalMessage The message to be translated
     * @return Processed message with translation (if applicable)
     */
    public static String processMessage(String originalMessage) {
        try {
            log("Processing message: " + originalMessage);

            // Skip empty or very short messages
            if (originalMessage == null || originalMessage.trim().length() < 2) {
                return originalMessage;
            }

            // Detect language and get initial translation
            TranslationResult result = makeTranslationRequest(originalMessage, "auto", targetLanguage);
            if (result == null) {
                log("Failed to get translation result");
                return originalMessage;
            }

            log("Detected source language: " + result.sourceLanguage);

            // Skip translation if already in target language
            if (result.sourceLanguage.equals(targetLanguage)) {
                log("Message is already in target language, skipping translation");
                return originalMessage;
            }

            // Use the translation we already have
            String translatedText = result.translatedText;

            if (translatedText != null && !translatedText.isEmpty()) {
                // Format output with original text, language codes, and translation
                String resultText = originalMessage + " (" + result.sourceLanguage + "→" + targetLanguage + ": " + translatedText + ")";
                log("Translated result: " + resultText);
                return resultText;
            }

            log("Translation failed, returning original");
            return originalMessage;

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
            return originalMessage;
        }
    }

    /**
     * Internal class to store translation results
     */
    private static class TranslationResult {
        String sourceLanguage;
        String translatedText;

        TranslationResult(String sourceLanguage, String translatedText) {
            this.sourceLanguage = sourceLanguage;
            this.translatedText = translatedText;
        }
    }

    /**
     * Makes HTTP request to Google Translate API and parses the response.
     *
     * @param text Text to translate
     * @param sourceLanguage Source language code or "auto" for detection
     * @param targetLang Target language code
     * @return TranslationResult object containing detected language and translated text
     */
    private static TranslationResult makeTranslationRequest(String text, String sourceLanguage, String targetLang) {
        try {
            // Build API request URL with parameters
            StringBuilder urlBuilder = new StringBuilder(TRANSLATE_API_URL);
            urlBuilder.append("?client=gtx");
            urlBuilder.append("&sl=").append(sourceLanguage);
            urlBuilder.append("&tl=").append(targetLang);
            urlBuilder.append("&dt=t&dt=ld");
            urlBuilder.append("&q=").append(URLEncoder.encode(text, "UTF-8"));

            // Set up HTTP connection
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            // Read API response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String jsonResponse = response.toString();
                log("Raw API Response: " + jsonResponse);

                // Extract translated text from JSON response
                String translatedText = null;
                if (jsonResponse.contains("\"")) {
                    int startTranslation = jsonResponse.indexOf("\"") + 1;
                    int endTranslation = jsonResponse.indexOf("\"", startTranslation);
                    if (startTranslation > 0 && endTranslation > startTranslation) {
                        translatedText = jsonResponse.substring(startTranslation, endTranslation);
                    }
                }

                // Extract detected language code from JSON response
                String detectedLanguage = "en"; // default
                String languageIdentifier = "\"";
                int lastCommaIndex = jsonResponse.lastIndexOf(",");
                if (lastCommaIndex > 0) {
                    int langStartIndex = jsonResponse.lastIndexOf("[\"", lastCommaIndex);
                    if (langStartIndex > 0) {
                        int langEndIndex = jsonResponse.indexOf("\"]", langStartIndex);
                        if (langEndIndex > langStartIndex) {
                            detectedLanguage = jsonResponse.substring(langStartIndex + 2, langEndIndex);
                        }
                    }
                }

                log("Extracted translation: " + translatedText);
                log("Extracted language: " + detectedLanguage);

                return new TranslationResult(detectedLanguage, translatedText);
            }

        } catch (Exception e) {
            log("Translation request error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

