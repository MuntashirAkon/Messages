package com.moez.QKSMS.feature.conversationinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.moez.QKSMS.R

class ConversationInfoDialog: BottomSheetDialogFragment() {
    companion object {
        val TAG: String = ConversationInfoDialog::class.java.simpleName
    }

    private lateinit var router: Router

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.activity_container, container, false)
        router = Conductor.attachRouter(requireActivity(), v as ViewGroup, savedInstanceState)
        if (!router.hasRootController()) {
            val threadId = requireArguments().getLong("threadId")
            router.setRoot(RouterTransaction.with(ConversationInfoController(threadId)))
        }
        return v
    }

    override fun onDetach() {
        router.handleBack()
        super.onDetach()
    }
}
