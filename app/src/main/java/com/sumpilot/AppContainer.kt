package com.sumpilot

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.sumpilot.data.local.AppDatabase
import com.sumpilot.data.repository.CalculatorRepository
import com.sumpilot.data.repository.PracticeRepository
import com.sumpilot.data.repository.RoomPracticeStore
import com.sumpilot.data.repository.SettingsRepository
import com.sumpilot.domain.calculator.CalculatorEngine
import com.sumpilot.domain.generation.QuestionGenerator
import com.sumpilot.domain.model.Clock
import com.sumpilot.domain.sessions.PracticeService
import com.sumpilot.ui.sound.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.random.Random

object SystemClockSource : Clock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun now(): Long = System.currentTimeMillis()
}

/** Manual dependency injection. */
class AppContainer(context: Context) {
    val clock: Clock = SystemClockSource

    /** Outlives screens; used for small persistence writes that must complete. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val practiceRepository = PracticeRepository(database)
    val calculatorRepository = CalculatorRepository(database, clock)
    val calculatorEngine = CalculatorEngine()
    val questionGenerator = QuestionGenerator(Random.Default)
    val practiceService = PracticeService(RoomPracticeStore(database), questionGenerator, clock)
    val soundPlayer = SoundPlayer(context)
}

class SumPilotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
