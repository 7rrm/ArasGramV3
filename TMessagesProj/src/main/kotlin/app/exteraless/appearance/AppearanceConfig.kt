package app.exteraless.appearance

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Настройки системы плеера и карточки музыки в профиле (порт из exteraless).
 *
 * Урезанная версия: только ключи, нужные перенесённому плееру (Md3Player /
 * PlayerSheet / PlayerMiniView), карточке ProfileMusicCard и логике разделов.
 * Остальные секции экрана Appearance из exteraless в этот форк не переносятся.
 *
 * Схема повторяет [app.exteraless.OpenExteraConfig]: те же SharedPreferences
 * (NekoConfig), тот же [ConfigItem], собственный список. Ключи с префиксами
 * OEAppearance / OEPlayer — они не конфликтуют с ключами NekoConfig/NaConfig.
 *
 * Загрузка ленивая: [ensureLoaded] вызывается из геттеров, потому что
 * ApplicationLoader трогать нельзя. После первой загрузки проверка стоит
 * один volatile-read.
 */
object AppearanceConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    // ---- Секции настроек ----

    /** Радиус скругления карточек-секций, dp. 0 — острые углы (как в стоке). */
    @JvmField
    val sectionRadius =
        addConfig("OEAppearanceSectionRadius", ConfigItem.configTypeInt, 20)

    // ---- Material Design 3 ----

    /** Полноэкранный плеер в стиле Material 3. Дефолт true. */
    @JvmField
    val md3Player =
        addConfig("OEAppearanceMd3Player", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3Player(): Boolean {
        ensureLoaded()
        return md3Player.Bool()
    }

    /** Мини-плеер над панелью вкладок. Дефолт true. */
    @JvmField
    val md3MiniPlayer =
        addConfig("OEAppearanceMd3MiniPlayer", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3MiniPlayer(): Boolean {
        ensureLoaded()
        return md3MiniPlayer.Bool()
    }

    /** Разрешить поиск текста песен в LRCLIB (синхронизированные тексты). */
    @JvmField
    val lrclibAllowed =
        addConfig("OEPlayerLrclibAllowed", ConfigItem.configTypeBool, false)

    @JvmField
    val playerCoverStyle = addConfig("OEPlayerCoverStyle", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerCoverSize = addConfig("OEPlayerCoverSize", ConfigItem.configTypeInt, 100)

    @JvmField
    val playerCoverRadius = addConfig("OEPlayerCoverRadius", ConfigItem.configTypeInt, 28)

    @JvmField
    val playerDiscSpin = addConfig("OEPlayerDiscSpin", ConfigItem.configTypeBool, true)

    @JvmField
    val playerCoverSwipe = addConfig("OEPlayerCoverSwipe", ConfigItem.configTypeBool, true)

    @JvmField
    val playerTitleAlign = addConfig("OEPlayerTitleAlign", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerTitleSize = addConfig("OEPlayerTitleSize", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerTitleAbove = addConfig("OEPlayerTitleAbove", ConfigItem.configTypeBool, false)

    @JvmField
    val playerOrder = addConfig("OEPlayerOrder", ConfigItem.configTypeString, "")

    @JvmField
    val playerShowActions = addConfig("OEPlayerShowActions", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowModes = addConfig("OEPlayerShowModes", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowLike = addConfig("OEPlayerShowLike", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowSource = addConfig("OEPlayerShowSource", ConfigItem.configTypeBool, true)

    @JvmField
    val playerWavy = addConfig("OEPlayerWavy", ConfigItem.configTypeBool, true)

    @JvmField
    val playerControlsSize = addConfig("OEPlayerControlsSize", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerActionsStyle = addConfig("OEPlayerActionsStyle", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerColors = addConfig("OEPlayerColors", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerBackground = addConfig("OEPlayerBackground", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerCoverExtend = addConfig("OEPlayerCoverExtend", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniCover = addConfig("OEPlayerMiniCover", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniShape = addConfig("OEPlayerMiniShape", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniProgress = addConfig("OEPlayerMiniProgress", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniColors = addConfig("OEPlayerMiniColors", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerMiniCompact = addConfig("OEPlayerMiniCompact", ConfigItem.configTypeBool, false)

    @JvmField
    val playerMiniArtist = addConfig("OEPlayerMiniArtist", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniPrevious = addConfig("OEPlayerMiniPrevious", ConfigItem.configTypeBool, false)

    @JvmField
    val playerMiniNext = addConfig("OEPlayerMiniNext", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniClose = addConfig("OEPlayerMiniClose", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniTop = addConfig("OEPlayerMiniTop", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerStyleItems(): List<ConfigItem> {
        ensureLoaded()
        return listOf(
            playerCoverStyle, playerCoverSize, playerCoverRadius, playerDiscSpin, playerCoverSwipe,
            playerTitleAlign, playerTitleSize, playerTitleAbove, playerOrder, playerShowActions,
            playerShowModes, playerShowLike, playerShowSource, playerWavy, playerControlsSize, playerActionsStyle,
            playerColors, playerBackground, playerCoverExtend, playerMiniCover, playerMiniShape, playerMiniProgress,
            playerMiniColors, playerMiniCompact, playerMiniArtist, playerMiniPrevious, playerMiniNext,
            playerMiniClose
        )
    }

    /** Карточка музыки в профиле вместо официальной пилюли. Дефолт true. */
    @JvmField
    val profileMusicCard =
        addConfig("OEAppearanceProfileMusicCard", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun profileMusicCard(): Boolean {
        ensureLoaded()
        return profileMusicCard.Bool()
    }

    // ---- Список чатов ----

    /** Мини-аватарка отправителя перед именем в превью списка чатов. Дефолт true. */
    @JvmField
    val senderMiniAvatars =
        addConfig("OEAppearanceSenderMiniAvatars", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun senderMiniAvatars(): Boolean {
        ensureLoaded()
        return senderMiniAvatars.Bool()
    }

    // ---- Геттеры для Java ----

    /** Радиус скругления карточек-секций, dp. 0 — острые углы (как в стоке). */
    @JvmStatic
    fun sectionRadius(): Int {
        ensureLoaded()
        return sectionRadius.Int()
    }

    // ---- Инфраструктура конфигов ----

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    private fun addConfig(item: ConfigItem): ConfigItem {
        configs.add(item)
        return item
    }

    @JvmStatic
    fun ensureLoaded() {
        if (!configLoaded) loadConfig(false)
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)

                        ConfigItem.configTypeInt ->
                            item.value = preferences.getInt(item.key, item.defaultValue as Int)

                        ConfigItem.configTypeLong ->
                            item.value = preferences.getLong(item.key, item.defaultValue as Long)

                        ConfigItem.configTypeFloat ->
                            item.value = preferences.getFloat(item.key, item.defaultValue as Float)

                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String?)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
    }

    /** Сбрасывает настройки плеера и карточки музыки к значениям по умолчанию. */
    @JvmStatic
    fun reset() {
        synchronized(sync) {
            val editor = getPreferences().edit()
            for (item in configs) {
                editor.remove(item.key)
                item.value = item.defaultValue
            }
            editor.apply()
        }
    }
}
