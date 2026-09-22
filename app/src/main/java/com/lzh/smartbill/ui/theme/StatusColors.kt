package com.lzh.smartbill.ui.theme

import androidx.compose.ui.graphics.Color

enum class STATUS(val color1: Color, val color2: Color) {
    SUCCESS(Color.Green, Color.White)
    , ERROR(Color.Red, Color.White)
    , ING(Color.Yellow, PurpleGrey40) // 进行中
    , NORMAL(Purple40, Color.White) // 未触发
}



