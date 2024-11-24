package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A helper class that provides translation functionality using Google Translate API.
 * Supports automatic language detection and configurable target language.
 */
public class GoogleTranslateHelper {
    // Constants for API endpoint and file paths
    private static final String TRANSLATE_API_URL = "https://translate.googleapis.com/translate_a/single";
    private static final String LOG_FILE = "translator_log.txt";
    private static final String CONFIG_FILE = "translator_config.txt";
    private static String targetLanguage = "en"; // Default target language code

    // Load configuration when class is initialized
    static {
        try {
            Files.write(Paths.get(LOG_FILE), new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log("Translation service started");
        } catch (IOException e) {
            // Ignore errors when creating/clearing log file
        }
        loadConfig();
    }

    private static final String[] VALID_LANGUAGE_CODES = {
            "en", "ru", "es", "fr", "de", "it", "ja", "ko", "zh", "ar", "hi", "pt",
            "bn", "ur", "tr", "vi", "th", "pl", "uk", "ro", "sv", "nl", "fa", "el",
            "he", "hu", "cs", "fi", "da", "no", "id", "ms", "ta", "te", "mr", "kn",
            "ml", "gu", "pa", "am", "my", "sw", "si", "eo", "sr", "bg", "sk", "sl",
            "lt", "lv", "et", "is", "mt", "az", "ka", "hy", "km", "lo", "mk", "sq",
            "bs", "hr", "zu", "xh", "af", "zh_cn"
    };



    /**
     * Loads target language configuration from file.
     * Creates a default config file with 'en' if it doesn't exist.
     */

    private static void loadConfig() {
        try {
            targetLanguage = "en";

            if (Files.exists(Paths.get(CONFIG_FILE))) {
                String configContent = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)), StandardCharsets.UTF_8).trim();
                if (!configContent.isEmpty()) {
                    String langCode = configContent.toLowerCase();
                    boolean isValidCode = false;
                    for (String code : VALID_LANGUAGE_CODES) {
                        if (code.equals(langCode)) {
                            isValidCode = true;
                            break;
                        }
                    }

                    if (isValidCode) {
                        targetLanguage = langCode;
                        log("Target language: " + targetLanguage);
                    } else {
                        log("Invalid language code in config, using default (en)");
                        try {
                            Files.write(Paths.get(CONFIG_FILE), "en".getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            log("Error writing config file: " + e.getMessage());
                        }
                    }
                }
            } else {
                try {
                    Files.write(Paths.get(CONFIG_FILE), "en".getBytes(StandardCharsets.UTF_8));
                    log("Created default config (en)");
                } catch (IOException e) {
                    log("Error creating config file: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log("Config error: " + e.getMessage());
            targetLanguage = "en";
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
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logMessage = String.format("[%s] %s%n", timestamp, message);
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

            // Skip translation if already in target language
            if (result.sourceLanguage.equals(targetLanguage)) {
                log(originalMessage + " : message is already in target language, skipping translation");
                return originalMessage;
            }

            // Use the translation we already have
            String translatedText = result.translatedText;

            if (translatedText != null && !translatedText.isEmpty()) {
                // Format output with original text, language codes, and translation
                String resultText = originalMessage + " (" + targetLanguage + ": " + translatedText + ")";
                log(String.format("%s -> %s: %s", result.sourceLanguage, targetLanguage, resultText));
                return resultText;
            }

            log("Translation failed, returning original");
            return originalMessage;

        } catch (Exception e) {
            log("Error: " + e.getMessage());
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
            String urlBuilder = TRANSLATE_API_URL + "?client=gtx" +
                    "&sl=" + sourceLanguage +
                    "&tl=" + targetLang +
                    "&dt=t&dt=ld" +
                    "&q=" + URLEncoder.encode(text, "UTF-8");

            // Set up HTTP connection
            URL url = new URL(urlBuilder);
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

                // Extract translated text from JSON response
                String translatedText = null;
                if (jsonResponse.contains("\"")) {
                    int startTranslation = jsonResponse.indexOf("\"") + 1;
                    int endTranslation = jsonResponse.indexOf("\"", startTranslation);
                    if (startTranslation > 0 && endTranslation > startTranslation) {
                        translatedText = jsonResponse.substring(startTranslation, endTranslation)
                                .replace("\\u003c", "<")
                                .replace("\\u003e", ">")
                                .replace("\\\"", "\"")
                                .replace("\\'", "'")
                                .replace("\\\\", "\\");
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

                return new TranslationResult(detectedLanguage, translatedText);
            }

        } catch (Exception e) {
            log("API error: " + e.getMessage());
            return null;
        }
    }
}

