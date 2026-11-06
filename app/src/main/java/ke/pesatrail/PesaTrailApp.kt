package ke.pesatrail

import android.app.Application
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ke.pesatrail.data.*
import ke.pesatrail.security.Vault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@HiltAndroidApp
class PesaTrailApp : Application()
@Module @InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): LedgerDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
            .openHelperFactory(SupportOpenHelperFactory(Vault.databaseKey(context))).build()
    }
    @Provides fun dao(db: LedgerDatabase): LedgerDao = db.ledger()
}
