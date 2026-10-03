package com.ramka.app;

import com.ramka.app.background.BackgroundDeliveryController;
import com.ramka.app.discovery.LanVisibilityController;
import com.ramka.data.incoming.IncomingMessageProcessor;
import com.ramka.domain.usecase.ProcessOutboxUseCase;
import com.ramka.network.local.LanDiscoveryService;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

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
public final class RamkaApp_MembersInjector implements MembersInjector<RamkaApp> {
  private final Provider<LanDiscoveryService> lanDiscoveryServiceProvider;

  private final Provider<IncomingMessageProcessor> incomingMessageProcessorProvider;

  private final Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  private final Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider;

  private final Provider<LanVisibilityController> lanVisibilityControllerProvider;

  public RamkaApp_MembersInjector(Provider<LanDiscoveryService> lanDiscoveryServiceProvider,
      Provider<IncomingMessageProcessor> incomingMessageProcessorProvider,
      Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider,
      Provider<CoroutineScope> appScopeProvider,
      Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider,
      Provider<LanVisibilityController> lanVisibilityControllerProvider) {
    this.lanDiscoveryServiceProvider = lanDiscoveryServiceProvider;
    this.incomingMessageProcessorProvider = incomingMessageProcessorProvider;
    this.processOutboxUseCaseProvider = processOutboxUseCaseProvider;
    this.appScopeProvider = appScopeProvider;
    this.backgroundDeliveryControllerProvider = backgroundDeliveryControllerProvider;
    this.lanVisibilityControllerProvider = lanVisibilityControllerProvider;
  }

  public static MembersInjector<RamkaApp> create(
      Provider<LanDiscoveryService> lanDiscoveryServiceProvider,
      Provider<IncomingMessageProcessor> incomingMessageProcessorProvider,
      Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider,
      Provider<CoroutineScope> appScopeProvider,
      Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider,
      Provider<LanVisibilityController> lanVisibilityControllerProvider) {
    return new RamkaApp_MembersInjector(lanDiscoveryServiceProvider, incomingMessageProcessorProvider, processOutboxUseCaseProvider, appScopeProvider, backgroundDeliveryControllerProvider, lanVisibilityControllerProvider);
  }

  @Override
  public void injectMembers(RamkaApp instance) {
    injectLanDiscoveryService(instance, lanDiscoveryServiceProvider.get());
    injectIncomingMessageProcessor(instance, incomingMessageProcessorProvider.get());
    injectProcessOutboxUseCase(instance, processOutboxUseCaseProvider.get());
    injectAppScope(instance, appScopeProvider.get());
    injectBackgroundDeliveryController(instance, backgroundDeliveryControllerProvider.get());
    injectLanVisibilityController(instance, lanVisibilityControllerProvider.get());
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.lanDiscoveryService")
  public static void injectLanDiscoveryService(RamkaApp instance,
      LanDiscoveryService lanDiscoveryService) {
    instance.lanDiscoveryService = lanDiscoveryService;
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.incomingMessageProcessor")
  public static void injectIncomingMessageProcessor(RamkaApp instance,
      IncomingMessageProcessor incomingMessageProcessor) {
    instance.incomingMessageProcessor = incomingMessageProcessor;
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.processOutboxUseCase")
  public static void injectProcessOutboxUseCase(RamkaApp instance,
      ProcessOutboxUseCase processOutboxUseCase) {
    instance.processOutboxUseCase = processOutboxUseCase;
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.appScope")
  public static void injectAppScope(RamkaApp instance, CoroutineScope appScope) {
    instance.appScope = appScope;
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.backgroundDeliveryController")
  public static void injectBackgroundDeliveryController(RamkaApp instance,
      BackgroundDeliveryController backgroundDeliveryController) {
    instance.backgroundDeliveryController = backgroundDeliveryController;
  }

  @InjectedFieldSignature("com.ramka.app.RamkaApp.lanVisibilityController")
  public static void injectLanVisibilityController(RamkaApp instance,
      LanVisibilityController lanVisibilityController) {
    instance.lanVisibilityController = lanVisibilityController;
  }
}
