package in.slanglabs.sampleretailapp.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import in.slanglabs.sampleretailapp.AppExecutors;
import in.slanglabs.sampleretailapp.Model.CartItem;
import in.slanglabs.sampleretailapp.Model.Item;
import in.slanglabs.sampleretailapp.Model.ItemsFts;
import in.slanglabs.sampleretailapp.Model.ListType;
import in.slanglabs.sampleretailapp.Model.Offer;
import in.slanglabs.sampleretailapp.Model.OrderItem;
import in.slanglabs.sampleretailapp.db.Convertors.CartItemsConvertor;
import in.slanglabs.sampleretailapp.db.Convertors.DateConverter;
import in.slanglabs.sampleretailapp.db.dao.CartDao;
import in.slanglabs.sampleretailapp.db.dao.ItemDao;
import in.slanglabs.sampleretailapp.db.dao.OfferDao;
import in.slanglabs.sampleretailapp.db.dao.OrderDao;

@Database(entities = {OrderItem.class, CartItem.class, Offer.class, Item.class, ItemsFts.class}, version = 18, exportSchema = false)
@TypeConverters({CartItemsConvertor.class, DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase sInstance;

    @VisibleForTesting
    public static final String DATABASE_NAME = "basic-sample-db";

    public abstract OrderDao orderDao();

    public abstract OfferDao offerDao();

    public abstract CartDao cartDao();

    public abstract ItemDao itemDao();

    private final MutableLiveData<Boolean> mIsDatabaseCreated = new MutableLiveData<>();

    public static AppDatabase getInstance(final Context context, final AppExecutors executors) {
        if (sInstance == null) {
            synchronized (AppDatabase.class) {
                if (sInstance == null) {
                    sInstance = buildDatabase(context.getApplicationContext(), executors);
                    sInstance.updateDatabaseCreated(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    /**
     * Build the database. {@link Builder#build()} only sets up the database configuration and
     * creates a new instance of the database.
     * The SQLite database is only created when it's accessed for the first time.
     */
    private static AppDatabase buildDatabase(final Context appContext, AppExecutors executors) {
        return Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addCallback(new Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        AppDatabase database = AppDatabase.getInstance(appContext, executors);
                        insertData(appContext, database, executors);
                    }

                    @Override
                    public void onDestructiveMigration(@NonNull SupportSQLiteDatabase db) {
                        super.onDestructiveMigration(db);
                        AppDatabase database = AppDatabase.getInstance(appContext, executors);
                        insertData(appContext, database, executors);
                    }
                })
//                .addMigrations(MIGRATION_5_6)
                .build();
    }

    private static void insertData(final Context context, final AppDatabase database, AppExecutors appExecutors) {
        appExecutors.diskIO().execute(() -> {
            //Parsing the json file and adding the items to items table.
            String json = loadJSONFromAsset(context, "list.json");
            String json2 = loadJSONFromAsset(context, "pharmacy_app_data.json");
            String json3 = loadJSONFromAsset(context, "fashion_app_data.json");
            Moshi moshi = new Moshi.Builder().build();
            Type type = Types.newParameterizedType(List.class, Item.class);
            JsonAdapter<List<Item>> adapter = moshi.adapter(type);
            List<Item> listItems;
            List<Item> finalListItems = new ArrayList<>();
            try {
                if (json != null) {
                    listItems = adapter.fromJson(json);
                    for (Item item : listItems) {
                        item.type = ListType.GROCERY;
                    }
                    finalListItems.addAll(listItems);
                }

                if (json2 != null) {
                    listItems = adapter.fromJson(json2);
                    for (Item item : listItems) {
                        item.type = ListType.PHARMACY;
                    }
                    finalListItems.addAll(listItems);
                }

                if (json3 != null) {
                    listItems = adapter.fromJson(json3);
                    for (Item item : listItems) {
                        item.type = ListType.FASHION;
                    }
                    finalListItems.addAll(listItems);
                }

                database.runInTransaction(() -> {
                    database.itemDao().insert(finalListItems);
                    database.setDatabaseCreated();
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static String loadJSONFromAsset(Context context, String fileName) {
        String json;
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    private void setDatabaseCreated() {
        mIsDatabaseCreated.postValue(true);
    }

    private void updateDatabaseCreated(final Context context) {
        if (context.getDatabasePath(DATABASE_NAME).exists()) {
            setDatabaseCreated();
        }
    }

    public LiveData<Boolean> getDatabaseCreated() {
        return mIsDatabaseCreated;
    }


//    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
//
//        @Override
//        public void migrate(@NonNull SupportSQLiteDatabase database) {
//            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `itemsFts` USING FTS4("
//                    + "`id` TEXT, `name` TEXT, content=`items`)");
//            database.execSQL("INSERT INTO itemsFts (`id`, `name`) "
//                    + "SELECT `id`, `name`, `description` FROM items");
//
//        }
//    };

}