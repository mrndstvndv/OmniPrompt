package com.mrndstvndv.search.provider.calculator

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.util.CalculatorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalculatorProvider(
    private val activity: ComponentActivity
) : Provider {

    override val id: String = "calculator"
    override val displayName: String = activity.getString(R.string.provider_calculator)

    override fun canHandle(query: Query): Boolean {
        return CalculatorEngine.isExpression(query.trimmedText)
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val expression = query.trimmedText
        val result = CalculatorEngine.compute(expression) ?: return emptyList()

        val action: suspend () -> Unit = {
            withContext(Dispatchers.Main) {
                copyToClipboard(result)
                activity.finish()
            }
        }

        return listOf(
            ProviderResult(
                id = "$id:$expression",
                title = activity.getString(R.string.calculator_result_title, result),
                subtitle = expression,
                providerId = id,
                onSelect = action,
                frequencyKey = id,
                frequencyQuery = id,
            )
        )
    }

    private fun copyToClipboard(value: String) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(activity.getString(R.string.provider_calculator), value)
        clipboard?.setPrimaryClip(clip)
    }
}
