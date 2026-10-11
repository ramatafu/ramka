package com.ramka.app.di;

import com.ramka.domain.relay.RelayClock;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class RelayControllersModule_RelayClockFactory implements Factory<RelayClock> {
  @Override
  public RelayClock get() {
    return relayClock();
  }

  public static RelayControllersModule_RelayClockFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RelayClock relayClock() {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.relayClock());
  }

  private static final class InstanceHolder {
    private static final RelayControllersModule_RelayClockFactory INSTANCE = new RelayControllersModule_RelayClockFactory();
  }
}
