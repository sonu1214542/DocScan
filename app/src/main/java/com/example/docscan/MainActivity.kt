package com.example.docscan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var pageLimitInputView: EditText
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>
    private var enableGalleryImport = true
    private var scannedPdfPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        pageLimitInputView = findViewById(R.id.pageInput)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            handleActivityResult(result)
        }

        createDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleCreateDocumentResult(result)
        }
    }

    @SuppressLint("StringFormatInvalid")
    fun onScanClick(view: View) {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setGalleryImportAllowed(enableGalleryImport)

        val pageLimitInputText = pageLimitInputView.text.toString()
        if (pageLimitInputText.isNotEmpty()) {
            try {
                val pageLimit = pageLimitInputText.toInt()
                options.setPageLimit(pageLimit)
            } catch (e: NumberFormatException) {
                Toast.makeText(this, getString(R.string.invalid_page_limit_message), Toast.LENGTH_SHORT).show()
                return
            }
        }

        GmsDocumentScanning.getClient(options.build())
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.error_default_message, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("StringFormatInvalid")
    private fun handleActivityResult(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)

        if (resultCode == Activity.RESULT_OK && result != null) {
            result.pdf?.let { pdf ->
                val pdfFile = File(filesDir, "scan.pdf")
                val fos = FileOutputStream(pdfFile)
                contentResolver.openInputStream(pdf.uri)?.use {
                    it.copyTo(fos)
                }
                scannedPdfPath = pdfFile.absolutePath
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, "scanned_document.pdf")
                }
                createDocumentLauncher.launch(intent)
            } ?: run {
                Toast.makeText(this, getString(R.string.error_scanning_message), Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "No PDF path found in scan result")
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.d("MainActivity", "Scan canceled")
        } else {
            Toast.makeText(this, getString(R.string.unknown_error_message), Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Unknown error during scan")
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun handleCreateDocumentResult(activityResult: ActivityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val uri = activityResult.data?.data
            if (uri != null && scannedPdfPath != null) {
                try {
                    writePdfToUri(uri, scannedPdfPath!!)
                    Toast.makeText(this, getString(R.string.document_saved_message), Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, getString(R.string.error_writing_pdf_message, e.message), Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Error writing PDF", e)
                }
            } else {
                Toast.makeText(this, getString(R.string.error_saving_document_message), Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "URI or scanned PDF path is null")
            }
        } else {
            Toast.makeText(this, getString(R.string.error_saving_document_message), Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Error saving document")
        }
    }

    @Throws(IOException::class)
    private fun writePdfToUri(uri: Uri, path: String) {
        val inputStream = FileInputStream(path)
        val outputStream = contentResolver.openOutputStream(uri)

        if (outputStream != null) {
            try {
                inputStream.copyTo(outputStream)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } else {
            throw IOException("Failed to open output stream")
        }
    }
}
