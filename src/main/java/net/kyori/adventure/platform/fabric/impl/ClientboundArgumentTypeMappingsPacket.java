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

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.kyori.adventure.Adventure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ClientboundArgumentTypeMappingsPacket(Int2ObjectMap<ResourceLocation> mappings) {
  public static final ResourceLocation ID = new ResourceLocation(Adventure.NAMESPACE, "registered_arg_mappings");

  public static ClientboundArgumentTypeMappingsPacket from(final FriendlyByteBuf buffer) {
    final Int2ObjectMap<ResourceLocation> map = buffer.readMap(
      Int2ObjectArrayMap::new,
      FriendlyByteBuf::readVarInt,
      FriendlyByteBuf::readResourceLocation
    );
    return new ClientboundArgumentTypeMappingsPacket(map);
  }

  private FriendlyByteBuf serialize() {
    final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeMap(
      this.mappings,
      FriendlyByteBuf::writeVarInt,
      FriendlyByteBuf::writeResourceLocation
    );
    return buf;
  }

  public void sendTo(final PacketSender responder) {
    responder.sendPacket(ID, this.serialize());
  }
}
