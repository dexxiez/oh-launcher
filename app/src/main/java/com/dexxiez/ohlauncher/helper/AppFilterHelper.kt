package com.dexxiez.ohlauncher.helper

import com.dexxiez.ohlauncher.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}