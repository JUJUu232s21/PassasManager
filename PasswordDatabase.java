import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class PasswordDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "password_vault.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "passwords";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_PASSWORD = "password";

    private static final String TABLE_CREATE = "CREATE TABLE " + TABLE_NAME + " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_TITLE + " TEXT NOT NULL, " + COLUMN_PASSWORD + " TEXT NOT NULL" +");";

    public PasswordDatabase(Context context, String passphrase) {
        super(context, DATABASE_NAME, passphrase, null, DATABASE_VERSION, 0, null, null, false);
        System.loadLibrary("sqlcipher");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long addPassword(String title, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_PASSWORD, password);
        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    public List<PasswordItem> getAllPasswords() {
        List<PasswordItem> passwordList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_ID + " ASC";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                int idIndex = cursor.getColumnIndex(COLUMN_ID);
                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                int passwordIndex = cursor.getColumnIndex(COLUMN_PASSWORD);

                long id = idIndex != -1 ? cursor.getLong(idIndex) : -1;
                String title = titleIndex != -1 ? cursor.getString(titleIndex) : "";
                String password = passwordIndex != -1 ? cursor.getString(passwordIndex) : "";

                passwordList.add(new PasswordItem(id, title, password));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return passwordList;
    }

    public void deletePassword(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updatePassword(long id, String title, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_PASSWORD, password);
        db.update(TABLE_NAME, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public static class PasswordItem {
        private final long id;
        private final String title;
        private final String password;

        public PasswordItem(long id, String title, String password) {
            this.id = id;
            this.title = title;
            this.password = password;
        }

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getPassword() {
            return password;
        }
    }
}
