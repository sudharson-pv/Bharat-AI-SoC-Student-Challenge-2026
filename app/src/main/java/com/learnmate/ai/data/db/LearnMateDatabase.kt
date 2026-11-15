package com.learnmate.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LearnerProfileEntity::class,
        ContentPackEntity::class,
        PackDependencyEntity::class,
        CurriculumNodeEntity::class,
        LearnerTrackEntity::class,
        NodePrerequisiteEntity::class,
        CurriculumResourceEntity::class,
        AssessmentBlueprintEntity::class,
        AssessmentQuestionV2Entity::class,
        PersonalisedRoadmapEntity::class,
        SkillGapReportEntity::class,
        StudyTaskEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        VerifiedTopicEntity::class,
        AssessmentQuestionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LearnMateDatabase : RoomDatabase() {
    abstract fun learnMateDao(): LearnMateDao

    companion object {
        const val DATABASE_NAME = "learnmate_ai.db"

        @Volatile
        private var INSTANCE: LearnMateDatabase? = null

        fun getInstance(context: Context): LearnMateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LearnMateDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_1_2,
                        DatabaseMigrations.MIGRATION_2_3,
                        DatabaseMigrations.MIGRATION_3_4
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): LearnMateDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                LearnMateDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
