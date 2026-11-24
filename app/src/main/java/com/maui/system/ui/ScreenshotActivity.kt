package com.maui.system.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.maui.system.R
import java.io.File
import java.io.FileOutputStream

/**
 * Takes a real screenshot of the screen.
 *
 * Android never lets a background app grab the screen, so the app asks for
 * the capture permission through the system's own consent dialog, takes the
 * frame and saves it as a PNG in the public Pictures/Maui folder.
 */
class ScreenshotActivity : AppCompatActivity() {

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCapture()
    }

    private fun requestCapture() {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        } catch (t: Throwable) {
            toast(getString(R.string.capture_failed))
            finish()
        }
    }

    @Deprecated("The system consent dialog still uses the old callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) {
            finish()
            return
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = manager.getMediaProjection(resultCode, data) ?: run {
            toast(getString(R.string.capture_failed))
            finish()
            return
        }
        projection = p
        capture()
    }

    private fun capture() {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = resources.displayMetrics.density

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            val p = projection ?: return
            virtualDisplay = p.createVirtualDisplay(
                "maui-capture",
                width,
                height,
                density.toInt(),
                autoMirrorFlag(),
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val bitmap = toBitmap(image)
                    val file = save(bitmap)
                    toast(
                        if (file != null) getString(R.string.capture_saved, file.name)
                        else getString(R.string.capture_failed)
                    )
                } finally {
                    image.close()
                    release()
                    finish()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            toast(getString(R.string.capture_failed))
            release()
            finish()
        }
    }

    /**
     * The flag that makes the virtual display mirror the real screen. It is
     * public from Android 12 onwards; on older versions the plain virtual
     * display already shows the whole screen.
     */
    private fun autoMirrorFlag(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
        } else {
            0
        }
    } catch (t: Throwable) {
        0
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / plane.pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    /** Saves the PNG where the gallery and the file manager can see it. */
    private fun save(bitmap: Bitmap): File? {
        return try {
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                "Maui"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "maui-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (t: Throwable) {
            null
        }
    }

    private fun release() {
        try { virtualDisplay?.release() } catch (t: Throwable) { }
        try { imageReader?.close() } catch (t: Throwable) { }
        try { projection?.stop() } catch (t: Throwable) { }
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CAPTURE = 4711

        /** Opens the capture consent screen. */
        fun open(context: android.content.Context) {
            context.startActivity(
                Intent(context, ScreenshotActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
