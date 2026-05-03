package com.pdfpro.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfOperationsManager @Inject constructor(private val context: Context) {

    /**
     * Merge multiple PDFs into one.
     * @param pdfUris List of URIs pointing to PDF files.
     * @param outputFile Destination file for merged PDF.
     * @return true if successful.
     */
    fun mergePdfs(pdfUris: List<Uri>, outputFile: File): Boolean {
        return try {
            val merger = PDFMergerUtility()
            pdfUris.forEach { uri ->
                merger.addSource(uri.toFile())
            }
            merger.setDestinationFileName(outputFile.absolutePath)
            merger.mergeDocuments(null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Split a PDF into separate files, one per page.
     * @param inputUri URI of the PDF to split.
     * @param outputDir Directory where page files will be saved.
     * @return List of generated files (one per page), empty if error.
     */
    fun splitPdf(inputUri: Uri, outputDir: File): List<File> {
        val outputFiles = mutableListOf<File>()
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                val splitter = Splitter()
                splitter.setSplitAtPage(1)
                val pages = splitter.split(document)
                pages.forEachIndexed { index, pageDoc ->
                    val outFile = File(outputDir, "page_${index + 1}.pdf")
                    pageDoc.save(outFile)
                    pageDoc.close()
                    outputFiles.add(outFile)
                }
            }
            outputFiles
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Compress a PDF (re‑save with default compression).
     * @param inputUri URI of the input PDF.
     * @param outputFile Destination file for compressed PDF.
     * @return true if successful.
     */
    fun compressPdf(inputUri: Uri, outputFile: File): Boolean {
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                document.save(outputFile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Encrypt a PDF with a password (owner and user).
     * @param inputUri URI of the PDF to encrypt.
     * @param outputFile Destination file for encrypted PDF.
     * @param userPassword Password for opening the document.
     * @param ownerPassword Password for full permissions.
     * @return true if successful.
     */
    fun encryptPdf(inputUri: Uri, outputFile: File, userPassword: String, ownerPassword: String): Boolean {
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                val accessPermission = AccessPermission()
                accessPermission.canPrint = true
                accessPermission.canModify = false
                val protectionPolicy = StandardProtectionPolicy(ownerPassword, userPassword, accessPermission)
                protectionPolicy.encryptionKeyLength = 128
                document.protect(protectionPolicy)
                document.save(outputFile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Add a text or image watermark to every page.
     * @param inputUri Input PDF URI.
     * @param outputFile Destination file.
     * @param watermarkText Optional text watermark.
     * @param watermarkImage Optional image watermark (Bitmap).
     * @return true if successful.
     */
    fun addWatermark(inputUri: Uri, outputFile: File, watermarkText: String? = null, watermarkImage: Bitmap? = null): Boolean {
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                for (pageIdx in 0 until document.numberOfPages) {
                    val page = document.getPage(pageIdx)
                    val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)

                    if (watermarkText != null) {
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                        contentStream.setNonStrokingColor(200, 200, 200)
                        contentStream.newLineAtOffset(150f, 400f)
                        contentStream.showText(watermarkText)
                        contentStream.endText()
                    }

                    if (watermarkImage != null) {
                        val pdImage = PDImageXObject.createFromFileByExtension(watermarkImage, null, document)
                        contentStream.drawImage(pdImage, 100f, 100f, 200f, 200f)
                    }

                    contentStream.close()
                }
                document.save(outputFile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Digitally sign a PDF using a PKCS#12 keystore.
     * @param inputUri Input PDF URI.
     * @param outputFile Destination file (signed PDF).
     * @param p12InputStream InputStream of the .p12 keystore.
     * @param password Password for the keystore.
     * @param reason Reason for signing.
     * @param location Location of signing.
     * @return true if successful.
     */
    fun signPdf(inputUri: Uri, outputFile: File, p12InputStream: InputStream, password: String, reason: String, location: String): Boolean {
        return try {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(p12InputStream, password.toCharArray())
            val alias = ks.aliases().nextElement()
            val privateKey = ks.getKey(alias, password.toCharArray()) as PrivateKey
            val certChain = ks.getCertificateChain(alias) as Array<X509Certificate>

            val document = Loader.loadPDF(inputUri.toFile())
            val signatureOptions = SignatureOptions()
            signatureOptions.setPage(0)

            val signature = PDSignature()
            signature.filter = PDSignature.FILTER_ADOBE_PPKLITE
            signature.subFilter = PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED
            signature.name = certChain[0].subjectX500Principal.name
            signature.location = location
            signature.reason = reason
            signature.setSignDate(Calendar.getInstance())

            val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
            val digestCalculatorProvider = JcaDigestCalculatorProviderBuilder().build()
            val signerInfoGeneratorBuilder = JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
                .build(contentSigner, certChain[0])

            val signedDataGen = CMSSignedDataGenerator()
            signedDataGen.addSignerInfoGenerator(signerInfoGeneratorBuilder)
            signedDataGen.addCertificates(JcaCertStore(listOf(certChain[0])))

            val externalSigningSupport = document.saveIncrementalForExternalSigning(outputFile)
            val cmsProcessable = CMSProcessableByteArray(externalSigningSupport.content)
            val signedData = signedDataGen.generate(cmsProcessable, true)
            externalSigningSupport.setSignature(signedData.encoded)

            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Convert first page of PDF to Bitmap.
     * @param uri URI of PDF.
     * @param dpi Resolution for rendering.
     * @return Bitmap or null on failure.
     */
    fun pdfToBitmap(uri: Uri, dpi: Int = 150): Bitmap? {
        return try {
            Loader.loadPDF(uri.toFile()).use { document ->
                if (document.numberOfPages == 0) return null
                val page = document.getPage(0)
                page.convertToImage(Bitmap.Config.ARGB_8888, dpi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
