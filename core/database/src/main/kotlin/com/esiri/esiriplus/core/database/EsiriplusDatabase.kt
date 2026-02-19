package com.esiri.esiriplus.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.esiri.esiriplus.core.database.converter.DateConverters
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.database.entity.SessionEntity
import com.esiri.esiriplus.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        SessionEntity::class,
        ConsultationEntity::class,
        PaymentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class EsiriplusDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun paymentDao(): PaymentDao
}
