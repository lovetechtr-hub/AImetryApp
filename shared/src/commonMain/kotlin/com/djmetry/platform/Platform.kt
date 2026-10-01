package com.djmetry.platform

/**
 * Десктоп: нет системной «Назад». Esc — это закрыть то, что открыто поверх, а не «уйти на главную вкладку».
 */
expect val isDesktopPlatform: Boolean
