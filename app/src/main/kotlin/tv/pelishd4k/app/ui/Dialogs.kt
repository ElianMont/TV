package tv.pelishd4k.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import tv.pelishd4k.app.R
import tv.pelishd4k.app.databinding.DialogRemoteBinding
import tv.pelishd4k.app.remote.RemoteManager

object Dialogs {

    fun edit(ctx: Context, title: String, current: String, hint: String? = null, onOk: (String) -> Unit) {
        val et = EditText(ctx).apply {
            setText(current)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            this.hint = hint
            setTextColor(ctx.getColor(R.color.text_primary))
            setHintTextColor(ctx.getColor(R.color.text_secondary))
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(ctx, R.style.TvDialog)
            .setTitle(title)
            .setView(et)
            .setPositiveButton(R.string.ok) { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun choice(ctx: Context, title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(ctx, R.style.TvDialog)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { d, which ->
                onPick(which)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun confirm(ctx: Context, title: String, message: String?, onOk: () -> Unit) {
        AlertDialog.Builder(ctx, R.style.TvDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Diálogo con QR + PIN para emparejar el iPhone. */
    fun remoteInfo(ctx: Context) {
        val b = DialogRemoteBinding.inflate(LayoutInflater.from(ctx))
        fun refresh() {
            val addr = RemoteManager.address()
            when {
                !tv.pelishd4k.app.settings.remoteEnabled -> {
                    b.address.text = ctx.getString(R.string.remote_off)
                    b.qr.setImageDrawable(null)
                }
                addr == null -> {
                    b.address.text = ctx.getString(R.string.remote_no_network)
                    b.qr.setImageDrawable(null)
                }
                else -> {
                    b.address.text = addr
                    b.qr.setImageBitmap(qr(addr))
                }
            }
            b.pin.text = RemoteManager.auth.pin
        }
        refresh()
        b.btnNewPin.setOnClickListener {
            RemoteManager.auth.regeneratePin()
            refresh()
        }
        AlertDialog.Builder(ctx, R.style.TvDialog)
            .setView(b.root)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun qr(text: String): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
        val quiet = 2
        val scale = 8
        val w = matrix.width
        val h = matrix.height
        val bmp = Bitmap.createBitmap((w + quiet * 2) * scale, (h + quiet * 2) * scale, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val px = IntArray(scale * scale) { Color.BLACK }
        for (y in 0 until h) for (x in 0 until w) {
            if (matrix.get(x, y)) bmp.setPixels(px, 0, scale, (x + quiet) * scale, (y + quiet) * scale, scale, scale)
        }
        return bmp
    }
}
