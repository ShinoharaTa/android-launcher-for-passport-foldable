package net.shino3.gzf8launcher.data

import android.content.pm.ApplicationInfo

/**
 * アプリのカテゴリ(`ApplicationInfo.category`)の表示名。
 * ドロワーの絞り込み(#29)と、束から作るフォルダの名前(#63)で使う。
 * 申告していないアプリ(-1)や、ここに無いカテゴリは名前を持たない。
 */
object AppCategories {
    val labels: Map<Int, String> = linkedMapOf(
        ApplicationInfo.CATEGORY_GAME to "GAME",
        ApplicationInfo.CATEGORY_AUDIO to "AUDIO",
        ApplicationInfo.CATEGORY_VIDEO to "VIDEO",
        ApplicationInfo.CATEGORY_IMAGE to "IMAGE",
        ApplicationInfo.CATEGORY_SOCIAL to "SOCIAL",
        ApplicationInfo.CATEGORY_NEWS to "NEWS",
        ApplicationInfo.CATEGORY_MAPS to "MAPS",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "PRODUCTIVITY",
    )

    fun labelOf(category: Int): String? = labels[category]
}
