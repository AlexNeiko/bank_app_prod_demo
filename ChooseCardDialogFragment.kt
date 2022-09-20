
class ChooseCardDialogFragment(listener: BottomSheetListener, cards: List<DepositCard>) : BottomSheetDialogFragment() {


    lateinit var binding: ItemChooseCardBinding
    private val _cards = cards


    private var mBottomSheetListener: BottomSheetListener?=null
     init {
         this.mBottomSheetListener = listener
     }




    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setDimAmount(0.4f) /** Set dim amount here */
            setOnShowListener {
                val bottomSheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
                bottomSheet.setBackgroundResource(android.R.color.transparent)
                addCardsToView(bottomSheet) /** add cards to View programmatically */
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        binding = ItemChooseCardBinding.bind(inflater.inflate(R.layout.item_choose_card, container))
        return binding.root

    }


    interface BottomSheetListener{
        fun chooseCardClick(saved: DepositCard)
        fun newCardClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            mBottomSheetListener = context as BottomSheetListener?
        }
        catch (e: ClassCastException){
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "ResourceType")
    private fun addCardsToView(bottomSheet: FrameLayout) {
        val mInflater = requireContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rootView = bottomSheet.getChildAt(0) as ConstraintLayout
        val injectedView = rootView.getChildAt(1) as LinearLayout

        for (i in 1.._cards.size) {

            val rootView = mInflater.inflate(R.layout.item_choosen_card_element, null) as ConstraintLayout

            rootView.setOnClickListener {
                mBottomSheetListener?.chooseCardClick(_cards[i-1])
            }

            /** Set view */
            val icon = rootView.getChildAt(0) as ImageView
            val cardTextView = rootView.getChildAt(2) as TextView
            /** Set data */
            cardTextView.text = _cards[i-1].maskedPan
            icon.setImageDrawable(resources.getDrawable(_cards[i-1].paySystemLocalDrawable))
            /** Add to View container */
            injectedView.addView(rootView, injectedView.childCount)
        }


        val rootViewEmpty = mInflater.inflate(R.layout.item_choosen_card_empty, null) as ConstraintLayout
        rootViewEmpty.setOnClickListener {
            mBottomSheetListener?.newCardClick()
        }
        injectedView.addView(rootViewEmpty, injectedView.childCount)

    }
}
