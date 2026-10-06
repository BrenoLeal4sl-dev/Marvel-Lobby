package com.example.marvellobby.data.local;
import androidx.room.Database;
import androidx.room.RoomDatabase;
@Database(entities={StoredRecord.class,LocalAccount.class,StoredConversation.class,RemoteAccount.class,AccountBinding.class,PublicFavoriteChange.class,CloudRecord.class},version=6,exportSchema=false)
public abstract class MarvelDatabase extends RoomDatabase {
 public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1,2) {
  @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
   db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, title TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
   db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_owner_updatedAt ON conversations(owner,updatedAt)");
  }
 };
 public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2,3) {
  @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
   db.execSQL("ALTER TABLE accounts ADD COLUMN username TEXT NOT NULL DEFAULT ''");
   java.util.Set<String> assigned=new java.util.HashSet<>();
   try(android.database.Cursor accounts=db.query("SELECT email,name FROM accounts ORDER BY email")) {
    while(accounts.moveToNext()) {
     String username;
     do { username=com.example.marvellobby.data.model.Usernames.create(accounts.getString(1)); } while(!assigned.add(username));
     db.execSQL("UPDATE accounts SET username=? WHERE email=?",new Object[]{username,accounts.getString(0)});
    }
   }
   db.execSQL("CREATE UNIQUE INDEX index_accounts_username ON accounts(username)");
  }
 };
 public static final androidx.room.migration.Migration MIGRATION_3_4 = new androidx.room.migration.Migration(3,4) {
  @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
   db.execSQL("CREATE TABLE remote_accounts (owner TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)");
   db.execSQL("CREATE TABLE account_bindings (localOwner TEXT NOT NULL PRIMARY KEY, remoteOwner TEXT NOT NULL)");
  }
 };
 public abstract ArchiveDao archive();
 public static final androidx.room.migration.Migration MIGRATION_5_6 = new androidx.room.migration.Migration(5,6) {
  @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
   db.execSQL("CREATE TABLE cloud_records (owner TEXT NOT NULL,recordKey TEXT NOT NULL,revision INTEGER NOT NULL,payload TEXT,nonce TEXT,pending INTEGER NOT NULL,PRIMARY KEY(owner,recordKey))");
  }
 };
 public static final androidx.room.migration.Migration MIGRATION_4_5 = new androidx.room.migration.Migration(4,5) {
  @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
   db.execSQL("ALTER TABLE accounts ADD COLUMN bio TEXT NOT NULL DEFAULT ''");
   db.execSQL("CREATE TABLE public_favorite_changes (owner TEXT NOT NULL,recordKey TEXT NOT NULL,payload TEXT NOT NULL,favorite INTEGER NOT NULL,nonce TEXT NOT NULL,PRIMARY KEY(owner,recordKey))");
  }
 };
}
