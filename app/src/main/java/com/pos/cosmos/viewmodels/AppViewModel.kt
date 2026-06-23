package com.pos.cosmos.viewmodels

import androidx.lifecycle.ViewModel
import com.pos.cosmos.models.Config
import com.pos.cosmos.models.MobileUiConfig
import com.pos.cosmos.models.PrinterInfo
import com.pos.cosmos.models.User
import com.pos.cosmos.models.UserPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {

    // Image bill cart persistence (survives navigation)
    private val _imageBillCart = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val imageBillCart: StateFlow<List<Map<String, Any>>> = _imageBillCart.asStateFlow()

    private val _imageBillCustomer = MutableStateFlow<Map<String, Any>?>(null)
    val imageBillCustomer: StateFlow<Map<String, Any>?> = _imageBillCustomer.asStateFlow()

    private val _imageBillCashPaid = MutableStateFlow("")
    val imageBillCashPaid: StateFlow<String> = _imageBillCashPaid.asStateFlow()

    private val _imageBillDiscount = MutableStateFlow("")
    val imageBillDiscount: StateFlow<String> = _imageBillDiscount.asStateFlow()

    private val _imageBillDiscountValue = MutableStateFlow("")
    val imageBillDiscountValue: StateFlow<String> = _imageBillDiscountValue.asStateFlow()

    fun setImageBillCart(cart: List<Map<String, Any>>) { _imageBillCart.value = cart }
    fun setImageBillCustomer(customer: Map<String, Any>?) { _imageBillCustomer.value = customer }
    fun setImageBillCashPaid(cash: String) { _imageBillCashPaid.value = cash }
    fun setImageBillDiscount(disc: String) { _imageBillDiscount.value = disc }
    fun setImageBillDiscountValue(discVal: String) { _imageBillDiscountValue.value = discVal }
    fun clearImageBillCart() {
        _imageBillCart.value = emptyList()
        _imageBillCustomer.value = null
        _imageBillCashPaid.value = ""
        _imageBillDiscount.value = ""
        _imageBillDiscountValue.value = ""
    }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _verifiedUserName = MutableStateFlow<String?>(null)
    val verifiedUserName: StateFlow<String?> = _verifiedUserName.asStateFlow()

    private val _connectedPrinter = MutableStateFlow<PrinterInfo?>(null)
    val connectedPrinter: StateFlow<PrinterInfo?> = _connectedPrinter.asStateFlow()

    private val _isBluetoothPrinting = MutableStateFlow(false)
    val isBluetoothPrinting: StateFlow<Boolean> = _isBluetoothPrinting.asStateFlow()

    private val _config = MutableStateFlow<Config?>(null)
    val config: StateFlow<Config?> = _config.asStateFlow()

    private val _userPermission = MutableStateFlow<UserPermission?>(null)
    val userPermission: StateFlow<UserPermission?> = _userPermission.asStateFlow()

    private val _mobileUiConfig = MutableStateFlow<MobileUiConfig>(MobileUiConfig())
    val mobileUiConfig: StateFlow<MobileUiConfig> = _mobileUiConfig.asStateFlow()

    fun setToken(token: String?) {
        _token.value = token
    }

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
    }

    fun setVerifiedUserName(userName: String?) {
        _verifiedUserName.value = userName
    }

    fun setConnectedPrinter(printer: PrinterInfo?) {
        _connectedPrinter.value = printer
    }

    fun setBluetoothPrinting(printing: Boolean) {
        _isBluetoothPrinting.value = printing
    }

    fun setConfig(config: Config?) {
        _config.value = config
    }

    fun setUserPermission(permission: UserPermission?) {
        _userPermission.value = permission
    }

    fun setMobileUiConfig(config: MobileUiConfig) {
        _mobileUiConfig.value = config
    }

    fun logout() {
        _token.value = null
        _currentUser.value = null
        _verifiedUserName.value = null
        _config.value = null
        _userPermission.value = null
    }
}
