/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2021 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.fabric.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.Adventure;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.fabric.ComponentArgumentType;
import net.kyori.adventure.platform.fabric.KeyArgumentType;
import net.kyori.adventure.platform.fabric.PlayerLocales;
import net.kyori.adventure.platform.fabric.impl.server.FabricServerAudiencesImpl;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.translation.Translator;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class AdventureCommon implements ModInitializer {

  private static final Logger LOGGER = LogUtils.getLogger();

  public static final SidedProxy SIDE_PROXY;

  public static final ComponentFlattener FLATTENER;
  private static final Pattern LOCALIZATION_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?s");
  public static final String MOD_FAPI_NETWORKING = "fabric-networking-api-v1";

  static {
    final var sidedProxy = chooseSidedProxy();
    SIDE_PROXY = sidedProxy;
    FLATTENER = createFlattener(sidedProxy);
  }

  private static SidedProxy chooseSidedProxy() {
    final EnvType environment = FabricLoader.getInstance().getEnvironmentType();
    final var sidedProxyContainers = FabricLoader.getInstance().getEntrypointContainers(
      "adventure-internal:sidedproxy/" + environment.name().toLowerCase(Locale.ROOT),
      SidedProxy.class
    );

    return switch (sidedProxyContainers.size()) {
      case 0 -> throw new IllegalStateException("No sided proxies were available for adventure-platform-fabric in environment " + environment);
      case 1 -> {
        final var proxy = sidedProxyContainers.get(0);
        LOGGER.debug("Selected sided proxy {} from {}", proxy.getEntrypoint(), proxy.getProvider().getMetadata().getId());
        yield proxy.getEntrypoint();
      }
      default -> {
        final var proxy = sidedProxyContainers.get(0);
        LOGGER.warn("Multiple applicable proxies were applicable, choosing first available: {} from {}", proxy.getEntrypoint(), proxy.getProvider().getMetadata().getId());
        yield proxy.getEntrypoint();
      }
    };
  }

  private static ComponentFlattener createFlattener(final SidedProxy proxy) {
    final ComponentFlattener.Builder flattenerBuilder = ComponentFlattener.basic().toBuilder();

    proxy.contributeFlattenerElements(flattenerBuilder);

    flattenerBuilder.complexMapper(TranslatableComponent.class, (translatable, consumer) -> {
      final String key = translatable.key();
      for (final Translator registry : GlobalTranslator.translator().sources()) {
        if (registry instanceof TranslationRegistry && ((TranslationRegistry) registry).contains(key)) {
          consumer.accept(GlobalTranslator.render(translatable, Locale.getDefault()));
          return;
        }
      }

      final @NotNull String translated = Language.getInstance().getOrDefault(key);
      final Matcher matcher = LOCALIZATION_PATTERN.matcher(translated);
      final List<Component> args = translatable.args();
      int argPosition = 0;
      int lastIdx = 0;
      while (matcher.find()) {
        // append prior
        if (lastIdx < matcher.start()) consumer.accept(Component.text(translated.substring(lastIdx, matcher.start())));
        lastIdx = matcher.end();

        final @Nullable String argIdx = matcher.group(1);
        // calculate argument position
        if (argIdx != null) {
          try {
            final int idx = Integer.parseInt(argIdx) - 1;
            if (idx < args.size()) {
              consumer.accept(args.get(idx));
            }
          } catch (final NumberFormatException ex) {
            // ignore, drop the format placeholder
          }
        } else {
          final int idx = argPosition++;
          if (idx < args.size()) {
            consumer.accept(args.get(idx));
          }
        }
      }

      // append tail
      if (lastIdx < translated.length()) {
        consumer.accept(Component.text(translated.substring(lastIdx)));
      }
    });

    return flattenerBuilder.build();
  }

  static ResourceLocation res(final @NotNull String value) {
    return new ResourceLocation(Adventure.NAMESPACE, value);
  }

  @Override
  public void onInitialize() {
    this.setupCustomArgumentTypes();

    PlayerLocales.CHANGED_EVENT.register((player, locale) -> {
      FabricServerAudiencesImpl.forEachInstance(instance -> {
        instance.bossBars().refreshTitles(player);
      });
    });

    // If we are in development mode, shut down immediately
    if (Boolean.getBoolean("adventure.testMode")) {
      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
          MixinEnvironment.getCurrentEnvironment().audit();
          server.execute(() -> {
            try {
              Files.writeString(Path.of("adventure-test-success.out"), "true");
            } catch (final IOException ex) {
              System.exit(1);
            }
            server.halt(false);
          });
        });
      }
    }
  }

  private void setupCustomArgumentTypes() {
    // sync is optional, so fapi is not required
    if (FabricLoader.getInstance().isModLoaded(MOD_FAPI_NETWORKING)) {
      ServerboundRegisteredArgumentTypesPacket.register();
    }

    ServerArgumentTypes.register(
      new ServerArgumentType<>(
        res("component"),
        ComponentArgumentType.class,
        ComponentArgumentTypeSerializer.INSTANCE,
        arg -> switch (arg.format()) {
          case JSON -> ComponentArgument.textComponent();
          case MINIMESSAGE -> StringArgumentType.greedyString();
        },
        null
      )
    );
    ServerArgumentTypes.register(
      new ServerArgumentType<>(
        res("key"),
        KeyArgumentType.class,
        SingletonArgumentInfo.contextFree(KeyArgumentType::key),
        arg -> ResourceLocationArgument.id(),
        null
      )
    );
  }

  public static Function<Pointered, Locale> localePartition() {
    return ptr -> ptr.getOrDefault(Identity.LOCALE, Locale.US);
  }

  public static Pointered pointered(final FPointered pointers) {
    return pointers;
  }

  @FunctionalInterface
  interface FPointered extends Pointered {
    @Override
    @NotNull Pointers pointers();
  }
}
