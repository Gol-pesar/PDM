package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.PostQueueAction
import com.example.data.model.QueueEntity

@Database(
    entities = [
        DownloadEntity::class,
        DownloadSegmentEntity::class,
        CategoryEntity::class,
        QueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun segmentDao(): SegmentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "dadeban_enterprise_downloads.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val defaultCategories: List<CategoryEntity> = listOf(
            CategoryEntity(
                id = "documents",
                nameFa = "اسناد و فایل‌ها",
                extensionsCsv = "pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,rtf,odt,ods,epub,md,json,xml",
                iconKey = "documents",
                saveSubfolder = "Documents",
                isCustom = false
            ),
            CategoryEntity(
                id = "archives",
                nameFa = "فشرده",
                extensionsCsv = "zip,rar,7z,tar,gz,bz2,xz,iso,cab,tgz",
                iconKey = "archives",
                saveSubfolder = "Archives",
                isCustom = false
            ),
            CategoryEntity(
                id = "programs",
                nameFa = "برنامه‌ها",
                extensionsCsv = "exe,msi,dmg,pkg,deb,rpm,appimage,bin,sh,bat",
                iconKey = "programs",
                saveSubfolder = "Programs",
                isCustom = false
            ),
            CategoryEntity(
                id = "images",
                nameFa = "تصاویر",
                extensionsCsv = "jpg,jpeg,png,webp,gif,svg,bmp,tiff,psd,ai,ico,heic",
                iconKey = "images",
                saveSubfolder = "Images",
                isCustom = false
            ),
            CategoryEntity(
                id = "video",
                nameFa = "ویدئو",
                extensionsCsv = "mp4,mkv,avi,mov,wmv,flv,webm,m4v,ts,3gp",
                iconKey = "video",
                saveSubfolder = "Video",
                isCustom = false
            ),
            CategoryEntity(
                id = "audio",
                nameFa = "موسیقی و صوت",
                extensionsCsv = "mp3,flac,wav,aac,ogg,m4a,wma,opus,amr",
                iconKey = "audio",
                saveSubfolder = "Audio",
                isCustom = false
            ),
            CategoryEntity(
                id = "other",
                nameFa = "سایر",
                extensionsCsv = "",
                iconKey = "other",
                saveSubfolder = "General",
                isCustom = false
            )
        )

        val defaultQueues: List<QueueEntity> = listOf(
            QueueEntity(
                id = 1L,
                nameFa = "صف اصلی",
                maxConcurrent = 3,
                scheduleEnabled = false,
                startHour = 0,
                startMinute = 0,
                stopHour = 23,
                stopMinute = 59,
                isRunning = true,
                postAction = PostQueueAction.NOTIFY.name,
                isDefault = true
            ),
            QueueEntity(
                id = 2L,
                nameFa = "دانلود شبانه",
                maxConcurrent = 2,
                scheduleEnabled = false,
                startHour = 2,
                startMinute = 0,
                stopHour = 7,
                stopMinute = 0,
                isRunning = false,
                postAction = PostQueueAction.STOP_QUEUE.name,
                isDefault = false
            )
        )
    }
}
