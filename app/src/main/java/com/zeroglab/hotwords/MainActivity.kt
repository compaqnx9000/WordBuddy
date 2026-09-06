package com.zeroglab.hotwords

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroglab.hotwords.ui.HotWordsRoot
import com.zeroglab.hotwords.ui.VocabViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: VocabViewModel = viewModel()
            HotWordsRoot(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onExit = { finish() },
            )
        }
    }
}
