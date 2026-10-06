package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import com.example.marvellobby.data.model.Usernames;
@Entity(tableName="accounts",indices={@Index(value={"username"},unique=true)})
public class LocalAccount {
 @PrimaryKey @NonNull public String email="";
 @NonNull public String name="";
 @NonNull public String passwordHash="";
 @NonNull public String salt="";
 @NonNull public String avatar="";
 @NonNull @ColumnInfo(defaultValue="''") public String username=Usernames.create("Hero");
 @NonNull @ColumnInfo(defaultValue="''") public String bio="";
 public LocalAccount() {}
}
