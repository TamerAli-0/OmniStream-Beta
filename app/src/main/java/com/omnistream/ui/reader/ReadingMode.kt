package com.omnistream.ui.reader

/**
 * Reading modes for manga/manhwa reader
 * Based on Saikou + Kotatsu reader modes
 */
enum class ReadingMode {
    /**
     * Webtoon mode (optimized vertical continuous scroll)
     * Best for: Webtoons, Manhwa, Manhua
     * Features: Auto-scroll support, seamless page loading
     */
    WEBTOON,

    /**
     * Vertical continuous scroll (smooth scrolling)
     * Best for: Webtoons, long strip manga
     */
    VERTICAL_CONTINUOUS,

    /**
     * Paged vertical (single page vertical swipe)
     * Best for: Traditional manga in vertical layout
     */
    PAGED_VERTICAL,

    /**
     * Horizontal left-to-right paging (Western comics style)
     * Best for: Western comics, some manga
     */
    HORIZONTAL_LTR,

    /**
     * Horizontal right-to-left paging (Traditional manga style)
     * Best for: Japanese manga
     */
    HORIZONTAL_RTL,

    /**
     * Dual page mode (two pages side-by-side)
     * Best for: Tablets, landscape mode
     */
    DUAL_PAGE,

    /**
     * Fit width mode (zoom to fit screen width)
     * Best for: Double-page spreads, detailed artwork
     */
    FIT_WIDTH,

    /**
     * Fit height mode (zoom to fit screen height)
     * Best for: Portrait manga, full page view
     */
    FIT_HEIGHT;

    val displayName: String
        get() = when (this) {
            WEBTOON -> "Webtoon"
            VERTICAL_CONTINUOUS -> "Vertical Scroll"
            PAGED_VERTICAL -> "Paged Vertical"
            HORIZONTAL_LTR -> "Left to Right"
            HORIZONTAL_RTL -> "Right to Left"
            DUAL_PAGE -> "Dual Page"
            FIT_WIDTH -> "Fit Width"
            FIT_HEIGHT -> "Fit Height"
        }

    val description: String
        get() = when (this) {
            WEBTOON -> "Optimized for webtoons with auto-scroll"
            VERTICAL_CONTINUOUS -> "Smooth continuous vertical scrolling"
            PAGED_VERTICAL -> "Swipe up/down for pages"
            HORIZONTAL_LTR -> "Swipe left to go forward"
            HORIZONTAL_RTL -> "Swipe right to go forward (manga)"
            DUAL_PAGE -> "Two pages side-by-side"
            FIT_WIDTH -> "Zoom pages to fit screen width"
            FIT_HEIGHT -> "Zoom pages to fit screen height"
        }

    val icon: String
        get() = when (this) {
            WEBTOON -> "📱"
            VERTICAL_CONTINUOUS -> "↕️"
            PAGED_VERTICAL -> "⬆️"
            HORIZONTAL_LTR -> "➡️"
            HORIZONTAL_RTL -> "⬅️"
            DUAL_PAGE -> "📖"
            FIT_WIDTH -> "↔️"
            FIT_HEIGHT -> "↕️"
        }
}
