package com.ramka.storage.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MessageEntity> __insertionAdapterOfMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfMarkReadAckSent;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessageEntity = new EntityInsertionAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`localId`,`contactId`,`direction`,`bodyType`,`bodyText`,`status`,`localCreatedAtEpochMillis`,`deleteAfterReadSeconds`,`remoteMessageId`,`readAckSent`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindString(1, entity.getLocalId());
        statement.bindString(2, entity.getContactId());
        statement.bindString(3, entity.getDirection());
        statement.bindString(4, entity.getBodyType());
        if (entity.getBodyText() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getBodyText());
        }
        statement.bindString(6, entity.getStatus());
        statement.bindLong(7, entity.getLocalCreatedAtEpochMillis());
        if (entity.getDeleteAfterReadSeconds() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getDeleteAfterReadSeconds());
        }
        if (entity.getRemoteMessageId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRemoteMessageId());
        }
        final int _tmp = entity.getReadAckSent() ? 1 : 0;
        statement.bindLong(10, _tmp);
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET status = ? WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkReadAckSent = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET readAckSent = 1 WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE localId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final MessageEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMessageEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final String localId, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markReadAckSent(final String localId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkReadAckSent.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkReadAckSent.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String localId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MessageEntity>> observeForContact(final String contactId) {
    final String _sql = "SELECT * FROM messages WHERE contactId = ? ORDER BY localCreatedAtEpochMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, contactId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfBodyType = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyType");
          final int _cursorIndexOfBodyText = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyText");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfLocalCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "localCreatedAtEpochMillis");
          final int _cursorIndexOfDeleteAfterReadSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "deleteAfterReadSeconds");
          final int _cursorIndexOfRemoteMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteMessageId");
          final int _cursorIndexOfReadAckSent = CursorUtil.getColumnIndexOrThrow(_cursor, "readAckSent");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpBodyType;
            _tmpBodyType = _cursor.getString(_cursorIndexOfBodyType);
            final String _tmpBodyText;
            if (_cursor.isNull(_cursorIndexOfBodyText)) {
              _tmpBodyText = null;
            } else {
              _tmpBodyText = _cursor.getString(_cursorIndexOfBodyText);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpLocalCreatedAtEpochMillis;
            _tmpLocalCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfLocalCreatedAtEpochMillis);
            final Long _tmpDeleteAfterReadSeconds;
            if (_cursor.isNull(_cursorIndexOfDeleteAfterReadSeconds)) {
              _tmpDeleteAfterReadSeconds = null;
            } else {
              _tmpDeleteAfterReadSeconds = _cursor.getLong(_cursorIndexOfDeleteAfterReadSeconds);
            }
            final String _tmpRemoteMessageId;
            if (_cursor.isNull(_cursorIndexOfRemoteMessageId)) {
              _tmpRemoteMessageId = null;
            } else {
              _tmpRemoteMessageId = _cursor.getString(_cursorIndexOfRemoteMessageId);
            }
            final boolean _tmpReadAckSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadAckSent);
            _tmpReadAckSent = _tmp != 0;
            _item = new MessageEntity(_tmpLocalId,_tmpContactId,_tmpDirection,_tmpBodyType,_tmpBodyText,_tmpStatus,_tmpLocalCreatedAtEpochMillis,_tmpDeleteAfterReadSeconds,_tmpRemoteMessageId,_tmpReadAckSent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final String localId,
      final Continuation<? super MessageEntity> $completion) {
    final String _sql = "SELECT * FROM messages WHERE localId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, localId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfBodyType = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyType");
          final int _cursorIndexOfBodyText = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyText");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfLocalCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "localCreatedAtEpochMillis");
          final int _cursorIndexOfDeleteAfterReadSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "deleteAfterReadSeconds");
          final int _cursorIndexOfRemoteMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteMessageId");
          final int _cursorIndexOfReadAckSent = CursorUtil.getColumnIndexOrThrow(_cursor, "readAckSent");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpBodyType;
            _tmpBodyType = _cursor.getString(_cursorIndexOfBodyType);
            final String _tmpBodyText;
            if (_cursor.isNull(_cursorIndexOfBodyText)) {
              _tmpBodyText = null;
            } else {
              _tmpBodyText = _cursor.getString(_cursorIndexOfBodyText);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpLocalCreatedAtEpochMillis;
            _tmpLocalCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfLocalCreatedAtEpochMillis);
            final Long _tmpDeleteAfterReadSeconds;
            if (_cursor.isNull(_cursorIndexOfDeleteAfterReadSeconds)) {
              _tmpDeleteAfterReadSeconds = null;
            } else {
              _tmpDeleteAfterReadSeconds = _cursor.getLong(_cursorIndexOfDeleteAfterReadSeconds);
            }
            final String _tmpRemoteMessageId;
            if (_cursor.isNull(_cursorIndexOfRemoteMessageId)) {
              _tmpRemoteMessageId = null;
            } else {
              _tmpRemoteMessageId = _cursor.getString(_cursorIndexOfRemoteMessageId);
            }
            final boolean _tmpReadAckSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadAckSent);
            _tmpReadAckSent = _tmp != 0;
            _result = new MessageEntity(_tmpLocalId,_tmpContactId,_tmpDirection,_tmpBodyType,_tmpBodyText,_tmpStatus,_tmpLocalCreatedAtEpochMillis,_tmpDeleteAfterReadSeconds,_tmpRemoteMessageId,_tmpReadAckSent);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object pendingForContact(final String contactId,
      final Continuation<? super List<MessageEntity>> $completion) {
    final String _sql = "SELECT * FROM messages WHERE contactId = ? AND status IN ('SENDING', 'FAILED')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, contactId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfBodyType = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyType");
          final int _cursorIndexOfBodyText = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyText");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfLocalCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "localCreatedAtEpochMillis");
          final int _cursorIndexOfDeleteAfterReadSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "deleteAfterReadSeconds");
          final int _cursorIndexOfRemoteMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteMessageId");
          final int _cursorIndexOfReadAckSent = CursorUtil.getColumnIndexOrThrow(_cursor, "readAckSent");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpBodyType;
            _tmpBodyType = _cursor.getString(_cursorIndexOfBodyType);
            final String _tmpBodyText;
            if (_cursor.isNull(_cursorIndexOfBodyText)) {
              _tmpBodyText = null;
            } else {
              _tmpBodyText = _cursor.getString(_cursorIndexOfBodyText);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpLocalCreatedAtEpochMillis;
            _tmpLocalCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfLocalCreatedAtEpochMillis);
            final Long _tmpDeleteAfterReadSeconds;
            if (_cursor.isNull(_cursorIndexOfDeleteAfterReadSeconds)) {
              _tmpDeleteAfterReadSeconds = null;
            } else {
              _tmpDeleteAfterReadSeconds = _cursor.getLong(_cursorIndexOfDeleteAfterReadSeconds);
            }
            final String _tmpRemoteMessageId;
            if (_cursor.isNull(_cursorIndexOfRemoteMessageId)) {
              _tmpRemoteMessageId = null;
            } else {
              _tmpRemoteMessageId = _cursor.getString(_cursorIndexOfRemoteMessageId);
            }
            final boolean _tmpReadAckSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadAckSent);
            _tmpReadAckSent = _tmp != 0;
            _item = new MessageEntity(_tmpLocalId,_tmpContactId,_tmpDirection,_tmpBodyType,_tmpBodyText,_tmpStatus,_tmpLocalCreatedAtEpochMillis,_tmpDeleteAfterReadSeconds,_tmpRemoteMessageId,_tmpReadAckSent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object incomingWithoutReadAck(final String contactId,
      final Continuation<? super List<MessageEntity>> $completion) {
    final String _sql = "SELECT * FROM messages WHERE contactId = ? AND direction = 'INCOMING' AND readAckSent = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, contactId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfBodyType = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyType");
          final int _cursorIndexOfBodyText = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyText");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfLocalCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "localCreatedAtEpochMillis");
          final int _cursorIndexOfDeleteAfterReadSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "deleteAfterReadSeconds");
          final int _cursorIndexOfRemoteMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteMessageId");
          final int _cursorIndexOfReadAckSent = CursorUtil.getColumnIndexOrThrow(_cursor, "readAckSent");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpBodyType;
            _tmpBodyType = _cursor.getString(_cursorIndexOfBodyType);
            final String _tmpBodyText;
            if (_cursor.isNull(_cursorIndexOfBodyText)) {
              _tmpBodyText = null;
            } else {
              _tmpBodyText = _cursor.getString(_cursorIndexOfBodyText);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpLocalCreatedAtEpochMillis;
            _tmpLocalCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfLocalCreatedAtEpochMillis);
            final Long _tmpDeleteAfterReadSeconds;
            if (_cursor.isNull(_cursorIndexOfDeleteAfterReadSeconds)) {
              _tmpDeleteAfterReadSeconds = null;
            } else {
              _tmpDeleteAfterReadSeconds = _cursor.getLong(_cursorIndexOfDeleteAfterReadSeconds);
            }
            final String _tmpRemoteMessageId;
            if (_cursor.isNull(_cursorIndexOfRemoteMessageId)) {
              _tmpRemoteMessageId = null;
            } else {
              _tmpRemoteMessageId = _cursor.getString(_cursorIndexOfRemoteMessageId);
            }
            final boolean _tmpReadAckSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadAckSent);
            _tmpReadAckSent = _tmp != 0;
            _item = new MessageEntity(_tmpLocalId,_tmpContactId,_tmpDirection,_tmpBodyType,_tmpBodyText,_tmpStatus,_tmpLocalCreatedAtEpochMillis,_tmpDeleteAfterReadSeconds,_tmpRemoteMessageId,_tmpReadAckSent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findIncomingByRemoteId(final String contactId, final String remoteMessageId,
      final Continuation<? super MessageEntity> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM messages WHERE contactId = ? AND direction = 'INCOMING'\n"
            + "        AND remoteMessageId = ? LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, contactId);
    _argIndex = 2;
    _statement.bindString(_argIndex, remoteMessageId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfBodyType = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyType");
          final int _cursorIndexOfBodyText = CursorUtil.getColumnIndexOrThrow(_cursor, "bodyText");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfLocalCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "localCreatedAtEpochMillis");
          final int _cursorIndexOfDeleteAfterReadSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "deleteAfterReadSeconds");
          final int _cursorIndexOfRemoteMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteMessageId");
          final int _cursorIndexOfReadAckSent = CursorUtil.getColumnIndexOrThrow(_cursor, "readAckSent");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpBodyType;
            _tmpBodyType = _cursor.getString(_cursorIndexOfBodyType);
            final String _tmpBodyText;
            if (_cursor.isNull(_cursorIndexOfBodyText)) {
              _tmpBodyText = null;
            } else {
              _tmpBodyText = _cursor.getString(_cursorIndexOfBodyText);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpLocalCreatedAtEpochMillis;
            _tmpLocalCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfLocalCreatedAtEpochMillis);
            final Long _tmpDeleteAfterReadSeconds;
            if (_cursor.isNull(_cursorIndexOfDeleteAfterReadSeconds)) {
              _tmpDeleteAfterReadSeconds = null;
            } else {
              _tmpDeleteAfterReadSeconds = _cursor.getLong(_cursorIndexOfDeleteAfterReadSeconds);
            }
            final String _tmpRemoteMessageId;
            if (_cursor.isNull(_cursorIndexOfRemoteMessageId)) {
              _tmpRemoteMessageId = null;
            } else {
              _tmpRemoteMessageId = _cursor.getString(_cursorIndexOfRemoteMessageId);
            }
            final boolean _tmpReadAckSent;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfReadAckSent);
            _tmpReadAckSent = _tmp != 0;
            _result = new MessageEntity(_tmpLocalId,_tmpContactId,_tmpDirection,_tmpBodyType,_tmpBodyText,_tmpStatus,_tmpLocalCreatedAtEpochMillis,_tmpDeleteAfterReadSeconds,_tmpRemoteMessageId,_tmpReadAckSent);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
