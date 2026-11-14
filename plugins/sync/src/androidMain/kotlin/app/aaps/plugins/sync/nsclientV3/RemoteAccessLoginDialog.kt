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
 * Login dialog for Remote Control access.
 */
class RemoteAccessLoginDialog : DialogFragment() {
    
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var authManager: RemoteAccessAuthManager

    var onUnlocked: (() -> Unit)? = null
    
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
        return inflater.inflate(R.layout.dialog_remote_access_login, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val btnUnlock = view.findViewById<Button>(R.id.btn_unlock)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.password_input)
        val errorText = view.findViewById<TextView>(R.id.error_text)

        btnUnlock.setOnClickListener {
            val password = passwordInput.text?.toString() ?: ""
            
            if (password.isEmpty()) {
                errorText.visibility = View.VISIBLE
                errorText.text = rh.gs(R.string.password_required)
                return@setOnClickListener
            }
            
            if (authManager.verifyPassword(password)) {
                // Success
                aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Access granted")
                dismiss()
                onUnlocked?.invoke()
            } else {
                // Failed
                errorText.visibility = View.VISIBLE
                errorText.text = rh.gs(R.string.remote_access_invalid_password)
                passwordInput.text?.clear()
            }
        }
        
        btnCancel.setOnClickListener {
            aapsLogger.info(LTag.NSCLIENT, "[RemoteAccess] Access cancelled")
            dismiss()
        }
    }
}
