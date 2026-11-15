package com.learnmate.ai

import android.app.Application
import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.AppPreferences
import com.learnmate.ai.data.db.LearnMateDatabase
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LearnMateApplication : Application() {

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var database: LearnMateDatabase
        private set

    lateinit var learningRepository: LearningRepository
        private set

    lateinit var assetManager: ModelAssetManager
        private set

    lateinit var llamaEngine: ExecuTorchLlamaEngine
        private set

    lateinit var aiCoreEngine: com.learnmate.ai.ai.LearnMateAiCoreEngine
        private set

    lateinit var aiTutorRepository: AiTutorRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
        database = LearnMateDatabase.getInstance(this)
        learningRepository = LearningRepository(database.learnMateDao())
        assetManager = ModelAssetManager(filesDir)
        llamaEngine = ExecuTorchLlamaEngine(assetManager)
        aiCoreEngine = com.learnmate.ai.ai.LearnMateAiCoreEngine(llamaEngine, assetManager)
        aiTutorRepository = AiTutorRepository(llamaEngine, assetManager, learningRepository)

        // Pre-populate verified curriculum and warm up model if present in background
        applicationScope.launch {
            learningRepository.initializeCurriculumIfNeeded()
            if (assetManager.isValidInstalledModel()) {
                aiTutorRepository.initializeModelAsync()
            }
        }
    }
}
