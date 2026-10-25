package com.ramka.app.ui.qr

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size
import kotlin.math.min

/**
 * Экран сканера QR (zxing-android-embedded).
 *
 * Встроенная [CaptureActivity] объявлена в манифесте библиотеки с `screenOrientation="sensorLandscape"` — из-за
 * этого сканер всегда открывался горизонтально, и телефон приходилось переворачивать. Эта Activity объявлена
 * в манифесте приложения с `fullUser`: ориентация следует за положением телефона и системной блокировкой
 * поворота (вертикально — вертикальный сканер, горизонтально — горизонтальный). Поворот камеры и разбор
 * кадра в обеих ориентациях делает сама библиотека. Блокировку ориентации при запуске отключает
 * `ScanOptions.setOrientationLocked(false)` (см. QrScreen).
 *
 * Рамка наведения — квадрат [FRAME_DP] dp по центру, но не больше [FRAME_MAX_SHARE] короткой стороны
 * экрана (на маленьких экранах и в горизонтали она не вылезает за края).
 */
class PortraitCaptureActivity : CaptureActivity() {

    override fun initializeContent(): DecoratedBarcodeView {
        val view = super.initializeContent()
        val metrics = resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        val side = min((FRAME_DP * metrics.density).toInt(), (shortSide * FRAME_MAX_SHARE).toInt())
        view.barcodeView.framingRectSize = Size(side, side)
        return view
    }

    private companion object {
        const val FRAME_DP = 250
        const val FRAME_MAX_SHARE = 0.7f
    }
}
