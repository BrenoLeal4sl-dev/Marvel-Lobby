package com.example.marvellobby.data.local;
import androidx.room.*;
import java.util.List;
@Dao public interface ArchiveDao {
 @Query("SELECT * FROM records WHERE owner = :owner ORDER BY viewedAt DESC") List<StoredRecord> records(String owner);
 @Query("SELECT * FROM records WHERE owner = :owner AND recordKey = :key LIMIT 1") StoredRecord record(String owner, String key);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void save(StoredRecord record);
 @Transaction default void updateRecord(String owner,String key,String payload,boolean toggle,long viewedAt) {
  AccountBinding linked=binding(owner);String target=linked==null?owner:linked.remoteOwner;
  StoredRecord record=record(target,key);
  if(record==null){record=new StoredRecord();record.owner=target;record.recordKey=key;}
  record.payload=payload;
  if(toggle)record.favorite=!record.favorite;else record.viewedAt=viewedAt;
  save(record);
  if(toggle && target.startsWith("remote:"))queuePublicFavorite(record);
  if(!toggle && target.startsWith("remote:"))queueCloudHistory(record);
 }
 @Insert(onConflict=OnConflictStrategy.REPLACE) void queuePublicChange(PublicFavoriteChange change);
 @Query("SELECT * FROM public_favorite_changes WHERE owner=:owner AND recordKey='@sharing' LIMIT 1") PublicFavoriteChange sharingChange(String owner);
 @Query("SELECT * FROM public_favorite_changes WHERE owner=:owner AND recordKey<>'@sharing' ORDER BY recordKey LIMIT 20") List<PublicFavoriteChange> publicChanges(String owner);
 @Query("DELETE FROM public_favorite_changes WHERE owner=:owner AND recordKey=:key AND nonce=:nonce") void acknowledgePublicChange(String owner,String key,String nonce);
 default void queuePublicFavorite(StoredRecord record) {
  if(!record.recordKey.matches("(CHARACTER|POWER|TEAM|STORY_ARC):[1-9][0-9]*"))return;
  PublicFavoriteChange change=new PublicFavoriteChange();change.owner=record.owner;change.recordKey=record.recordKey;
  change.payload=record.payload;change.favorite=record.favorite;change.nonce=java.util.UUID.randomUUID().toString();queuePublicChange(change);
 }
 @Transaction default void queueFavoriteSharing(String owner,boolean enabled) {
  if(!owner.startsWith("remote:"))throw new IllegalArgumentException("Connect an online account first.");
  if(enabled)for(StoredRecord record:records(owner))if(record.favorite)queuePublicFavorite(record);
  PublicFavoriteChange choice=new PublicFavoriteChange();choice.owner=owner;choice.recordKey="@sharing";
  choice.favorite=enabled;choice.nonce=java.util.UUID.randomUUID().toString();queuePublicChange(choice);
 }
 @Query("DELETE FROM records WHERE owner = :owner AND favorite = 0") void deleteHistory(String owner);
 @Query("UPDATE records SET viewedAt = 0 WHERE owner = :owner") void resetHistory(String owner);
 @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1") LocalAccount account(String email);
 @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1") LocalAccount accountByUsername(String username);
 @Query("SELECT * FROM remote_accounts WHERE owner = :owner LIMIT 1") RemoteAccount remoteAccount(String owner);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void saveRemoteAccount(RemoteAccount account);
 @Query("SELECT * FROM account_bindings WHERE localOwner = :owner LIMIT 1") AccountBinding binding(String owner);
 @Insert(onConflict=OnConflictStrategy.ABORT) void insertBinding(AccountBinding binding);
 @Query("INSERT OR REPLACE INTO records(owner,recordKey,payload,favorite,viewedAt) SELECT :target,a.recordKey,CASE WHEN coalesce(b.viewedAt,0)>a.viewedAt THEN b.payload ELSE a.payload END,CASE WHEN a.favorite OR b.favorite THEN 1 ELSE 0 END,max(a.viewedAt,coalesce(b.viewedAt,0)) FROM records a LEFT JOIN records b ON b.owner=:target AND b.recordKey=a.recordKey WHERE a.owner=:source")
 void mergeLibrary(String source,String target);
 @Query("DELETE FROM records WHERE owner=:owner") void removeOwnerRecords(String owner);
 @Transaction default void bindLocalAccount(String source,RemoteAccount target) {
  if(account(source)==null)throw new IllegalStateException("Local account unavailable.");
  AccountBinding existing=binding(source);
  if(existing!=null) {
   if(!existing.remoteOwner.equals(target.owner))throw new IllegalStateException("This local account is already connected to another online account.");
   saveRemoteAccount(target);return;
  }
  saveRemoteAccount(target);
  mergeLibrary(source,target.owner);removeOwnerRecords(source);moveConversations(source,target.owner);
  for(StoredRecord record:records(target.owner))if(record.favorite)queuePublicFavorite(record);
  AccountBinding binding=new AccountBinding();binding.localOwner=source;binding.remoteOwner=target.owner;insertBinding(binding);
 }
 @Insert(onConflict=OnConflictStrategy.ABORT) void insertAccount(LocalAccount account);
 @Update void updateAccount(LocalAccount account);
 @Query("DELETE FROM accounts WHERE email = :email") void deleteAccount(String email);
 @Query("UPDATE records SET owner = :newOwner WHERE owner = :oldOwner") void moveRecords(String oldOwner,String newOwner);
 @Query("UPDATE conversations SET owner = :newOwner WHERE owner = :oldOwner") void moveConversations(String oldOwner,String newOwner);
 @Query("UPDATE accounts SET email=:newEmail,name=:name,passwordHash=:hash,salt=:salt,avatar=:avatar,username=:username,bio=:bio WHERE email=:oldEmail")
 void renameAccount(String oldEmail,String newEmail,String name,String hash,String salt,String avatar,String username,String bio);
 @Transaction default void changeAccountEmail(String oldEmail,LocalAccount replacement) {
  renameAccount(oldEmail,replacement.email,replacement.name,replacement.passwordHash,replacement.salt,replacement.avatar,replacement.username,replacement.bio);
  moveRecords(oldEmail,replacement.email);
  moveConversations(oldEmail,replacement.email);
 }
 @Query("SELECT * FROM conversations WHERE owner = :owner ORDER BY updatedAt DESC") List<StoredConversation> conversations(String owner);
 @Query("SELECT * FROM conversations WHERE owner = :owner AND id = :id LIMIT 1") StoredConversation conversation(String owner,String id);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void insertConversation(StoredConversation conversation);
 @Transaction default void saveConversation(StoredConversation conversation) {
  AccountBinding linked=binding(conversation.owner);
  if(linked!=null)conversation.owner=linked.remoteOwner;
  if(!conversation.owner.equals("guest") && account(conversation.owner)==null && remoteAccount(conversation.owner)==null)return;
  StoredConversation previous=conversation(conversation.owner,conversation.id);
  // A UUID belongs to a single account; never overwrite another owner's chat.
  if(previous==null && conversationOwner(conversation.id)!=null)return;
  insertConversation(conversation);
  if(conversation.owner.startsWith("remote:"))queueCloud(conversation.owner,"chat:"+conversation.id,conversation.payload);
 }
 @Query("SELECT owner FROM conversations WHERE id = :id LIMIT 1") String conversationOwner(String id);
 @Query("DELETE FROM conversations WHERE owner = :owner AND id = :id") void deleteConversation(String owner,String id);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void saveCloud(CloudRecord record);
 @Query("SELECT * FROM cloud_records WHERE owner=:owner AND recordKey=:key LIMIT 1") CloudRecord cloudRecord(String owner,String key);
 @Query("SELECT * FROM cloud_records WHERE owner=:owner AND pending=1 ORDER BY recordKey LIMIT 20") List<CloudRecord> cloudChanges(String owner);
 @Query("SELECT count(*) FROM cloud_records WHERE owner=:owner AND pending=1") int cloudPendingCount(String owner);
 @Transaction default void queueCloud(String owner,String key,String payload) {
  if(!owner.startsWith("remote:"))return;
  CloudRecord row=cloudRecord(owner,key);if(row==null){row=new CloudRecord();row.owner=owner;row.recordKey=key;}
  row.payload=payload;row.nonce=java.util.UUID.randomUUID().toString();row.pending=true;saveCloud(row);
 }
 default void queueCloudHistory(StoredRecord record) {
  // Old or damaged snapshots must not make a local library update or account binding fail.
  com.google.gson.JsonElement entity;
  try { entity=com.google.gson.JsonParser.parseString(record.payload); }
  catch(com.google.gson.JsonParseException invalid) { return; }
  if(!entity.isJsonObject())return;
  com.google.gson.JsonObject snapshot=new com.google.gson.JsonObject();
  snapshot.add("entity",entity);snapshot.addProperty("viewedAt",record.viewedAt);
  queueCloud(record.owner,"history:"+record.recordKey,snapshot.toString());
 }
 @Transaction default void seedCloud(String owner) {
  for(StoredRecord record:records(owner))if(record.viewedAt>0 && cloudRecord(owner,"history:"+record.recordKey)==null)queueCloudHistory(record);
  for(StoredConversation chat:conversations(owner))if(cloudRecord(owner,"chat:"+chat.id)==null)queueCloud(owner,"chat:"+chat.id,chat.payload);
 }
 @Transaction default void clearHistoryWithSync(String owner) {
  for(StoredRecord record:records(owner))if(record.viewedAt>0)queueCloud(owner,"history:"+record.recordKey,null);
  deleteHistory(owner);resetHistory(owner);
 }
 @Transaction default void deleteConversationWithSync(String owner,String id) {
  if(conversation(owner,id)!=null)queueCloud(owner,"chat:"+id,null);deleteConversation(owner,id);
 }
 /** ACK only its nonce; a newer local edit must survive an in-flight request. */
 @Transaction default void acknowledgeCloud(String owner,String key,String nonce,long revision) {
  CloudRecord row=cloudRecord(owner,key);if(row==null)return;
  row.revision=Math.max(row.revision,revision);
  if(java.util.Objects.equals(row.nonce,nonce)){row.pending=false;row.nonce=null;}saveCloud(row);
 }
 @Transaction default boolean rebaseCloud(String owner,String key,String nonce,long revision,String payload) {
  CloudRecord row=cloudRecord(owner,key);if(row==null || !java.util.Objects.equals(row.nonce,nonce))return false;
  row.revision=revision;row.payload=payload;row.nonce=java.util.UUID.randomUUID().toString();saveCloud(row);return true;
 }
 @Transaction default boolean importCloud(String owner,String key,String payload,long revision,String expectedNonce) {
  CloudRecord prior=cloudRecord(owner,key);
  if(prior!=null && (prior.revision>revision || (!prior.pending && prior.revision==revision) || (prior.pending && !java.util.Objects.equals(prior.nonce,expectedNonce))))return false;
  if(key.startsWith("history:")) {
   String recordKey=key.substring(8);StoredRecord record=record(owner,recordKey);
   if(payload==null) {if(record!=null){record.viewedAt=0;save(record);}}
   else {
    com.google.gson.JsonObject json=com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
    if(record==null){record=new StoredRecord();record.owner=owner;record.recordKey=recordKey;}
    if(record.payload==null || record.payload.isEmpty())record.payload=json.get("entity").toString();record.viewedAt=json.get("viewedAt").getAsLong();save(record);
   }
  } else if(key.startsWith("chat:")) {
   String id=key.substring(5);
   if(payload==null)deleteConversation(owner,id);
   else {
    if(conversationOwner(id)!=null && !owner.equals(conversationOwner(id)))throw new IllegalStateException("Conversation belongs to another account.");
    com.google.gson.JsonObject json=com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
    StoredConversation chat=new StoredConversation();chat.owner=owner;chat.id=id;chat.title=json.get("title").getAsString();chat.payload=payload;
    chat.updatedAt=System.currentTimeMillis();insertConversation(chat);
   }
  }
  CloudRecord row=new CloudRecord();row.owner=owner;row.recordKey=key;row.payload=payload;row.revision=revision;saveCloud(row);return true;
 }
 @Transaction default boolean forkCloud(String owner,String key,String nonce,String branchKey,String branchPayload,String remotePayload,long revision) {
  CloudRecord prior=cloudRecord(owner,key);if(prior==null || !java.util.Objects.equals(prior.nonce,nonce))return false;
  importCloud(owner,branchKey,branchPayload,0,null);queueCloud(owner,branchKey,branchPayload);
  importCloud(owner,key,remotePayload,revision,nonce);
  return true;
 }
}
