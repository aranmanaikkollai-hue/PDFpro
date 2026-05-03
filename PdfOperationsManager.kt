package com.pdfpro.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toFile
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfOperationsManager @Inject constructor(private val context: Context) {

    // ------------------- Merge PDFs -------------------
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

    // ------------------- Split PDF -------------------
    fun splitPdf(inputUri: Uri, outputDir: File): List<File> {
        val outputFiles = mutableListOf<File>()
        Loader.loadPDF(inputUri.toFile()).use { document ->
            val splitter = Splitter()
            splitter.setSplitAtPage(1)  // each page to separate PDF
            val pages = splitter.split(document)
            pages.forEachIndexed { index, pageDoc ->
                val outFile = File(outputDir, "page_${index + 1}.pdf")
                pageDoc.save(outFile)
                pageDoc.close()
                outputFiles.add(outFile)
            }
        }
        return outputFiles
    }

    // ------------------- Compress (reduce size) -------------------
    fun compressPdf(inputUri: Uri, outputFile: File): Boolean {
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                // Save with default compression (already applied by PDFBox)
                // To further compress, we can write with a custom strategy:
                document.save(outputFile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ------------------- Encrypt with password -------------------
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

    // ------------------- Add Watermark (text or image) -------------------
    fun addWatermark(inputUri: Uri, outputFile: File, watermarkText: String? = null, watermarkImage: Bitmap? = null): Boolean {
        return try {
            Loader.loadPDF(inputUri.toFile()).use { document ->
                for (pageIdx in 0 until document.numberOfPages) {
                    val page = document.getPage(pageIdx)
                    val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)

                    if (watermarkText != null) {
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                        contentStream.setNonStrokingColor(200, 200, 200) // light grey
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

    // ------------------- Digital Signature (basic PKCS#12) -------------------
    // Note: PDFBox requires a keystore (.p12) with private key + cert.
    fun signPdf(inputUri: Uri, outputFile: File, p12InputStream: InputStream, password: String, reason: String, location: String): Boolean {
        return try {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(p12InputStream, password.toCharArray())
            val alias = ks.aliases().nextElement()
            val privateKey = ks.getKey(alias, password.toCharArray()) as PrivateKey
            val certChain = ks.getCertificateChain(alias) as Array<X509Certificate>

            val document = Loader.loadPDF(inputUri.toFile())
            val signatureOptions = SignatureOptions()
            signatureOptions.setPage(0) // sign first page

            val signature = PDSignature()
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
            signature.setName(certChain[0].subjectX500Principal.name)
            signature.setLocation(location)
            signature.setReason(reason)
            signature.setSignDate(Calendar.getInstance())

            // Sign using Bouncy Castle CMS
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
            val encodedSignature = signedData.encoded
            externalSigningSupport.setSignature(encodedSignature)

            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ------------------- Export image from first page -------------------
    fun pdfToBitmap(uri: Uri, dpi: Int = 150): Bitmap? {
        return try {
            Loader.loadPDF(uri.toFile()).use { document ->
                val page = document.getPage(0)
                val image = page.convertToImage(Bitmap.Config.ARGB_8888, dpi)
                image
            }
        } catch (e: Exception) {
            null
        }
    }
}
