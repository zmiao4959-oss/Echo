package com.example.myapplication.ui

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import com.example.myapplication.R

/** Eight authored visual worlds: four still, four living. */
object ThemeManager {

    enum class Kind { STATIC, DYNAMIC }
    enum class Motion { NONE, TIDE, ORBIT, GROVE, INK_RAIN }
    enum class Typography { EDITORIAL, CARTOGRAPHIC, CINEMATIC, LITERARY, AIRY, CELESTIAL, ORGANIC, INK }

    data class ThemeSpec(
        val key: String,
        val name: String,
        val description: String,
        val kind: Kind,
        @param:StyleRes val styleRes: Int,
        @param:DrawableRes val artworkRes: Int? = null,
        @param:DrawableRes val previewArtworkRes: Int? = null,
        @param:DrawableRes val surfaceTextureRes: Int? = null,
        val motion: Motion = Motion.NONE,
        val typography: Typography,
        val dark: Boolean = false,
        val previewColors: IntArray,
    )

    const val THEME_PAPER_ATELIER = "paper_atelier"
    const val THEME_ABYSSAL_ARCHIVE = "abyssal_archive"
    const val THEME_FILM_DUSK = "film_dusk"
    const val THEME_CEDAR_STUDY = "cedar_study"
    const val THEME_TIDAL_LIGHT = "tidal_light"
    const val THEME_ORBITAL_NIGHT = "orbital_night"
    const val THEME_BREATHING_GROVE = "breathing_grove"
    const val THEME_INK_RAIN = "ink_rain"

    /** Kept for source compatibility and preference migration. */
    const val THEME_WARM_TEA = "warm_tea"
    const val THEME_FOREST = "forest"
    const val THEME_OCEAN = "ocean"
    const val THEME_TWILIGHT = "twilight"
    const val THEME_DARK = "dark"

    val themes: List<ThemeSpec> = listOf(
        ThemeSpec(
            key = THEME_PAPER_ATELIER,
            name = "纸本工坊",
            description = "和纸纤维、矿物水彩与压印植物",
            kind = Kind.STATIC,
            styleRes = R.style.Theme_MyApplication_PaperAtelier,
            artworkRes = R.drawable.theme_paper_atelier,
            previewArtworkRes = R.drawable.theme_paper_atelier_thumb,
            surfaceTextureRes = R.drawable.theme_texture_paper_atelier,
            typography = Typography.EDITORIAL,
            previewColors = colors("#F4EFE4", "#526B5F", "#C77C55"),
        ),
        ThemeSpec(
            key = THEME_ABYSSAL_ARCHIVE,
            name = "深海档案",
            description = "海图等深线与冷静的生物荧光",
            kind = Kind.STATIC,
            styleRes = R.style.Theme_MyApplication_AbyssalArchive,
            artworkRes = R.drawable.theme_abyssal_archive,
            previewArtworkRes = R.drawable.theme_abyssal_archive_thumb,
            surfaceTextureRes = R.drawable.theme_texture_abyssal_archive,
            typography = Typography.CARTOGRAPHIC,
            dark = true,
            previewColors = colors("#061421", "#214D66", "#56D1D8"),
        ),
        ThemeSpec(
            key = THEME_FILM_DUSK,
            name = "胶片暮色",
            description = "过期胶片、烟紫余晖与轻微漏光",
            kind = Kind.STATIC,
            styleRes = R.style.Theme_MyApplication_FilmDusk,
            artworkRes = R.drawable.theme_film_dusk,
            previewArtworkRes = R.drawable.theme_film_dusk_thumb,
            surfaceTextureRes = R.drawable.theme_texture_film_dusk,
            typography = Typography.CINEMATIC,
            dark = true,
            previewColors = colors("#241B2A", "#6F4A58", "#E29361"),
        ),
        ThemeSpec(
            key = THEME_CEDAR_STUDY,
            name = "雪松书房",
            description = "雪松纸面、百叶光影与旧铜细节",
            kind = Kind.STATIC,
            styleRes = R.style.Theme_MyApplication_CedarStudy,
            artworkRes = R.drawable.theme_cedar_study,
            previewArtworkRes = R.drawable.theme_cedar_study_thumb,
            surfaceTextureRes = R.drawable.theme_texture_cedar_study,
            typography = Typography.LITERARY,
            dark = true,
            previewColors = colors("#21170F", "#4E3A24", "#C8A66A"),
        ),
        ThemeSpec(
            key = THEME_TIDAL_LIGHT,
            name = "流光潮汐",
            description = "随时间缓慢折叠的冷光水面",
            kind = Kind.DYNAMIC,
            styleRes = R.style.Theme_MyApplication_TidalLight,
            motion = Motion.TIDE,
            typography = Typography.AIRY,
            previewColors = colors("#DDECEA", "#4D858B", "#A97D9A"),
        ),
        ThemeSpec(
            key = THEME_ORBITAL_NIGHT,
            name = "星轨夜航",
            description = "微弱星尘沿个人轨道安静运行",
            kind = Kind.DYNAMIC,
            styleRes = R.style.Theme_MyApplication_OrbitalNight,
            motion = Motion.ORBIT,
            typography = Typography.CELESTIAL,
            dark = true,
            previewColors = colors("#090D18", "#36466E", "#D0A96B"),
        ),
        ThemeSpec(
            key = THEME_BREATHING_GROVE,
            name = "呼吸森林",
            description = "雾中叶影随呼吸舒展与回落",
            kind = Kind.DYNAMIC,
            styleRes = R.style.Theme_MyApplication_BreathingGrove,
            motion = Motion.GROVE,
            typography = Typography.ORGANIC,
            previewColors = colors("#E4E9DF", "#496557", "#B28B62"),
        ),
        ThemeSpec(
            key = THEME_INK_RAIN,
            name = "墨雨",
            description = "宣纸灰阶、墨滴与层层涟漪",
            kind = Kind.DYNAMIC,
            styleRes = R.style.Theme_MyApplication_InkRain,
            motion = Motion.INK_RAIN,
            typography = Typography.INK,
            previewColors = colors("#E7E4DE", "#4B5051", "#8B6E65"),
        ),
    )

    val themeNames: Map<String, String> = themes.associate { it.key to it.name }

    private val aliases = mapOf(
        THEME_WARM_TEA to THEME_PAPER_ATELIER,
        THEME_FOREST to THEME_BREATHING_GROVE,
        THEME_OCEAN to THEME_ABYSSAL_ARCHIVE,
        THEME_TWILIGHT to THEME_FILM_DUSK,
        THEME_DARK to THEME_ORBITAL_NIGHT,
    )

    fun normalizedKey(themeKey: String): String = aliases[themeKey] ?: themeKey

    fun specFor(themeKey: String): ThemeSpec =
        themes.firstOrNull { it.key == normalizedKey(themeKey) } ?: themes.first()

    fun getThemeRes(themeKey: String): Int = specFor(themeKey).styleRes

    fun isDark(themeKey: String): Boolean = specFor(themeKey).dark

    fun isDynamic(themeKey: String): Boolean = specFor(themeKey).kind == Kind.DYNAMIC

    fun applyTheme(activity: Activity, themeKey: String) {
        activity.setTheme(getThemeRes(themeKey))
    }

    var pendingChange: Boolean = false

    private fun colors(vararg values: String): IntArray =
        values.map { value ->
            ((value.removePrefix("#").toLong(16) or 0xFF000000L) and 0xFFFFFFFFL).toInt()
        }.toIntArray()
}
