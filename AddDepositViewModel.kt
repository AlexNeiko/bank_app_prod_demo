
@HiltViewModel
class AddDepositViewModel @Inject constructor(private val cardUseCase: CardUseCase,
                                              private val depositUseCase: DepositUseCase) :
    ViewModel() {
    val savedCards = MutableLiveData<List<DepositCard>>() /** to get remote Saved card, if exist */
    val balanceModel = MutableLiveData<BalanceModel>() /** Available balance from API */
    val transactionStepsModel = MutableLiveData<Map<String, Any>>()
    val transactionShowDialog = MutableLiveData<String>()
    val isLoader = MutableLiveData(false)
    private var isGetBalance = false
    val infoCard = MutableLiveData<InfoModel>()
    val errorMsg = MutableLiveData<String>()
    var transactionIdStr = ""
    var processAreqModel = ProcessAreqModel()

    init {
        getInfo()
    }


    @SuppressLint("ResourceType")
    private fun getSavedCards() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                /** step 1 -> get saved cards from API, if exists */
                depositUseCase.getSavedCards(infoCard.value!!.card_id)
            }.onSuccess { apiSavedCards ->
                withContext(Dispatchers.IO) {
                if (!apiSavedCards.external?.sender.isNullOrEmpty()) {
                    /** step 1.2 -> get Pay systems and rebuild local data */
                    depositUseCase.getPaySystems().onSuccess {  apiPaySystems ->
                        withContext(Dispatchers.Main) { savedCards.value = buildDepositCardForUi(apiSavedCards, apiPaySystems) }
                    }
                } else {
                    withContext(Dispatchers.Main) { savedCards.value = mutableListOf() }
                }}

            }.onFailure {
                withContext(Dispatchers.Main) { savedCards.value = mutableListOf() }
            }
        }
    }


    private fun buildDepositCardForUi(apiSavedCards: CardsModel, apiPaySystems: PaySystemsModel): MutableList<DepositCard> {
        val cards: MutableList<DepositCard> = mutableListOf()
        apiSavedCards.external?.sender?.forEach { item ->
            var maskedPan = ""
            var image = R.drawable.ic_card_type_def
            apiPaySystems.items?.forEach { paySystemItem ->
               var paySystemStr = ""
               if (item?.paySystemId.equals(paySystemItem?.id)) paySystemStr = paySystemItem?.type.toString()
               when(paySystemStr) {
                   PaySystemsTypes.PAY_SYSTEM_MASTERCARD -> image = R.drawable.ic_card_type_mc
                   PaySystemsTypes.PAY_SYSTEM_VISA -> image = R.drawable.ic_card_type_visa
                   PaySystemsTypes.PAY_SYSTEM_MIR -> image = R.drawable.ic_card_type_mir
               }
                maskedPan = "•••• " + item?.maskedPan.toString().takeLast(4)
           }
            cards.add(DepositCard(
                id = item?.id.toString(),
                expDate = item?.expireDate.toString(),
                maskedPan = maskedPan,
                paySystemId = item?.paySystemId.toString(),
                paySystemLocalDrawable = image))
        }
        return cards
    }



    /** 1. Payment stage. Push to API and get transaction id */
    fun createDepositPay(depositPaymentModel: DepositPaymentModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                depositUseCase.getDepositTransactionId(infoCard.value!!.card_id, depositPaymentModel.toDepositModel())
            }.onSuccess { transactionId ->
                withContext(Dispatchers.Main) {
                    transactionIdStr = transactionId.transactionId
                    transactionStepsModel.value = mapOf(Contracts.DEPOSIT_TRANSACTION_ID to transactionId.transactionId)
                    /** 2. Payment stage. receiving process status */
                    getProcessStatus(transactionId.transactionId)
                }
            }.onFailure {
                isLoader.value = false
                errorMsg.value = it.message
            }
        }
    }

    /***
     * Getting card information
     */
    fun getInfo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cardUseCase.getInfo()
            }.onSuccess {
                infoCard.value = it
                isGetBalance = false
                onGetBalance(it.card_id, "last")
                getSavedCards()
            }
        }
    }

    /** Function for getting the card balance */
    private fun onGetBalance(card_id:String, state:String) {
        isLoader.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cardUseCase.getBalance(card_id,state)
            }.onSuccess {
                if(isGetBalance)
                {
                    isLoader.value = false
                    balanceModel.value = it /** receive balance to UI */
                    return@launch
                }

                if (it.available_balance.isNotEmpty())
                {
                    isLoader.value = false
                    balanceModel.value = it /** receive balance to UI */
                }
                else{
                    isGetBalance = true
                    onGetBalance(card_id, "actual")
                }
            }.onFailure {
                isLoader.value = false
                errorMsg.value = it.message
            }
        }
    }

    private fun getProcessStatus(transactionId: String) {
        viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                depositUseCase.getProcessStatus(transactionId)
            }.onSuccess { processStatus ->

                /** Steps of payment */
                when(processStatus.data?.nextStep.toString()) {
                    DEPOSIT_PROCESS_STATUS_PARES -> {
                        /** call to UI for Web View */
                        transactionStepsModel.value = mapOf(processStatus.data?.nextStep.toString() to processStatus)
                    }
                    DEPOSIT_PROCESS_STATUS_AREQ -> {
                        /** call to UI 3dSecure v 2 */
                        transactionStepsModel.value = mapOf(processStatus.data?.nextStep.toString() to processStatus)
                    }
                    DEPOSIT_PROCESS_STATUS_LAST_STEP -> {
                        /** Verification of an approved transfer */
                        depositStatus()
                    }
                }
            }.onFailure {
                getProcessStatus(transactionId) /** Knock until we get the data */
            }
        }
    }



    fun processPARES(pares: String) {
        viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                depositUseCase.getProcessPares(
                    transaction_id = transactionIdStr,
                    pares = pares)
            }.onSuccess {
                getProcessStatus(transactionIdStr)
            }.onFailure {
                transactionShowDialog.value = Contracts.DEPOSIT_PROCESS_RESULT_LOCAL_ERROR
            }
        }
    }

    fun processAREQ() {
       viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                depositUseCase.getProcessAreq(
                    transaction_id = transactionIdStr,
                    model = processAreqModel)
            }.onSuccess {
                delay(400)
                getProcessStatus(transactionIdStr)
            }.onFailure {
                transactionShowDialog.value = Contracts.DEPOSIT_PROCESS_RESULT_LOCAL_ERROR
            }
        }
    }


    fun extAREQ3DSecurePostMethod(url: String, threeDSMethodData: String) {
        viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                Log.d("alex", "extAREQ3DSecurePostMethod START")
                depositUseCase.getExtAREQ3DSecurePostMethod(url, threeDSMethodData)
            }.onSuccess {
                Log.d("alex", "extAREQ3DSecurePostMethod onSuccess")
                processAREQ()
            }.onFailure {
                Log.d("alex", "extAREQ3DSecurePostMethod ERROR")
                transactionShowDialog.value = Contracts.DEPOSIT_PROCESS_RESULT_LOCAL_ERROR
            }
        }
    }


    fun depositStatus() {
        viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                depositUseCase.getDepositStatus(transactionIdStr)
            }.onSuccess {
                transactionShowDialog.value = Contracts.DEPOSIT_STATUS_CREATED
            }.onFailure {
                transactionShowDialog.value = Contracts.DEPOSIT_PROCESS_RESULT_LOCAL_ERROR
            }
        }
    }

}
