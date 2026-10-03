package com.ramka.storage.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
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
public final class OutboxDao_Impl implements OutboxDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OutboxEntity> __insertionAdapterOfOutboxEntity;

  private final SharedSQLiteStatement __preparedStmtOfRecordFailedAttempt;

  private final SharedSQLiteStatement __preparedStmtOfRemove;

  public OutboxDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOutboxEntity = new EntityInsertionAdapter<OutboxEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `outbox` (`messageLocalId`,`contactId`,`attemptCount`,`nextAttemptAtEpochMillis`,`createdAtEpochMillis`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OutboxEntity entity) {
        statement.bindString(1, entity.getMessageLocalId());
        statement.bindString(2, entity.getContactId());
        statement.bindLong(3, entity.getAttemptCount());
        statement.bindLong(4, entity.getNextAttemptAtEpochMillis());
        statement.bindLong(5, entity.getCreatedAtEpochMillis());
      }
    };
    this.__preparedStmtOfRecordFailedAttempt = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE outbox SET attemptCount = ?, nextAttemptAtEpochMillis = ?\n"
                + "        WHERE messageLocalId = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfRemove = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM outbox WHERE messageLocalId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final OutboxEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfOutboxEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object recordFailedAttempt(final String messageLocalId, final int attemptCount,
      final long nextAttemptAtEpochMillis, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRecordFailedAttempt.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, attemptCount);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, nextAttemptAtEpochMillis);
        _argIndex = 3;
        _stmt.bindString(_argIndex, messageLocalId);
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
          __preparedStmtOfRecordFailedAttempt.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object remove(final String messageLocalId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemove.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, messageLocalId);
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
          __preparedStmtOfRemove.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<OutboxEntity>> observeAll() {
    final String _sql = "SELECT * FROM outbox";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"outbox"}, new Callable<List<OutboxEntity>>() {
      @Override
      @NonNull
      public List<OutboxEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageLocalId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfAttemptCount = CursorUtil.getColumnIndexOrThrow(_cursor, "attemptCount");
          final int _cursorIndexOfNextAttemptAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "nextAttemptAtEpochMillis");
          final int _cursorIndexOfCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMillis");
          final List<OutboxEntity> _result = new ArrayList<OutboxEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutboxEntity _item;
            final String _tmpMessageLocalId;
            _tmpMessageLocalId = _cursor.getString(_cursorIndexOfMessageLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final int _tmpAttemptCount;
            _tmpAttemptCount = _cursor.getInt(_cursorIndexOfAttemptCount);
            final long _tmpNextAttemptAtEpochMillis;
            _tmpNextAttemptAtEpochMillis = _cursor.getLong(_cursorIndexOfNextAttemptAtEpochMillis);
            final long _tmpCreatedAtEpochMillis;
            _tmpCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfCreatedAtEpochMillis);
            _item = new OutboxEntity(_tmpMessageLocalId,_tmpContactId,_tmpAttemptCount,_tmpNextAttemptAtEpochMillis,_tmpCreatedAtEpochMillis);
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
  public Object due(final long nowEpochMillis,
      final Continuation<? super List<OutboxEntity>> $completion) {
    final String _sql = "SELECT * FROM outbox WHERE nextAttemptAtEpochMillis <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, nowEpochMillis);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OutboxEntity>>() {
      @Override
      @NonNull
      public List<OutboxEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMessageLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageLocalId");
          final int _cursorIndexOfContactId = CursorUtil.getColumnIndexOrThrow(_cursor, "contactId");
          final int _cursorIndexOfAttemptCount = CursorUtil.getColumnIndexOrThrow(_cursor, "attemptCount");
          final int _cursorIndexOfNextAttemptAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "nextAttemptAtEpochMillis");
          final int _cursorIndexOfCreatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMillis");
          final List<OutboxEntity> _result = new ArrayList<OutboxEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OutboxEntity _item;
            final String _tmpMessageLocalId;
            _tmpMessageLocalId = _cursor.getString(_cursorIndexOfMessageLocalId);
            final String _tmpContactId;
            _tmpContactId = _cursor.getString(_cursorIndexOfContactId);
            final int _tmpAttemptCount;
            _tmpAttemptCount = _cursor.getInt(_cursorIndexOfAttemptCount);
            final long _tmpNextAttemptAtEpochMillis;
            _tmpNextAttemptAtEpochMillis = _cursor.getLong(_cursorIndexOfNextAttemptAtEpochMillis);
            final long _tmpCreatedAtEpochMillis;
            _tmpCreatedAtEpochMillis = _cursor.getLong(_cursorIndexOfCreatedAtEpochMillis);
            _item = new OutboxEntity(_tmpMessageLocalId,_tmpContactId,_tmpAttemptCount,_tmpNextAttemptAtEpochMillis,_tmpCreatedAtEpochMillis);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
