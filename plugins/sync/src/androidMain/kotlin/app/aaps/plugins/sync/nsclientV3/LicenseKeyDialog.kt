package app.aaps.plugins.sync.nsclientV3

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.sync.R
import dev.zacsweers.metro.Inject

/**
 * Dialog for entering Remote Control premium license key.
 */
class LicenseKeyDialog : DialogFragment() {
    
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var licenseValidator: LicenseKeyValidator

    var onLicenseActivated: (() -> Unit)? = null
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        context.injectMetroMembers(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = false
        return inflater.inflate(R.layout.dialog_license_key, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val btnActivate = view.findViewById<Button>(R.id.btn_activate)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val licenseKeyInput = view.findViewById<TextInputEditText>(R.id.license_key_input)
        val errorText = view.findViewById<TextView>(R.id.error_text)

        btnActivate.setOnClickListener {
            val licenseKey = licenseKeyInput.text?.toString() ?: ""
            
            if (licenseKey.isEmpty()) {
                errorText.visibility = View.VISIBLE
                errorText.text = rh.gs(R.string.license_key_required)
                return@setOnClickListener
            }
            
            if (licenseValidator.validateLicenseKey(licenseKey)) {
                // Success
                aapsLogger.info(LTag.NSCLIENT, "[License] License activated successfully")
                dismiss()
                onLicenseActivated?.invoke()
            } else {
                // Failed
                errorText.visibility = View.VISIBLE
                errorText.text = rh.gs(R.string.license_key_invalid)
                licenseKeyInput.text?.clear()
            }
        }
        
        btnCancel.setOnClickListener {
            aapsLogger.info(LTag.NSCLIENT, "[License] License activation cancelled")
            dismiss()
        }
    }
}
