/*
 * This file is part of adventure, licensed under the MIT License.
 *
 * Copyright (c) 2020 KyoriPowered
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

package net.kyori.adventure.platform.fabric.impl.mixin;

import com.google.common.collect.MapMaker;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.platform.fabric.FabricServerAudienceProvider;
import net.kyori.adventure.platform.fabric.impl.server.FabricServerAudienceProviderImpl;
import net.kyori.adventure.platform.fabric.impl.server.PlainAudience;
import net.kyori.adventure.platform.fabric.impl.server.RenderableAudience;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.rcon.RconConsoleSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RconConsoleSource.class)
public abstract class RconConsoleSourceMixin implements RenderableAudience, ForwardingAudience.Single {
  @Shadow @Final private StringBuffer buffer;

  @Shadow @Final
  private MinecraftServer server;
  private final Map<FabricAudiences, Audience> adventure$renderers = new MapMaker().weakKeys().makeMap();

  @Override
  public Audience renderUsing(final FabricServerAudienceProviderImpl controller) {
    return this.adventure$renderers.computeIfAbsent(controller, ctrl -> new PlainAudience(ctrl, this.buffer::append));
  }

  @Override
  public @NonNull Audience audience() {
    return FabricServerAudienceProvider.of(this.server).audience((RconConsoleSource) (Object) this);
  }
}
